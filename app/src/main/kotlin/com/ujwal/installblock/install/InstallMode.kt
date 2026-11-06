// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.install

/**
 * The install modes supported by InstallBlock.
 *
 * [Shizuku] is the recommended and default mode, providing silent installs and hidden
 * keep-data blocks without system dialog prompts.
 */
enum class InstallMode {
    /**
     * Always use Shizuku for silent install and keep-data hidden blocks.
     * Recommended and default mode. Falls back to Normal if Shizuku is unavailable.
     */
    Shizuku,

    /**
     * Always use system PackageInstaller dialogs. Requires the user to grant
     * "install unknown apps" for InstallBlock in system Settings.
     */
    Normal,

    /**
     * Pick automatically: use Shizuku if it is running and permission is granted,
     * fall back to Normal otherwise.
     */
    Auto,
}

