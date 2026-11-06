// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private const val INSTALL_ACTION_PREFIX   = "com.ujwal.installblock.INSTALL_"
private const val UNINSTALL_ACTION_PREFIX = "com.ujwal.installblock.UNINSTALL_"

/**
 * Installs and uninstalls packages using the standard [PackageInstaller] API.
 *
 * Always available :  no root or Shizuku required. Install requests go through the system
 * installer UI, which may show a prompt or fail silently depending on device policy and
 * whether Install Unknown Apps permission is granted.
 *
 * Both operations use a one-shot [BroadcastReceiver] registered for the duration of the
 * session commit. The receiver is unregistered before the coroutine resumes, so there is
 * no receiver leak.
 */
class NormalInstaller(private val context: Context) : InstallerGateway {

    /** Normal install is always a valid fallback path. */
    override fun isAvailable(): Boolean = true

    /**
     * Writes [apkBytes] into a [PackageInstaller] session and commits it.
     *
     * Returns [Result.failure] when the system installer reports a failure status,
     * or when the install is blocked by verification (e.g. Play Protect).
     */
    override suspend fun install(apkBytes: ByteArray, packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params    = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)

            installer.openSession(sessionId).use { session ->
                session.openWrite("package", 0, apkBytes.size.toLong()).use { out ->
                    out.write(apkBytes)
                    session.fsync(out)
                }

                val action = "$INSTALL_ACTION_PREFIX$sessionId"
                try {
                    withTimeout(OPERATION_TIMEOUT_MS) {
                        awaitBroadcast(action) { pi ->
                            session.commit(pi.intentSender)
                        }
                    }
                } catch (error: Throwable) {
                    installer.abandonSession(sessionId)
                    throw error
                }
            }
        }

    /**
     * Requests uninstallation of [packageName] via [PackageInstaller].
     *
     * On API 26+ the user may see a confirmation dialog. The coroutine suspends
     * until the system broadcasts the result.
     */
    override suspend fun uninstall(packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val action = "$UNINSTALL_ACTION_PREFIX${java.util.UUID.randomUUID()}"
            withTimeout(OPERATION_TIMEOUT_MS) {
                awaitBroadcast(action) { pi ->
                    installer.uninstall(packageName, pi.intentSender)
                }
            }
        }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Registers a one-shot broadcast receiver for [action], executes [commit], and
     * suspends until the broadcast arrives.
     *
     * The receiver is unregistered in the coroutine's cancellation handler so it is
     * always cleaned up regardless of how the coroutine ends.
     */
    private suspend fun awaitBroadcast(
        action: String,
        commit: (android.app.PendingIntent) -> Unit,
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        val registered = AtomicBoolean(false)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val status  = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

                if (cont.isActive) {
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { action ->
                            action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(action)
                        } ?: finish(Result.failure(InstallException("Android requested confirmation but provided no action")))
                    } else if (status == PackageInstaller.STATUS_SUCCESS) {
                        finish(Result.success(Unit))
                    } else {
                        // Distinguish verification and system-app delete failures so the UI can offer guidance.
                        val isVerification = "INSTALL_FAILED_VERIFICATION_FAILURE" in message
                        val reason = if (isVerification) {
                            "Blocked by device verification"
                        } else if ("DELETE_FAILED_INTERNAL_ERROR" in message) {
                            "Uninstall failed: protected or read-only system app"
                        } else {
                            message
                        }
                        finish(Result.failure(InstallException(reason.ifBlank { "Install failed (status $status)" }, isVerification)))
                    }

                }
            }

            private fun finish(result: Result<Unit>) {
                if (registered.compareAndSet(true, false)) {
                    context.unregisterReceiver(this)
                }
                if (cont.isActive) cont.resume(result)
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        registered.set(true)

        cont.invokeOnCancellation {
            if (registered.compareAndSet(true, false)) {
                context.unregisterReceiver(receiver)
            }
        }

        val pi = android.app.PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(action).setPackage(context.packageName),
            android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            commit(pi)
        } catch (error: Throwable) {
            if (registered.compareAndSet(true, false)) {
                context.unregisterReceiver(receiver)
            }
            if (cont.isActive) {
                cont.resume(Result.failure(error))
            }
        }

    }

    private companion object {
        const val OPERATION_TIMEOUT_MS = 30_000L
    }
}

/** Thrown when the system installer rejects an install or uninstall. */
class InstallException(
    message: String,
    /** True when the failure is specifically a Play Protect / verification block. */
    val isVerificationBlock: Boolean = false,
) : RuntimeException(message)
