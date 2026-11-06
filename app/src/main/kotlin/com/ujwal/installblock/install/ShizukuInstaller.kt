// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.install

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ShizukuState {
    Ready,
    RunningButNotGranted,
    Stopped,
    NotInstalled,
}

/**
 * Installs and uninstalls packages through a Shizuku user service.
 */
class ShizukuInstaller(private val context: Context) : InstallerGateway {

    /**
     * Rikka's Shizuku API library statically caches `permissionGranted = true` on the client
     * process after the initial handshake. If the user subsequently revokes permission in the
     * Shizuku manager app, `Shizuku.checkSelfPermission()` continues returning 0 (GRANTED)
     * unless the internal static cache is cleared. Clearing it forces an IPC call to the
     * live Shizuku service to verify actual permission status.
     */
    private fun resetPermissionCache() {
        try {
            val field = Shizuku::class.java.getDeclaredField("permissionGranted")
            field.isAccessible = true
            field.setBoolean(null, false)
        } catch (_: Throwable) {
            // Ignored if field is renamed or inaccessible
        }
    }

    override fun isAvailable(): Boolean = try {
        resetPermissionCache()
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        resetPermissionCache()
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun getShizukuState(): ShizukuState {
        val isInstalled = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        if (!isInstalled) return ShizukuState.NotInstalled

        val isRunning = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        if (!isRunning) return ShizukuState.Stopped

        resetPermissionCache()
        val hasPerm = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
        return if (hasPerm) ShizukuState.Ready else ShizukuState.RunningButNotGranted
    }

    override suspend fun install(apkBytes: ByteArray, packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val result = withService { it.installApk(apkBytes, packageName) }
                checkResult(result)
            }
        }

    override suspend fun uninstall(packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val primary = execute(arrayOf("pm", "uninstall", packageName))
            if (primary.isSuccess) {
                primary
            } else {
                execute(arrayOf("pm", "uninstall", "--user", "0", packageName))
            }
        }

    override suspend fun uninstallKeepData(packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val primary = execute(arrayOf("pm", "uninstall", "-k", packageName))
            if (primary.isSuccess) {
                primary
            } else {
                execute(arrayOf("pm", "uninstall", "-k", "--user", "0", packageName))
            }
        }

    override suspend fun clearData(packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val pmResult = execute(arrayOf("pm", "clear", packageName))
            if (pmResult.isSuccess) {
                pmResult
            } else {
                execute(arrayOf("cmd", "package", "clear", "--user", "0", packageName))
            }
        }

    override suspend fun installExisting(packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val primary = execute(arrayOf("pm", "install-existing", packageName))
            if (primary.isSuccess) {
                primary
            } else {
                val fallbackPm = execute(arrayOf("pm", "install-existing", "--user", "0", packageName))
                if (fallbackPm.isSuccess) {
                    fallbackPm
                } else {
                    execute(arrayOf("cmd", "package", "install-existing", "--user", "0", packageName))
                }
            }
        }

    override suspend fun setActiveAdmin(packageName: String, adminClass: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            execute(arrayOf("dpm", "set-active-admin", "--user", "current", "$packageName/$adminClass"))
        }

    override suspend fun removeActiveAdmin(packageName: String, adminClass: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            execute(arrayOf("dpm", "remove-active-admin", "--user", "current", "$packageName/$adminClass"))
        }

    private suspend fun execute(command: Array<String>): Result<Unit> =
        runCatching {
            val result = withService { it.execute(command) }
            checkResult(result)
        }

    private fun checkResult(result: String) {
        val firstLineEnd = result.indexOf('\n').takeIf { it >= 0 } ?: result.length
        val exitCode = result.substring(0, firstLineEnd).toIntOrNull()
            ?: error("Invalid Shizuku service response")
        val output = result.substringAfter('\n', "").trim()
        val isFailureOutput = output.contains("Failure [", ignoreCase = true) ||
            output.startsWith("Error:", ignoreCase = true) ||
            output.equals("Failed", ignoreCase = true)
        if (exitCode != 0 || isFailureOutput) {
            val message = if (output.contains("DELETE_FAILED_INTERNAL_ERROR", ignoreCase = true)) {
                "Uninstall failed (DELETE_FAILED_INTERNAL_ERROR): protected or read-only system app. Use --user 0 to disable for current user."
            } else {
                output.ifEmpty { "Shizuku command exited with code $exitCode" }
            }
            error(message)
        }
    }

    private suspend fun <T> withService(block: suspend (IShizukuShell) -> T): T =
        withContext(Dispatchers.IO) {
            val updateTime = try {
                context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            } catch (_: Exception) {
                1L
            }
            val serviceVersion = (updateTime and 0x7FFFFFFF).toInt().coerceAtLeast(2)

            val args = Shizuku.UserServiceArgs(
                ComponentName(context, ShizukuShellService::class.java),
            ).tag("installblock-shell")
                .processNameSuffix("installblock_shell")
                .version(serviceVersion)
                .daemon(false)

            var boundConnection: ServiceConnection? = null
            val service = suspendCancellableCoroutine<IShizukuShell> { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        if (continuation.isActive) {
                            continuation.resume(IShizukuShell.Stub.asInterface(binder))
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Shizuku user service disconnected"),
                            )
                        }
                    }
                }

                boundConnection = connection
                try {
                    Shizuku.bindUserService(args, connection)
                } catch (e: Throwable) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                continuation.invokeOnCancellation {
                    runCatching { Shizuku.unbindUserService(args, connection, true) }
                }
            }

            try {
                block(service)
            } finally {
                boundConnection?.let { conn ->
                    runCatching { Shizuku.unbindUserService(args, conn, true) }
                }
            }
        }
}
