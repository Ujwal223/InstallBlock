// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ujwal.installblock.data.BlockListRepository
import com.ujwal.installblock.data.Prefs
import com.ujwal.installblock.data.model.AppEntry
import com.ujwal.installblock.data.model.AppState
import com.ujwal.installblock.data.model.BlockType
import com.ujwal.installblock.install.BlockController
import com.ujwal.installblock.install.BlockProgress
import com.ujwal.installblock.install.InstallMode
import com.ujwal.installblock.install.ShizukuState
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import android.graphics.drawable.Drawable

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    private val repository: BlockListRepository = app.repository
    private val controller: BlockController = app.blockController
    private val prefs: Prefs = app.prefs

    val apps = repository.apps
    val states = repository.states
    val blockedApps = repository.blockedApps
    val isOffline = repository.isOffline
    val installMode = prefs.installMode
    val defaultBlockType = prefs.defaultBlockType
    val customBlockTypes = prefs.customBlockTypes
    val explainedLockedBlock = prefs.explainedLockedBlock
    val explainedHiddenBlock = prefs.explainedHiddenBlock
    val protectBlocksAdmin = prefs.protectBlocksAdmin
    val orphanedBlocks = repository.orphanedBlocks
    val removedOutsideBlocks = repository.removedOutsideBlocks
    val loadIcons = prefs.loadIcons
    val darkTheme = prefs.darkTheme
    val isOnboardingDoneSync: Boolean
        get() = prefs.isOnboardingDoneSync
    val onboardingDone: StateFlow<Boolean?> = prefs.onboardingDone
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            if (prefs.isOnboardingDoneSync) true else null,
        )
    val shizukuPermissionGranted: Boolean
        get() {
            val override = _debugShizukuOverride.value
            if (override != null) return override == ShizukuState.Ready
            return app.shizukuInstaller.hasPermission()
        }
    private val iconRepository = app.iconRepository
    val listUrl = prefs.listUrl

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    @OptIn(FlowPreview::class)
    private val debouncedQuery = _query.debounce { if (it.isEmpty()) 0L else 100L }
    private val recentPackages = prefs.recentPackages
    private val _sort = MutableStateFlow(AppSort.Alphabetical)
    val sort: StateFlow<AppSort> = _sort.asStateFlow()
    val filteredApps: StateFlow<List<AppEntry>> = combine(apps, debouncedQuery, _sort, recentPackages) { entries, value, sort, recent ->
        val needle = value.trim().lowercase()
        val filtered = if (needle.isEmpty()) entries else entries.filter {
            it.name.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
        }
        when (sort) {
            AppSort.Alphabetical -> filtered.sortedBy { it.name.lowercase() }
            AppSort.Popularity -> filtered.sortedByDescending { it.installCount }
            AppSort.RecentlyAccessed -> filtered.sortedWith(
                compareByDescending<AppEntry> { recent[it.packageName] ?: 0L }.thenBy { it.name.lowercase() },
            )
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _progress = MutableStateFlow<BlockProgress?>(null)
    val progress: StateFlow<BlockProgress?> = _progress.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            repository.loadCache()
            repository.sync(prefs.listUrl.first())
            repository.checkRemovedOutside(prefs.customBlockTypes.first())
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val list = apps.first()
                list.take(15).forEach { entry ->
                    if (entry.iconUrl.isNotBlank()) {
                        iconRepository.load(entry.iconUrl)
                    }
                }
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setSort(value: AppSort) {
        _sort.value = value
    }

    fun recordAccess(entry: AppEntry) {
        viewModelScope.launch { prefs.recordRecent(entry.packageName) }
    }

    fun setInstallMode(mode: InstallMode) {
        viewModelScope.launch { prefs.setInstallMode(mode) }
    }

    fun setLoadIcons(value: Boolean) {
        viewModelScope.launch { prefs.setLoadIcons(value) }
    }

    fun setDarkTheme(value: String) {
        viewModelScope.launch { prefs.setDarkTheme(value) }
    }

    fun completeOnboarding(isBasicMode: Boolean = false) {
        _debugOnboardingStartPage.value = 0
        viewModelScope.launch {
            prefs.setInstallMode(if (isBasicMode) InstallMode.Normal else InstallMode.Shizuku)
            prefs.setOnboardingDone(true)
        }
    }

    suspend fun iconFile(url: String): File? = iconRepository.load(url)

    suspend fun iconFileForPackage(packageName: String): File? =
        iconRepository.loadForPackage(packageName)

    private val appIconCache = android.util.LruCache<String, Drawable>(250)
    private val appIconNegativeCache = java.util.Collections.synchronizedSet(HashSet<String>())

    fun appIcon(packageName: String): Drawable? {
        if (packageName in appIconNegativeCache) return null
        val cached = synchronized(appIconCache) { appIconCache.get(packageName) }
        if (cached != null) return cached

        val icon = runCatching {
            getApplication<Application>().packageManager.getApplicationIcon(packageName)
        }.getOrNull()

        if (icon != null) {
            synchronized(appIconCache) { appIconCache.put(packageName, icon) }
        } else {
            appIconNegativeCache.add(packageName)
        }
        return icon
    }

    fun setDefaultBlockType(type: BlockType) {
        viewModelScope.launch { prefs.setDefaultBlockType(type) }
    }

    fun setCustomBlockType(packageName: String, type: BlockType) {
        viewModelScope.launch { prefs.setCustomBlockType(packageName, type) }
    }

    fun setExplainedLockedBlock(value: Boolean) {
        viewModelScope.launch { prefs.setExplainedLockedBlock(value) }
    }

    fun setExplainedHiddenBlock(value: Boolean) {
        viewModelScope.launch { prefs.setExplainedHiddenBlock(value) }
    }

    fun setProtectBlocksAdmin(value: Boolean) {
        viewModelScope.launch { prefs.setProtectBlocksAdmin(value) }
    }

    fun dismissRemovedOutside(packageName: String) {
        repository.dismissRemovedOutside(packageName)
        viewModelScope.launch {
            prefs.removeCustomBlockType(packageName)
        }
    }

    private val _debugShizukuOverride = MutableStateFlow<ShizukuState?>(null)
    val debugShizukuOverride: StateFlow<ShizukuState?> = _debugShizukuOverride.asStateFlow()

    private val _shizukuState = MutableStateFlow(app.shizukuInstaller.getShizukuState())
    val shizukuState: StateFlow<ShizukuState> = _shizukuState.asStateFlow()

    fun refreshShizukuState() {
        _shizukuState.value = getShizukuState()
    }

    fun debugSetShizukuStateOverride(state: ShizukuState?) {
        _debugShizukuOverride.value = state
        _shizukuState.value = getShizukuState()
    }

    fun getShizukuState(): ShizukuState =
        _debugShizukuOverride.value ?: app.shizukuInstaller.getShizukuState()

    private val _debugOnboardingStartPage = MutableStateFlow(0)
    val debugOnboardingStartPage: StateFlow<Int> = _debugOnboardingStartPage.asStateFlow()

    fun debugResetOnboarding(startPage: Int = 0) {
        _debugOnboardingStartPage.value = startPage
        viewModelScope.launch { prefs.setOnboardingDone(false) }
    }

    fun debugForceSyncCatalog() {
        viewModelScope.launch {
            val etagFile = File(getApplication<Application>().filesDir, "blocklist.etag")
            if (etagFile.exists()) etagFile.delete()
            repository.sync(prefs.listUrl.first())
        }
    }

    fun debugSimulateState(packageName: String, state: AppState) {
        repository.markResult(packageName, state)
    }

    fun debugSimulateProgress(progress: BlockProgress) {
        _progress.value = progress
    }

    fun debugAddDummyBlocked(packageName: String, name: String) {
        val entry = AppEntry(name = name, packageName = packageName, iconUrl = "", category = "Entertainment")
        repository.addCustom(entry)
        repository.markResult(packageName, AppState.Blocked(BlockType.Hidden))
        repository.addMockBlocked(entry)
    }

    fun debugClearDummyBlocked() {
        repository.clearMockBlocked()
    }

    fun debugClearAllCache() {
        val context = getApplication<Application>()
        File(context.filesDir, "blocklist.json").delete()
        File(context.filesDir, "blocklist.etag").delete()
        viewModelScope.launch { repository.loadCache() }
    }

    fun block(entry: AppEntry) {
        recordAccess(entry)
        viewModelScope.launch {
            val mode = prefs.installMode.first()
            controller.blockFlow(
                context = getApplication(),
                packageName = entry.packageName,
                appName = entry.name,
                mode = mode,
                blockType = BlockType.Hidden,
            ).collect {
                if (it == BlockProgress.FallingBackToNormal) {
                    _notice.value = "Shizuku is unavailable. Continuing with normal system dialogs."
                }
                _progress.value = it
            }
        }
    }

    fun addCustomPackage(packageName: String): Result<AppEntry> {
        val trimmed = packageName.trim()
        if (!PACKAGE_NAME_RE.matches(trimmed)) {
            return Result.failure(IllegalArgumentException("Enter a valid Android package name."))
        }
        val app = getApplication<Application>()
        if (com.ujwal.installblock.placeholder.Denylist.blocks(app, trimmed)) {
            return Result.failure(IllegalArgumentException("This app cannot be blocked."))
        }
        val pm = app.packageManager
        val entry = runCatching {
            val info = pm.getApplicationInfo(trimmed, 0)
            AppEntry(
                name = pm.getApplicationLabel(info).toString(),
                packageName = trimmed,
                iconUrl = "",
                category = "Custom",
            )
        }.getOrElse {
            AppEntry(trimmed, trimmed, "", "Custom")
        }
        repository.addCustom(entry)
        return Result.success(entry)
    }

    fun blockBatch(entries: List<AppEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            controller.blockBatchFlow(getApplication(), entries, BlockType.Hidden).collect { batch ->
                _progress.value = batch.result
            }
        }
    }

    fun unblock(packageName: String) {
        viewModelScope.launch {
            val mode = prefs.installMode.first()
            _progress.value = BlockProgress.UninstallingReal
            controller.unblock(getApplication(), packageName, mode)
                .onSuccess {
                    prefs.removeCustomBlockType(packageName)
                    _progress.value = BlockProgress.Done
                }
                .onFailure { _progress.value = BlockProgress.Failed(it.message ?: "Uninstall failed") }
        }
    }

    fun releaseAll() {
        viewModelScope.launch {
            val mode = prefs.installMode.first()
            controller.releaseAll(getApplication(), mode).collect { batch ->
                _progress.value = batch.result
                if (batch.packageName.isNotEmpty()) {
                    prefs.removeCustomBlockType(batch.packageName)
                }
            }
        }
    }

    fun clearProgress() {
        _progress.value = null
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun requestShizukuPermission() {
        if (app.shizukuInstaller.hasPermission()) return
        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
    }

    fun state(packageName: String): AppState =
        states.value[packageName] ?: AppState.NotBlocked

    private companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        val PACKAGE_NAME_RE = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")
    }
}

enum class AppSort { Alphabetical, Popularity, RecentlyAccessed }
