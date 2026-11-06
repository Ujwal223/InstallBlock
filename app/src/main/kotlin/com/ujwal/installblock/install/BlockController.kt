// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.install

import android.content.Context
import com.ujwal.installblock.data.BlockListRepository
import com.ujwal.installblock.data.model.AppState
import com.ujwal.installblock.data.model.BlockType
import com.ujwal.installblock.placeholder.Denylist
import com.ujwal.installblock.placeholder.KeystoreManager
import com.ujwal.installblock.placeholder.PlaceholderBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import com.ujwal.installblock.data.model.AppEntry

interface InstallerGateway {
    fun isAvailable(): Boolean
    suspend fun install(apkBytes: ByteArray, packageName: String): Result<Unit>
    suspend fun uninstall(packageName: String): Result<Unit>
    suspend fun uninstallKeepData(packageName: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Keep-data uninstall requires Shizuku"))
    suspend fun clearData(packageName: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Clear data requires Shizuku"))
    suspend fun installExisting(packageName: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Install-existing requires Shizuku"))
    suspend fun setActiveAdmin(packageName: String, adminClass: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Device admin activation requires Shizuku"))
    suspend fun removeActiveAdmin(packageName: String, adminClass: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Device admin deactivation requires Shizuku"))
}

interface BlockRepositoryGateway {
    val states: kotlinx.coroutines.flow.StateFlow<Map<String, AppState>>
    val blockedApps: kotlinx.coroutines.flow.StateFlow<List<AppEntry>>
    fun markBlocking(packageName: String)
    fun markResult(packageName: String, state: AppState)
    suspend fun refreshState(packageName: String)
    suspend fun refreshBlockedApps()
}

/** Progress events emitted by [BlockController.blockFlow]. */
sealed class BlockProgress {
    /** The requested Shizuku mode was unavailable, so normal mode will be used. */
    data object FallingBackToNormal : BlockProgress()
    /** The real app is being uninstalled before the placeholder is installed. */
    data object UninstallingReal : BlockProgress()
    /** The signed placeholder APK is being built in memory. */
    data object BuildingPlaceholder : BlockProgress()
    /** The placeholder APK is being submitted to the installer. */
    data object InstallingPlaceholder : BlockProgress()
    /** Querying PackageManager to confirm the placeholder is now installed. */
    data object Verifying : BlockProgress()
    /** Block completed successfully. */
    data object Done : BlockProgress()
    /** Block failed. [isVerificationBlock] is true when Play Protect is the cause. */
    data class Failed(val reason: String, val isVerificationBlock: Boolean = false) : BlockProgress()
}

data class BatchBlockProgress(
    val completed: Int,
    val total: Int,
    val packageName: String,
    val result: BlockProgress,
)

/**
 * Orchestrates the full block / unblock lifecycle.
 *
 * Auto mode prefers Shizuku when it is available because Shizuku bypasses the system
 * installer UI; Normal mode is the silent fallback that still works without root.
 *
 * The class is intentionally stateless :  callers own all lifecycle management.
 */
class BlockController(
    private val normal: InstallerGateway,
    private val shizuku: InstallerGateway,
    private val placeholderBuilder: PlaceholderBuilder,
    private val keystoreManager: KeystoreManager,
    private val repository: BlockRepositoryGateway,
) {

    /**
     * Emits [BlockProgress] events as the block operation proceeds.
     *
     * Sequence:
     * 1. Denylist guard :  immediately fails if [packageName] is on the system denylist.
     * 2. If currently [AppState.Installed]: uninstall the real app first.
     * 3. Build the signed placeholder APK.
     * 4. Install the placeholder.
     * 5. Verify via [BlockListRepository.refreshState].
     * 6. Emit [BlockProgress.Done] or [BlockProgress.Failed].
     */
    fun blockFlow(
        context: Context,
        packageName: String,
        appName: String,
        mode: InstallMode,
        blockType: BlockType = BlockType.Hidden,
    ): Flow<BlockProgress> = flow {
        repository.markBlocking(packageName)
        val effectiveMode = resolveMode(mode)
        if (mode == InstallMode.Shizuku && effectiveMode == InstallMode.Normal) {
            emit(BlockProgress.FallingBackToNormal)
        }

        if (effectiveMode != InstallMode.Shizuku && blockType == BlockType.Hidden && mode == InstallMode.Normal) {
            val failure = BlockProgress.Failed("Hidden blocks require Shizuku mode")
            repository.markResult(packageName, AppState.Failed(failure.reason))
            emit(failure)
            return@flow
        }

        if (blockType == BlockType.Locked && effectiveMode != InstallMode.Shizuku) {
            val failure = BlockProgress.Failed("Locked blocks require Shizuku mode")
            repository.markResult(packageName, AppState.Failed(failure.reason))
            emit(failure)
            return@flow
        }

        if (Denylist.blocks(context, packageName)) {
            val failure = BlockProgress.Failed("$packageName is on the system denylist")
            repository.markResult(packageName, AppState.Failed(failure.reason))
            emit(failure)
            return@flow
        }

        val currentState = repository.states.value[packageName]
        if (currentState == AppState.Installed) {
            emit(BlockProgress.UninstallingReal)
            val uninstallResult = resolveUninstall(packageName, effectiveMode)
            if (uninstallResult.isFailure) {
                val e = uninstallResult.exceptionOrNull()
                val failure = BlockProgress.Failed(e?.message ?: "Uninstall failed")
                repository.markResult(packageName, AppState.Installed)
                emit(failure)
                return@flow
            }
        }

        emit(BlockProgress.BuildingPlaceholder)
        val apkBytes = runCatching {
            placeholderBuilder.build(context, packageName, appName, keystoreManager)
        }.getOrElse { e ->
            val failure = BlockProgress.Failed(e.message ?: "APK build failed")
            repository.markResult(packageName, AppState.Failed(failure.reason))
            emit(failure)
            return@flow
        }

        emit(BlockProgress.InstallingPlaceholder)
        val installResult = resolveInstall(apkBytes, packageName, effectiveMode)
        if (installResult.isFailure) {
            val e = installResult.exceptionOrNull()
            val isVerification = e is InstallException && e.isVerificationBlock
            val failure = BlockProgress.Failed(e?.message ?: "Install failed", isVerification)
            repository.markResult(
                packageName,
                AppState.Failed(failure.reason, failure.isVerificationBlock),
            )
            emit(failure)
            return@flow
        }

        // When using Shizuku, Hidden (keep-data uninstall) is the default and standard mechanism
        if (effectiveMode == InstallMode.Shizuku || blockType == BlockType.Hidden) {
            val keepDataResult = shizuku.uninstallKeepData(packageName)
            if (keepDataResult.isFailure) {
                val e = keepDataResult.exceptionOrNull()
                val failure = BlockProgress.Failed(e?.message ?: "Keep-data uninstall failed")
                repository.markResult(packageName, AppState.Failed(failure.reason))
                emit(failure)
                return@flow
            }
        } else if (blockType == BlockType.Locked) {
            val adminResult = shizuku.setActiveAdmin(packageName, "com.ujwal.installblock.placeholder.BlockAdmin")
            if (adminResult.isFailure) {
                val e = adminResult.exceptionOrNull()
                val failure = BlockProgress.Failed(e?.message ?: "Setting device admin failed")
                repository.markResult(packageName, AppState.Failed(failure.reason))
                emit(failure)
                return@flow
            }
        }

        emit(BlockProgress.Verifying)
        repository.refreshState(packageName)
        val verifiedState = repository.states.value[packageName]
        if (verifiedState is AppState.Blocked) {
            repository.markResult(packageName, verifiedState)
            repository.refreshBlockedApps()
            emit(BlockProgress.Done)
        } else {
            val failure = BlockProgress.Failed("Placeholder installed but signing cert did not match")
            repository.markResult(packageName, AppState.Failed(failure.reason))
            emit(failure)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Blocks several packages without changing mode between items.
     * Batch mode intentionally requires Shizuku to avoid repeated system dialogs.
     */
    fun blockBatchFlow(
        context: Context,
        entries: List<AppEntry>,
        blockType: BlockType = BlockType.Hidden,
    ): Flow<BatchBlockProgress> = flow {
        if (!shizuku.isAvailable()) {
            emit(
                BatchBlockProgress(
                    completed = 0,
                    total = entries.size,
                    packageName = "",
                    result = BlockProgress.Failed("Shizuku is required for batch blocking"),
                ),
            )
            return@flow
        }
        entries.forEachIndexed { index, entry ->
            blockFlow(
                context = context,
                packageName = entry.packageName,
                appName = entry.name,
                mode = InstallMode.Shizuku,
                blockType = blockType,
            ).collect { result ->
                emit(BatchBlockProgress(index + 1, entries.size, entry.packageName, result))
            }
        }
    }

    /**
     * Unblocks [packageName], removing device admin if locked or cleaning residual data if hidden.
     */
    suspend fun unblock(context: Context, packageName: String, mode: InstallMode): Result<Unit> {
        var currentState = repository.states.value[packageName]
        if (currentState == null) {
            repository.refreshState(packageName)
            currentState = repository.states.value[packageName]
        }
        val effectiveMode = resolveMode(mode)
        val result = if (currentState is AppState.Blocked && currentState.type == BlockType.Hidden) {
            // A hidden block was uninstalled with -k, leaving residual data in /data/data/<pkg>
            // and an uninstalled record in PackageManager. Clear data to wipe residual state completely.
            val clearResult = shizuku.clearData(packageName)
            val uninstallResult = shizuku.uninstall(packageName)
            if (uninstallResult.isSuccess || clearResult.isSuccess) {
                Result.success(Unit)
            } else {
                uninstallResult
            }
        } else if (currentState is AppState.Blocked && currentState.type == BlockType.Locked) {
            // Remove device admin before attempting uninstall
            shizuku.removeActiveAdmin(packageName, "com.ujwal.installblock.placeholder.BlockAdmin")
            resolveUninstall(packageName, effectiveMode)
        } else {
            resolveUninstall(packageName, effectiveMode)
        }
        repository.refreshState(packageName)
        repository.refreshBlockedApps()
        return result
    }

    /**
     * Unblocks all currently blocked apps across all block types in one operation.
     */
    fun releaseAll(context: Context, mode: InstallMode = InstallMode.Shizuku): Flow<BatchBlockProgress> = flow {
        val blockedFromStates = repository.states.value.filter { it.value is AppState.Blocked }.keys
        val blockedFromApps = repository.blockedApps.value.map { it.packageName }
        val blockedList = (blockedFromStates + blockedFromApps).distinct()
        if (blockedList.isEmpty()) {
            emit(BatchBlockProgress(0, 0, "", BlockProgress.Done))
            return@flow
        }
        blockedList.forEachIndexed { index, pkg ->
            val result = unblock(context, pkg, mode)
            val progress = if (result.isSuccess) {
                BlockProgress.Done
            } else {
                BlockProgress.Failed(result.exceptionOrNull()?.message ?: "Unblock failed")
            }
            emit(BatchBlockProgress(index + 1, blockedList.size, pkg, progress))
        }
    }.flowOn(Dispatchers.IO)

    // ── Private ───────────────────────────────────────────────────────────────

    /** Auto: Shizuku when available (silent, no UI prompt), Normal otherwise. */
    private fun resolveMode(mode: InstallMode): InstallMode =
        if (mode == InstallMode.Auto) {
            if (shizuku.isAvailable()) InstallMode.Shizuku else InstallMode.Normal
        } else if (mode == InstallMode.Shizuku && !shizuku.isAvailable()) {
            InstallMode.Normal
        } else {
            mode
        }

    private suspend fun resolveInstall(
        apkBytes: ByteArray,
        packageName: String,
        mode: InstallMode,
    ): Result<Unit> = when (mode) {
        InstallMode.Shizuku -> shizuku.install(apkBytes, packageName)
        else                -> normal.install(apkBytes, packageName)
    }

    private suspend fun resolveUninstall(packageName: String, mode: InstallMode): Result<Unit> =
        when (mode) {
            InstallMode.Shizuku -> shizuku.uninstall(packageName)
            else                -> normal.uninstall(packageName)
        }
}
