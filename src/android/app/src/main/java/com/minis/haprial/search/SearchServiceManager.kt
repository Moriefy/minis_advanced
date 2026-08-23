package com.minis.haprial.search

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android search service manager — mirrors iOS SearchServiceManager.
 * Manages search provider configuration and persistence.
 */
class SearchServiceManager(context: Context) {

    data class SearchConfig(
        val type: String = "brave", // brave, tavily, exa, jina, serper, bing, searxng, firecrawl, perplexity
        val apiKey: String = "",
        val customUrl: String = "",
        val depth: String = "advanced"
    )

    private val prefs: SharedPreferences = context.getSharedPreferences("minis_search", Context.MODE_PRIVATE)

    var searchEnabled: Boolean
        get() = prefs.getBoolean("search_enabled", false)
        set(value) = prefs.edit().putBoolean("search_enabled", value).apply()

    var selectedIndex: Int
        get() = prefs.getInt("selected_index", 0)
        set(value) = prefs.edit().putInt("selected_index", value).apply()

    var configs: List<SearchConfig>
        get() {
            val json = prefs.getString("configs", null) ?: return defaultConfigs()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    SearchConfig(
                        type = obj.optString("type", "brave"),
                        apiKey = obj.optString("apiKey", ""),
                        customUrl = obj.optString("customUrl", ""),
                        depth = obj.optString("depth", "advanced")
                    )
                }
            } catch (e: Exception) {
                defaultConfigs()
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { config ->
                arr.put(JSONObject().apply {
                    put("type", config.type)
                    put("apiKey", config.apiKey)
                    put("customUrl", config.customUrl)
                    put("depth", config.depth)
                })
            }
            prefs.edit().putString("configs", arr.toString()).apply()
        }

    val selectedConfig: SearchConfig?
        get() {
            val list = configs
            return if (selectedIndex in list.indices) list[selectedIndex] else null
        }

    private fun defaultConfigs() = listOf(
        SearchConfig(type = "brave"),
        SearchConfig(type = "tavily"),
        SearchConfig(type = "bing")
    )

    companion object {
        val DISPLAY_NAMES = mapOf(
            "brave" to "Brave",
            "tavily" to "Tavily",
            "exa" to "Exa",
            "jina" to "Jina",
            "serper" to "Serper",
            "bing" to "Bing",
            "searxng" to "SearXNG",
            "firecrawl" to "Firecrawl",
            "perplexity" to "Perplexity"
        )

        val API_KEY_URLS = mapOf(
            "brave" to "https://api.search.brave.com/",
            "tavily" to "https://app.tavily.com/home",
            "exa" to "https://exa.ai/",
            "jina" to "https://jina.ai/",
            "serper" to "https://serper.dev/",
            "firecrawl" to "https://firecrawl.dev/",
            "perplexity" to "https://www.perplexity.ai/"
        )
    }
}
