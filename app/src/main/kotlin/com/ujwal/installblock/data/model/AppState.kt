// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.data.model

/**
 * Per-app display state shown in the Browse and Blocked lists.
 *
 * State is derived on demand by querying PackageManager, DevicePolicyManager, and signing certificates.
 */
sealed class AppState {
    /** Not installed (neither the real app nor a placeholder). */
    data object NotBlocked : AppState()

    /** The real app is installed (signed by its own developer, not by us). */
    data object Installed : AppState()

    /** A block operation is currently running. */
    data object Blocking : AppState()

    /**
     * Our placeholder occupies the package name, either as an active installed package
     * (Plain / Locked) or as an uninstalled record with retained data (Hidden).
     *
     * @param type The specific enforcement type used.
     */
    data class Blocked(val type: BlockType = BlockType.Plain) : AppState() {
        companion object {
            val Plain = Blocked(BlockType.Plain)
            val Locked = Blocked(BlockType.Locked)
            val Hidden = Blocked(BlockType.Hidden)
        }
    }

    /**
     * The last block attempt failed.
     *
     * @param reason Human-readable explanation, never a raw error code.
     * @param isVerificationBlock True when Android developer verification rejected the install.
     */
    data class Failed(
        val reason: String,
        val isVerificationBlock: Boolean = false,
    ) : AppState()
}
