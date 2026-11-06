// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.install

import android.content.ContextWrapper
import com.ujwal.installblock.data.Prefs
import com.ujwal.installblock.data.model.AppState
import com.ujwal.installblock.data.model.BlockType
import com.ujwal.installblock.placeholder.KeystoreManager
import com.ujwal.installblock.placeholder.PlaceholderBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BlockControllerTest {
    @Test
    fun blockingDenylistedPackageEmitsFailureWithoutFlowContextCrash() = runTest {
        val repository = FakeRepository()
        val controller = BlockController(
            normal = FakeInstaller(available = true),
            shizuku = FakeInstaller(available = false),
            placeholderBuilder = PlaceholderBuilder(),
            keystoreManager = KeystoreManager(),
            repository = repository,
        )

        val progress = controller.blockFlow(
            context = ContextWrapper(null),
            packageName = "com.ujwal.installblock",
            appName = "InstallBlock",
            mode = InstallMode.Auto,
        ).toList()

        assertEquals(1, progress.size)
        val failure = assertIs<BlockProgress.Failed>(progress.single())
        assertEquals(AppState.Failed(failure.reason), repository.states.value["com.ujwal.installblock"])

        val progressBlocker = controller.blockFlow(
            context = ContextWrapper(null),
            packageName = "com.ujwal.installblocker",
            appName = "InstallBlocker",
            mode = InstallMode.Auto,
        ).toList()
        val failureBlocker = assertIs<BlockProgress.Failed>(progressBlocker.single())
        assertEquals(AppState.Failed(failureBlocker.reason), repository.states.value["com.ujwal.installblocker"])
    }

    @Test
    fun hiddenBlockRequiresShizukuMode() = runTest {
        val repository = FakeRepository()
        val controller = BlockController(
            normal = FakeInstaller(available = true),
            shizuku = FakeInstaller(available = false),
            placeholderBuilder = PlaceholderBuilder(),
            keystoreManager = KeystoreManager(),
            repository = repository,
        )

        val progress = controller.blockFlow(
            context = ContextWrapper(null),
            packageName = "com.example.distraction",
            appName = "Distraction",
            mode = InstallMode.Normal,
            blockType = BlockType.Hidden,
        ).toList()

        val failure = assertIs<BlockProgress.Failed>(progress.single())
        assertTrue(failure.reason.contains("Hidden blocks require Shizuku"))
    }

    @Test
    fun lockedBlockRequiresShizukuMode() = runTest {
        val repository = FakeRepository()
        val controller = BlockController(
            normal = FakeInstaller(available = true),
            shizuku = FakeInstaller(available = false),
            placeholderBuilder = PlaceholderBuilder(),
            keystoreManager = KeystoreManager(),
            repository = repository,
        )

        val progress = controller.blockFlow(
            context = ContextWrapper(null),
            packageName = "com.example.distraction",
            appName = "Distraction",
            mode = InstallMode.Normal,
            blockType = BlockType.Locked,
        ).toList()

        val failure = assertIs<BlockProgress.Failed>(progress.single())
        assertTrue(failure.reason.contains("Locked blocks require Shizuku"))
    }

    @Test
    fun releaseAllUnblocksAllBlockedPackages() = runTest {
        val repository = FakeRepository()
        repository.states.value = mapOf(
            "com.app.one" to AppState.Blocked(BlockType.Plain),
            "com.app.two" to AppState.Blocked(BlockType.Hidden),
            "com.app.three" to AppState.Installed,
        )
        val fakeShizuku = FakeInstaller(available = true)
        val controller = BlockController(
            normal = FakeInstaller(available = true),
            shizuku = fakeShizuku,
            placeholderBuilder = PlaceholderBuilder(),
            keystoreManager = KeystoreManager(),
            repository = repository,
        )

        val results = controller.releaseAll(ContextWrapper(null), InstallMode.Shizuku).toList()
        assertEquals(2, results.size)
        assertEquals(2, results.last().total)
        assertEquals(2, results.last().completed)
        assertIs<BlockProgress.Done>(results[0].result)
        assertIs<BlockProgress.Done>(results[1].result)
    }

    @Test
    fun unblockHiddenBlockClearsResidualData() = runTest {
        val repository = FakeRepository()
        repository.states.value = mapOf(
            "com.example.hidden" to AppState.Blocked(BlockType.Hidden),
        )
        val fakeShizuku = FakeInstaller(available = true)
        val controller = BlockController(
            normal = FakeInstaller(available = true),
            shizuku = fakeShizuku,
            placeholderBuilder = PlaceholderBuilder(),
            keystoreManager = KeystoreManager(),
            repository = repository,
        )

        val result = controller.unblock(ContextWrapper(null), "com.example.hidden", InstallMode.Shizuku)
        assertTrue(result.isSuccess)
        assertEquals("com.example.hidden", fakeShizuku.clearedDataPackage)
    }

    @Test
    fun releaseAllIncludesOrphanedBlockedAppsNotInStates() = runTest {
        val repository = FakeRepository()
        repository.states.value = mapOf(
            "com.app.one" to AppState.Blocked(BlockType.Plain),
        )
        repository.blockedApps.value = listOf(
            com.ujwal.installblock.data.model.AppEntry(
                name = "Orphaned App",
                packageName = "com.orphaned.app",
                iconUrl = "",
            ),
        )
        val fakeShizuku = FakeInstaller(available = true)
        val controller = BlockController(
            normal = FakeInstaller(available = true),
            shizuku = fakeShizuku,
            placeholderBuilder = PlaceholderBuilder(),
            keystoreManager = KeystoreManager(),
            repository = repository,
        )

        val results = controller.releaseAll(ContextWrapper(null), InstallMode.Shizuku).toList()
        assertEquals(2, results.size)
        assertEquals(2, results.last().total)
        assertEquals(2, results.last().completed)
        val unblockedPackages = results.map { it.packageName }
        assertTrue(unblockedPackages.contains("com.app.one"))
        assertTrue(unblockedPackages.contains("com.orphaned.app"))
    }

    @Test
    fun blocklistSourceIsFixedToTheBundledRepository() {
        assertEquals(
            "https://raw.githubusercontent.com/ujwal/installblock/main/blocklist.json",
            Prefs.DEFAULT_LIST_URL,
        )
    }
}

private class FakeInstaller(
    private val available: Boolean,
) : InstallerGateway {
    var installedPackage: String? = null
    var uninstalledPackage: String? = null
    var keptDataPackage: String? = null
    var clearedDataPackage: String? = null
    var installedExistingPackage: String? = null
    var activeAdminPackage: String? = null
    var removedAdminPackage: String? = null

    override fun isAvailable() = available
    override suspend fun install(apkBytes: ByteArray, packageName: String): Result<Unit> {
        installedPackage = packageName
        return Result.success(Unit)
    }
    override suspend fun uninstall(packageName: String): Result<Unit> {
        uninstalledPackage = packageName
        return Result.success(Unit)
    }
    override suspend fun uninstallKeepData(packageName: String): Result<Unit> {
        keptDataPackage = packageName
        return Result.success(Unit)
    }
    override suspend fun clearData(packageName: String): Result<Unit> {
        clearedDataPackage = packageName
        return Result.success(Unit)
    }
    override suspend fun installExisting(packageName: String): Result<Unit> {
        installedExistingPackage = packageName
        return Result.success(Unit)
    }
    override suspend fun setActiveAdmin(packageName: String, adminClass: String): Result<Unit> {
        activeAdminPackage = packageName
        return Result.success(Unit)
    }
    override suspend fun removeActiveAdmin(packageName: String, adminClass: String): Result<Unit> {
        removedAdminPackage = packageName
        return Result.success(Unit)
    }
}

private class FakeRepository : BlockRepositoryGateway {
    override val states = MutableStateFlow<Map<String, AppState>>(emptyMap())
    override val blockedApps = MutableStateFlow<List<com.ujwal.installblock.data.model.AppEntry>>(emptyList())

    override fun markBlocking(packageName: String) {
        states.value = states.value + (packageName to AppState.Blocking)
    }

    override fun markResult(packageName: String, state: AppState) {
        states.value = states.value + (packageName to state)
    }

    override suspend fun refreshState(packageName: String) = Unit
    override suspend fun refreshBlockedApps() = Unit
}
