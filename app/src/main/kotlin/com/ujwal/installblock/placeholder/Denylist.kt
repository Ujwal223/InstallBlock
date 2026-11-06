// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.placeholder

import android.content.Context
import android.content.pm.PackageManager

/**
 * Packages that must never be blocked, regardless of what appears in the list.
 *
 * A bad list entry or a rogue PR could target the installer, Shizuku, or this app itself.
 * Hardcoding the denylist here means no server-side change can remove the safeguard.
 *
 * Adding a new entry: add the package name string below and document why.
 * Do NOT make this list configurable at runtime.
 */
object Denylist {

    private val PACKAGES: Set<String> = setOf(
        // This app itself :  blocking it would make unblocking impossible.
        "com.ujwal.installblock",
        "com.ujwal.installblocker",
        "com.ujwal.installblock.debug",

        // Android's own package installer :  breaking this would make ALL installs impossible.
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",

        // Shizuku :  blocking it would break Shizuku mode in an unrecoverable way.
        "moe.shizuku.privileged.api",

        // F-Droid clients :  InstallBlock ships through F-Droid; blocking an F-Droid client
        // would prevent users from updating or uninstalling this app via F-Droid.
        "org.fdroid.fdroid",
        "org.fdroid.basic",
        "org.fdroid.fdroid.privileged",
        "eu.bubu1.fdroidclassic",
        "com.aurora.fdroid",
    )

    /**
     * Returns true if [packageName] must not be blocked.
     *
     * Also returns true for system apps: a system app cannot be uninstalled first
     * (we would need Device Owner / MDM for that, which is a non-goal).
     */
    fun blocks(context: Context, packageName: String): Boolean {
        if (packageName in PACKAGES) return true
        if (runCatching { context.packageName }.getOrNull() == packageName) return true
        return isSystemApp(context, packageName)
    }

    private fun isSystemApp(context: Context, packageName: String): Boolean = try {
        val pm = context.packageManager ?: return false
        val info = pm.getApplicationInfo(packageName, 0)
        (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (_: Throwable) {
        // Not installed or unmocked :  not a system app by definition.
        false
    }
}

