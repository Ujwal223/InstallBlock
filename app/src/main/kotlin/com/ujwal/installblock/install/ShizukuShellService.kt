// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.install

import android.content.Context
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Runs package-manager and device-policy commands inside a Shizuku user-service process.
 *
 * This service runs with UID 2000 (shell). On Android 11+ (API 30+), Scoped Storage isolates
 * `/storage/emulated/0/Android/data/` such that shell commands like `pm install` fail with
 * "Can't open file". Since this service runs as shell, it writes APK payloads directly into
 * `/data/local/tmp` (which UID 2000 owns and `pm` can open) and cleans them up immediately.
 */
@Keep
class ShizukuShellService() : IShizukuShell.Stub() {

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun execute(command: Array<String>): String {
        require(isSafeCommand(command)) { "Unsupported Shizuku command: ${command.joinToString(" ")}" }
        return executeInternal(command)
    }

    override fun installApk(bytes: ByteArray, packageName: String): String {
        require(packageName.matches(PACKAGE_NAME_RE)) { "Invalid package name: $packageName" }
        require(bytes.isNotEmpty()) { "Empty APK bytes" }

        val tmpDir = File("/data/local/tmp")
        val tmp = try {
            File.createTempFile("ib_${packageName}_", ".apk", tmpDir)
        } catch (e: Exception) {
            return "1\nFailed to create temp APK in /data/local/tmp: ${e.message}"
        }

        return try {
            tmp.writeBytes(bytes)
            tmp.setReadable(true, false)
            val command = arrayOf("pm", "install", "-r", "--user", "0", tmp.absolutePath)
            executeInternal(command)
        } catch (e: Exception) {
            "1\nFailed to install APK: ${e.message}"
        } finally {
            tmp.delete()
        }
    }

    private fun executeInternal(command: Array<String>): String {
        val process = ProcessBuilder(command.asList())
            .redirectErrorStream(true)
            .start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        val finished = process.waitFor(20, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return "124\nShizuku command timed out"
        }

        val exitCode = process.exitValue()
        return "$exitCode\n$output"
    }

    companion object {
        val PACKAGE_NAME_RE = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")
        val COMPONENT_NAME_RE = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+/[A-Za-z0-9_.]+$""")

        @androidx.annotation.VisibleForTesting
        internal fun isSafeCommand(command: Array<String>): Boolean {
            if (command.isEmpty()) return false
            val containsMetas = command.any { arg ->
                arg.isEmpty() || arg.any { ch ->
                    ch == ';' || ch == '|' || ch == '&' || ch == '`' || ch == '$' ||
                        ch == '\n' || ch == '\r' || ch == '<' || ch == '>'
                }
            }
            if (containsMetas) return false

            return when (command.first()) {
                "pm" -> when (command.getOrNull(1)) {
                    "uninstall" -> when (command.size) {
                        3 -> command[2].matches(PACKAGE_NAME_RE)
                        4 -> command[2] == "-k" && command[3].matches(PACKAGE_NAME_RE)
                        5 -> command[2] == "--user" && command[3].toIntOrNull() != null && command[4].matches(PACKAGE_NAME_RE)
                        6 -> command[2] == "-k" && command[3] == "--user" && command[4].toIntOrNull() != null && command[5].matches(PACKAGE_NAME_RE)
                        else -> false
                    }
                    "install" -> command.size == 6 && command[2] == "-r" && command[3] == "--user" &&
                        command[4].toIntOrNull() != null && command[5].startsWith("/")
                    "install-existing" -> when (command.size) {
                        3 -> command[2].matches(PACKAGE_NAME_RE)
                        5 -> command[2] == "--user" && command[3].toIntOrNull() != null && command[4].matches(PACKAGE_NAME_RE)
                        else -> false
                    }
                    "clear" -> when (command.size) {
                        3 -> command[2].matches(PACKAGE_NAME_RE)
                        5 -> command[2] == "--user" && command[3].toIntOrNull() != null && command[4].matches(PACKAGE_NAME_RE)
                        else -> false
                    }
                    "list" -> {
                        command.size in 3..4 && command[2] == "packages" &&
                            (command.size == 3 || command[3] == "-u")
                    }
                    else -> false
                }
                "cmd" -> {
                    if (command.getOrNull(1) == "package") {
                        when (command.getOrNull(2)) {
                            "clear" -> when (command.size) {
                                4 -> command[3].matches(PACKAGE_NAME_RE)
                                6 -> command[3] == "--user" && command[4].toIntOrNull() != null && command[5].matches(PACKAGE_NAME_RE)
                                else -> false
                            }
                            "install-existing" -> when (command.size) {
                                4 -> command[3].matches(PACKAGE_NAME_RE)
                                6 -> command[3] == "--user" && command[4].toIntOrNull() != null && command[5].matches(PACKAGE_NAME_RE)
                                else -> false
                            }
                            "uninstall" -> when (command.size) {
                                4 -> command[3].matches(PACKAGE_NAME_RE)
                                5 -> command[3] == "-k" && command[4].matches(PACKAGE_NAME_RE)
                                6 -> command[3] == "--user" && command[4].toIntOrNull() != null && command[5].matches(PACKAGE_NAME_RE)
                                7 -> command[3] == "-k" && command[4] == "--user" && command[5].toIntOrNull() != null && command[6].matches(PACKAGE_NAME_RE)
                                else -> false
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                "dpm" -> when (command.getOrNull(1)) {
                    "set-active-admin", "remove-active-admin" -> {
                        command.size == 5 && command[2] == "--user" &&
                            (command[3] == "current" || command[3].toIntOrNull() != null) &&
                            command[4].matches(COMPONENT_NAME_RE)
                    }
                    else -> false
                }
                else -> false
            }
        }
    }
}
