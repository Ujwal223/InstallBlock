// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ujwal.installblock.data.model.BlockType
import com.ujwal.installblock.install.InstallMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "installblock_prefs")

/**
 * Thin wrapper around DataStore.
 *
 * All keys live here so callers never construct raw preference keys themselves.
 */
class Prefs(private val context: Context) {

    private val syncPrefs = context.getSharedPreferences("installblock_sync", Context.MODE_PRIVATE)

    var isOnboardingDoneSync: Boolean
        get() = syncPrefs.getBoolean("onboarding_done", false)
        private set(value) = syncPrefs.edit().putBoolean("onboarding_done", value).apply()

    private object Keys {
        val INSTALL_MODE            = stringPreferencesKey("install_mode")
        val DEFAULT_BLOCK_TYPE      = stringPreferencesKey("default_block_type")
        val CUSTOM_BLOCK_TYPES      = stringPreferencesKey("custom_block_types")
        val LOAD_ICONS              = booleanPreferencesKey("load_icons")
        val DARK_THEME              = stringPreferencesKey("dark_theme") // "auto" | "light" | "dark"
        val ONBOARDING_DONE         = booleanPreferencesKey("onboarding_done")
        val RECENT_PACKAGES         = stringPreferencesKey("recent_packages")
        val EXPLAINED_LOCKED_BLOCK  = booleanPreferencesKey("explained_locked_block")
        val EXPLAINED_HIDDEN_BLOCK  = booleanPreferencesKey("explained_hidden_block")
        val PROTECT_BLOCKS_ADMIN    = booleanPreferencesKey("protect_blocks_admin")
    }

    companion object {
        const val DEFAULT_LIST_URL = "https://raw.githubusercontent.com/ujwal/installblock/main/blocklist.json"
    }

    // Default install mode: Shizuku is primary per Requirement SH-1
    val installMode: Flow<InstallMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.INSTALL_MODE]?.let { runCatching { InstallMode.valueOf(it) }.getOrNull() }
            ?: InstallMode.Shizuku
    }

    // Default block type: Hidden is default for Shizuku
    val defaultBlockType: Flow<BlockType> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_BLOCK_TYPE]?.let { runCatching { BlockType.valueOf(it) }.getOrNull() }
            ?: BlockType.Hidden
    }

    val customBlockTypes: Flow<Map<String, BlockType>> = context.dataStore.data.map { prefs ->
        decodeCustomBlockTypes(prefs[Keys.CUSTOM_BLOCK_TYPES].orEmpty())
    }

    val listUrl: Flow<String> = flowOf(DEFAULT_LIST_URL)

    val loadIcons: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOAD_ICONS] ?: true
    }

    val darkTheme: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_THEME] ?: "auto"
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val done = prefs[Keys.ONBOARDING_DONE] ?: false
        if (done && !isOnboardingDoneSync) {
            isOnboardingDoneSync = true
        }
        done
    }

    val recentPackages: Flow<Map<String, Long>> = context.dataStore.data.map { prefs ->
        decodeRecent(prefs[Keys.RECENT_PACKAGES].orEmpty())
    }

    val explainedLockedBlock: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.EXPLAINED_LOCKED_BLOCK] ?: false
    }

    val explainedHiddenBlock: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.EXPLAINED_HIDDEN_BLOCK] ?: false
    }

    val protectBlocksAdmin: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PROTECT_BLOCKS_ADMIN] ?: false
    }

    suspend fun setInstallMode(mode: InstallMode)         = context.dataStore.edit { it[Keys.INSTALL_MODE] = mode.name }
    suspend fun setDefaultBlockType(type: BlockType)      = context.dataStore.edit { it[Keys.DEFAULT_BLOCK_TYPE] = type.name }
    suspend fun setLoadIcons(load: Boolean)               = context.dataStore.edit { it[Keys.LOAD_ICONS] = load }
    suspend fun setDarkTheme(theme: String)               = context.dataStore.edit { it[Keys.DARK_THEME] = theme }
    suspend fun setOnboardingDone(done: Boolean) {
        isOnboardingDoneSync = done
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }
    suspend fun setExplainedLockedBlock(value: Boolean)   = context.dataStore.edit { it[Keys.EXPLAINED_LOCKED_BLOCK] = value }
    suspend fun setExplainedHiddenBlock(value: Boolean)   = context.dataStore.edit { it[Keys.EXPLAINED_HIDDEN_BLOCK] = value }
    suspend fun setProtectBlocksAdmin(value: Boolean)     = context.dataStore.edit { it[Keys.PROTECT_BLOCKS_ADMIN] = value }

    suspend fun setCustomBlockType(packageName: String, type: BlockType) = context.dataStore.edit { prefs ->
        val current = decodeCustomBlockTypes(prefs[Keys.CUSTOM_BLOCK_TYPES].orEmpty()).toMutableMap()
        current[packageName] = type
        prefs[Keys.CUSTOM_BLOCK_TYPES] = Json.encodeToString(current.mapValues { it.value.name })
    }

    suspend fun removeCustomBlockType(packageName: String) = context.dataStore.edit { prefs ->
        val current = decodeCustomBlockTypes(prefs[Keys.CUSTOM_BLOCK_TYPES].orEmpty()).toMutableMap()
        current.remove(packageName)
        prefs[Keys.CUSTOM_BLOCK_TYPES] = Json.encodeToString(current.mapValues { it.value.name })
    }

    suspend fun recordRecent(packageName: String) = context.dataStore.edit { preferences ->
        val updated = decodeRecent(preferences[Keys.RECENT_PACKAGES].orEmpty())
            .toMutableMap()
            .also { it[packageName] = System.currentTimeMillis() }
        preferences[Keys.RECENT_PACKAGES] = Json.encodeToString(updated)
    }

    private fun decodeRecent(value: String): Map<String, Long> =
        runCatching { Json.decodeFromString<Map<String, Long>>(value) }.getOrDefault(emptyMap())

    private fun decodeCustomBlockTypes(value: String): Map<String, BlockType> =
        runCatching {
            val raw = Json.decodeFromString<Map<String, String>>(value)
            raw.mapNotNull { (k, v) ->
                runCatching { BlockType.valueOf(v) }.getOrNull()?.let { k to it }
            }.toMap()
        }.getOrDefault(emptyMap())
}
