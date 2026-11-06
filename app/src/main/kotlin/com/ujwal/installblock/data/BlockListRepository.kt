// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.data

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.ujwal.installblock.data.model.AppEntry
import com.ujwal.installblock.data.model.AppState
import com.ujwal.installblock.data.model.BlockList
import com.ujwal.installblock.data.model.BlockType
import com.ujwal.installblock.placeholder.KeystoreManager
import com.ujwal.installblock.placeholder.PlaceholderBuilder
import com.ujwal.installblock.install.BlockRepositoryGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val TAG = "BlockListRepository"

/** Maximum raw size accepted for the blocklist JSON :  avoids OOM on a malformed response. */
private const val MAX_LIST_BYTES = 2 * 1024 * 1024L // 2 MB

/** Java package name pattern :  rejects obviously invalid entries before they enter the list. */
private val PACKAGE_NAME_RE = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")

/**
 * Fetches and caches the app blocklist from GitHub.
 *
 * Rules:
 * - On launch, return the cached list immediately, then try a conditional GET.
 * - 304 → no change.
 * - 200 → validate, replace cache atomically, emit update.
 * - Any error → keep cache, set [isOffline] = true.
 * - No cache and no network → emit empty list + [isOffline] = true.
 *
 * State updates (per-app [AppState]) are derived here by querying PackageManager and
 * comparing signing certificates. This is intentionally synchronous and cheap because
 * the blocklist is expected to stay well under 10,000 entries for the foreseeable future.
 */
class BlockListRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val keystoreManager: KeystoreManager,
    private val prefs: Prefs,
) : BlockRepositoryGateway {
    private val cacheFile = File(context.filesDir, "blocklist.json")
    private val etagFile  = File(context.filesDir, "blocklist.etag")

    private val json = Json { ignoreUnknownKeys = true }

    private val _apps     = MutableStateFlow<List<AppEntry>>(emptyList())
    private val _states   = MutableStateFlow<Map<String, AppState>>(emptyMap())
    private val _blockedApps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val _mockBlockedApps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val _isOffline = MutableStateFlow(false)
    private val _customApps = MutableStateFlow<List<AppEntry>>(emptyList())

    val apps:      StateFlow<List<AppEntry>> = kotlinx.coroutines.flow.combine(_apps, _customApps) { base, custom ->
        (base + custom).distinctBy { it.packageName }
    }.stateIn(
        kotlinx.coroutines.CoroutineScope(Dispatchers.Default),
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        emptyList(),
    )
    override val states: StateFlow<Map<String, AppState>> = _states.asStateFlow()
    override val blockedApps: StateFlow<List<AppEntry>> = kotlinx.coroutines.flow.combine(_blockedApps, _mockBlockedApps) { real, mock ->
        (real + mock).distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
    }.stateIn(
        kotlinx.coroutines.CoroutineScope(Dispatchers.Default),
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        emptyList(),
    )
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()
    val orphanedBlocks = MutableStateFlow<List<String>>(emptyList())
    val removedOutsideBlocks = MutableStateFlow<List<Pair<String, BlockType>>>(emptyList())

    fun addMockBlocked(entry: AppEntry) {
        _mockBlockedApps.update { (it + entry).distinctBy { e -> e.packageName } }
    }

    fun clearMockBlocked() {
        _mockBlockedApps.value = emptyList()
    }

    fun addCustom(entry: AppEntry) {
        _customApps.update { current -> (current + entry).distinctBy { it.packageName } }
        _states.update { it + (entry.packageName to resolveState(entry.packageName)) }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    /** Loads the cached list synchronously (call from a coroutine on Dispatchers.IO). */
    suspend fun loadCache() = withContext(Dispatchers.IO) {
        keystoreManager.ensureKey()
        if (cacheFile.exists()) {
            parseSafe(cacheFile.readText())?.let { list ->
                val merged = mergeWithBundledSeed(list)
                _apps.value = merged.apps
                _states.value = computeStates(merged.apps)
            }
        } else {
            loadBundledSeed()?.let { list ->
                _apps.value = list.apps
                _states.value = computeStates(list.apps)
            }
        }
        refreshBlockedApps()
        scanOrphanedHiddenBlocks()
    }

    fun scanOrphanedHiddenBlocks(): List<String> {
        val pm = context.packageManager
        val normalInstalled = runCatching {
            pm.getInstalledPackages(0).map { it.packageName }.toSet()
        }.getOrDefault(emptySet())

        val allPackages = runCatching {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            pm.getInstalledPackages(flags or PackageManager.MATCH_UNINSTALLED_PACKAGES)
        }.getOrDefault(emptyList())

        val orphans = allPackages.filter { info ->
            info.packageName !in normalInstalled &&
                (info.versionName == PlaceholderBuilder.PLACEHOLDER_VERSION_NAME ||
                    info.versionCode == PlaceholderBuilder.PLACEHOLDER_VERSION_CODE)
        }.map { it.packageName }

        orphanedBlocks.value = orphans
        return orphans
    }

    fun checkRemovedOutside(customTypes: Map<String, BlockType>) {
        val currentStates = _states.value
        val removed = mutableListOf<Pair<String, BlockType>>()
        for ((pkg, expectedType) in customTypes) {
            val currentState = currentStates[pkg] ?: resolveState(pkg)
            when (expectedType) {
                BlockType.Locked -> {
                    if (currentState !is AppState.Blocked || currentState.type != BlockType.Locked) {
                        removed.add(pkg to BlockType.Locked)
                    }
                }
                BlockType.Hidden -> {
                    if (currentState !is AppState.Blocked || currentState.type != BlockType.Hidden) {
                        removed.add(pkg to BlockType.Hidden)
                    }
                }
                BlockType.Plain -> {
                    if (currentState !is AppState.Blocked) {
                        removed.add(pkg to BlockType.Plain)
                    }
                }
            }
        }
        removedOutsideBlocks.value = removed
    }

    fun dismissRemovedOutside(packageName: String) {
        removedOutsideBlocks.update { list -> list.filterNot { it.first == packageName } }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    /**
     * Performs a conditional GET against [listUrl].
     * Swaps in the new list atomically if the server returns 200.
     */
    suspend fun sync(listUrl: String) = withContext(Dispatchers.IO) {
        keystoreManager.ensureKey()
        val cachedEtag = if (etagFile.exists()) etagFile.readText().trim() else null

        val request = Request.Builder()
            .url(listUrl)
            .apply { cachedEtag?.let { header("If-None-Match", it) } }
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> {
                        // Server confirms our cache is current :  nothing to do.
                        _isOffline.value = false
                    }
                    response.isSuccessful -> {
                        val body = response.body

                        // Guard against oversized responses before we touch memory.
                        val bytes = body.byteStream()
                            .use { it.readBytes(MAX_LIST_BYTES.toInt()) }
                        if (bytes.size >= MAX_LIST_BYTES.toInt()) {
                            return@withContext markOffline("Response exceeded $MAX_LIST_BYTES bytes")
                        }

                        val text = bytes.decodeToString()
                        val list = parseSafe(text)
                            ?.let(::mergeWithBundledSeed)
                            ?: return@withContext markOffline("Parse failed")

                        // Atomic write: temp → rename.
                        val tmp = File(context.filesDir, "blocklist.tmp")
                        tmp.writeText(text)
                        tmp.renameTo(cacheFile)

                        response.header("ETag")?.let { etagFile.writeText(it) }

                        _apps.value = list.apps
                        _states.value = computeStates(list.apps)
                        refreshBlockedApps()
                        _isOffline.value = false
                    }
                    else -> markOffline("HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
            markOffline(e.message ?: "Network error")
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Recomputes the state for a single package and updates the shared flow. */
    override suspend fun refreshState(packageName: String) = withContext(Dispatchers.IO) {
        _states.update { current ->
            current.toMutableMap().also { map ->
                map[packageName] = resolveState(packageName)
            }
        }
        refreshBlockedApps()
    }

    /** Marks a package as [AppState.Blocking] while an operation is in progress. */
    override fun markBlocking(packageName: String) = _states.update { it + (packageName to AppState.Blocking) }

    /** Marks a package with the terminal result of a block attempt. */
    override fun markResult(packageName: String, state: AppState) = _states.update { it + (packageName to state) }

    /**
     * Finds every installed or hidden-kept package signed by InstallBlock or bearing placeholder marker,
     * including packages no longer present in the current remote blocklist.
     */
    override suspend fun refreshBlockedApps() = withContext(Dispatchers.IO) {
        val ourCert = keystoreManager.certificate
        val knownApps = apps.value.associateBy { it.packageName }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val allPkgs = runCatching {
            context.packageManager.getInstalledPackages(flags or PackageManager.MATCH_UNINSTALLED_PACKAGES)
        }.getOrDefault(emptyList())

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val activeAdmins = dpm?.activeAdmins?.map { it.packageName }?.toSet().orEmpty()
        val normalSet = runCatching {
            context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
        }.getOrDefault(emptySet())

        val newlyDiscoveredStates = mutableMapOf<String, AppState>()
        val blocked = allPkgs.mapNotNull { info ->
            val certs = signingCertificates(info)
            val certMatches = ourCert != null && certs.any { it.contentEquals(ourCert.encoded) }
            @Suppress("DEPRECATION")
            val markerMatches = info.versionName == PlaceholderBuilder.PLACEHOLDER_VERSION_NAME ||
                info.versionCode == PlaceholderBuilder.PLACEHOLDER_VERSION_CODE ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.longVersionCode == PlaceholderBuilder.PLACEHOLDER_VERSION_CODE.toLong())
            if (!certMatches && !markerMatches) return@mapNotNull null

            val pkg = info.packageName
            val state = if (pkg in normalSet) {
                if (pkg in activeAdmins) AppState.Blocked(BlockType.Locked) else AppState.Blocked(BlockType.Plain)
            } else {
                AppState.Blocked(BlockType.Hidden)
            }
            newlyDiscoveredStates[pkg] = state

            val known = knownApps[pkg]
            known ?: AppEntry(
                name = pkg,
                packageName = pkg,
                iconUrl = "",
            )
        }
        _states.update { it + newlyDiscoveredStates }
        _blockedApps.value = blocked.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun computeStates(apps: List<AppEntry>): Map<String, AppState> {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val normalSet = runCatching {
            pm.getInstalledPackages(0).map { it.packageName }.toSet()
        }.getOrDefault(emptySet())

        val allPackages = runCatching {
            pm.getInstalledPackages(flags or PackageManager.MATCH_UNINSTALLED_PACKAGES).associateBy { it.packageName }
        }.getOrDefault(emptyMap())

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val activeAdmins = dpm?.activeAdmins?.map { it.packageName }?.toSet().orEmpty()
        val ourCert = keystoreManager.certificate?.encoded

        return apps.associate { entry ->
            val pkg = entry.packageName
            val info = allPackages[pkg]
            val state = if (info == null) {
                AppState.NotBlocked
            } else {
                val certs = signingCertificates(info)
                val isOurApp = ourCert != null && certs.any { it.contentEquals(ourCert) }
                @Suppress("DEPRECATION")
                val isMarker = info.versionName == PlaceholderBuilder.PLACEHOLDER_VERSION_NAME ||
                    info.versionCode == PlaceholderBuilder.PLACEHOLDER_VERSION_CODE ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.longVersionCode == PlaceholderBuilder.PLACEHOLDER_VERSION_CODE.toLong())
                if (isOurApp || isMarker) {
                    if (pkg in normalSet) {
                        if (pkg in activeAdmins) AppState.Blocked(BlockType.Locked) else AppState.Blocked(BlockType.Plain)
                    } else {
                        AppState.Blocked(BlockType.Hidden)
                    }
                } else {
                    if (pkg in normalSet) AppState.Installed else AppState.NotBlocked
                }
            }
            pkg to state
        }
    }

    private fun resolveState(packageName: String): AppState {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val ourCert = keystoreManager.certificate

        // 1. Check normal active installation
        val activeInfo = try {
            pm.getPackageInfo(packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        if (activeInfo != null) {
            val certs = signingCertificates(activeInfo)
            val isOurApp = ourCert != null && certs.any { it.contentEquals(ourCert.encoded) }
            @Suppress("DEPRECATION")
            val isMarker = activeInfo.versionName == PlaceholderBuilder.PLACEHOLDER_VERSION_NAME ||
                activeInfo.versionCode == PlaceholderBuilder.PLACEHOLDER_VERSION_CODE ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activeInfo.longVersionCode == PlaceholderBuilder.PLACEHOLDER_VERSION_CODE.toLong())
            if (isOurApp || isMarker) {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                val isAdmin = dpm?.activeAdmins?.any { it.packageName == packageName } == true
                return if (isAdmin) AppState.Blocked(BlockType.Locked) else AppState.Blocked(BlockType.Plain)
            }
            return AppState.Installed
        }

        // 2. Check uninstalled-kept package record (Hidden block)
        val hiddenInfo = try {
            pm.getPackageInfo(packageName, flags or PackageManager.MATCH_UNINSTALLED_PACKAGES)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

        if (hiddenInfo != null) {
            val certs = signingCertificates(hiddenInfo)
            val isOurApp = ourCert != null && certs.any { it.contentEquals(ourCert.encoded) }
            @Suppress("DEPRECATION")
            val isMarker = hiddenInfo.versionName == PlaceholderBuilder.PLACEHOLDER_VERSION_NAME ||
                hiddenInfo.versionCode == PlaceholderBuilder.PLACEHOLDER_VERSION_CODE ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hiddenInfo.longVersionCode == PlaceholderBuilder.PLACEHOLDER_VERSION_CODE.toLong())
            if (isOurApp || isMarker) {
                return AppState.Blocked(BlockType.Hidden)
            }
        }

        return AppState.NotBlocked
    }

    private fun signingCertificates(info: android.content.pm.PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            val signers = if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory ?: signingInfo?.apkContentsSigners
            }
            signers?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toByteArray() }.orEmpty()
        }

    private fun parseSafe(text: String): BlockList? = try {
        val list = json.decodeFromString<BlockList>(text)
        BlockList(
            version = list.version,
            apps = list.apps.filter { entry ->
                val validName    = PACKAGE_NAME_RE.matches(entry.packageName)
                val validIconUrl = entry.iconUrl.isBlank() ||
                    entry.iconUrl.startsWith("https://") ||
                    BUNDLED_ICON_RE.matches(entry.iconUrl)
                if (!validName || !validIconUrl) {
                    Log.w(TAG, "Dropped invalid entry: ${entry.packageName}")
                }
                validName && validIconUrl
            }.map { entry ->
                entry.copy(name = displayName(entry.name))
            }
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse blocklist", e)
        null
    }

    private fun loadBundledSeed(): BlockList? = runCatching {
        context.assets.open("blocklist_seed.json").bufferedReader().use { reader ->
            parseSafe(reader.readText())
        }
    }.onFailure { Log.w(TAG, "Bundled seed list unavailable", it) }.getOrNull()

    private fun mergeWithBundledSeed(remote: BlockList): BlockList {
        val seed = loadBundledSeed() ?: return remote
        val merged = (seed.apps + remote.apps).associateBy { it.packageName }.values.toList()
        return BlockList(version = maxOf(seed.version, remote.version), apps = merged)
    }

    private fun markOffline(reason: String) {
        Log.w(TAG, "Going offline: $reason")
        _isOffline.value = true
    }

    private fun displayName(value: String): String =
        value.substringBefore(':')
            .substringBefore('|')
            .substringBefore('•')
            .substringBefore(" - ")
            .trim()
            .ifEmpty { value }

    // InputStream.readBytes with a size cap so we don't OOM on a monster response.
    private fun java.io.InputStream.readBytes(maxBytes: Int): ByteArray {
        val buf = ByteArray(maxBytes)
        var total = 0
        while (total < maxBytes) {
            val read = read(buf, total, maxBytes - total)
            if (read == -1) break
            total += read
        }
        return buf.copyOf(total)
    }

    private companion object {
        val BUNDLED_ICON_RE = Regex("""^asset://icons/[a-z0-9_-]+\.png$""")
    }
}
