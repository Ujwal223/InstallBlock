// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.data.model

/**
 * Defines the enforcement mechanism used to prevent target app installation.
 */
enum class BlockType(val label: String, val description: String) {
    /**
     * Standard block: A signed placeholder APK occupies the package name with
     * versionCode 2147483647. The real app cannot be installed or updated due to
     * signature mismatch (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
     */
    Plain(
        label = "Plain",
        description = "Standard signed placeholder. Visible in Settings.",
    ),

    /**
     * Locked block: The placeholder is registered as an active device admin with an
     * empty policy list. The Android system disables the 'Uninstall' button in Settings.
     */
    Locked(
        label = "Locked",
        description = "Disables the Uninstall button in system Settings via Device Admin.",
    ),

    /**
     * Hidden block (Experimental): After installing the placeholder, Shizuku executes a
     * keep-data uninstall ('pm uninstall -k'). The package disappears from Settings and app lists,
     * while Android's retained signature record continues to reject the real app.
     */
    Hidden(
        label = "Hidden",
        description = "Invisible in Settings and app lists. Unblockable only inside InstallBlock.",
    ),
}
