package com.minis.haprial.search

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent storage for search provider configurations.
 * Uses SharedPreferences with JSON serialisation.
 *
 * Data model:
 * - providers: ordered list of SearchProviderConfig (user can add/remove/reorder)
 * - selectedIndex: which provider is currently active
 * - commonOptions: shared settings (result count, etc.)
 */
class SearchSettingsStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    /** All configured search providers, in display order. */
    var providers: List<SearchProviderConfig>
        get() {
            val json = prefs.getString(KEY_PROVIDERS, null) ?: return listOf(DEFAULT_PROVIDER)
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { SearchProviderConfig.fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                listOf(DEFAULT_PROVIDER)
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(it.toJson()) }
            prefs.edit().putString(KEY_PROVIDERS, arr.toString()).apply()
        }

    /** Index of the currently selected provider. */
    var selectedIndex: Int
        get() = prefs.getInt(KEY_SELECTED, 0)
        set(value) = prefs.edit().putInt(KEY_SELECTED, value).apply()

    /** Shared search options. */
    var commonOptions: SearchCommonOptions
        get() {
            val json = prefs.getString(KEY_COMMON, null) ?: return SearchCommonOptions()
            return try {
                SearchCommonOptions.fromJson(JSONObject(json))
            } catch (_: Exception) {
                SearchCommonOptions()
            }
        }
        set(value) = prefs.edit().putString(KEY_COMMON, value.toJson().toString()).apply()

    /** Get the currently active provider config. */
    fun getActiveProvider(): SearchProviderConfig {
        val list = providers
        return list.getOrElse(selectedIndex.coerceIn(0, list.lastIndex)) { list.first() }
    }

    /** Add a new provider. */
    fun addProvider(config: SearchProviderConfig) {
        providers = listOf(config) + providers
        selectedIndex = 0
    }

    /** Remove a provider by index. */
    fun removeProvider(index: Int) {
        val list = providers.toMutableList()
        if (list.size <= 1) return // keep at least one
        if (index < 0 || index >= list.size) return
        list.removeAt(index)
        providers = list
        if (selectedIndex >= list.size) selectedIndex = list.lastIndex
        if (selectedIndex < 0) selectedIndex = 0
    }

    /** Update a provider at a specific index. */
    fun updateProvider(index: Int, config: SearchProviderConfig) {
        val list = providers.toMutableList()
        if (index < 0 || index >= list.size) return
        list[index] = config
        providers = list
    }

    /** Move a provider from one position to another (for reordering). */
    fun moveProvider(from: Int, to: Int) {
        val list = providers.toMutableList()
        if (from < 0 || from >= list.size || to < 0 || to >= list.size) return
        val item = list.removeAt(from)
        list.add(to, item)
        providers = list
        // Adjust selected index
        selectedIndex = when (selectedIndex) {
            from -> to
            in minOf(from, to)..maxOf(from, to) -> if (from < to) selectedIndex - 1 else selectedIndex + 1
            else -> selectedIndex
        }.coerceIn(0, list.lastIndex)
    }

    companion object {
        private const val PREFS_NAME = "minis_search_settings"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_SELECTED = "selected_index"
        private const val KEY_COMMON = "common_options"

        val DEFAULT_PROVIDER = SearchProviderConfig(
            type = SearchProviderType.BING,
            apiKey = ""
        )

        @Volatile
        private var instance: SearchSettingsStore? = null

        fun getInstance(context: Context): SearchSettingsStore {
            return instance ?: synchronized(this) {
                instance ?: SearchSettingsStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
