package com.minis.haprial.search

import org.json.JSONArray
import org.json.JSONObject

/**
 * Common options shared across all search providers.
 */
data class SearchCommonOptions(
    val resultSize: Int = 10
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("resultSize", resultSize)
    }

    companion object {
        fun fromJson(json: JSONObject): SearchCommonOptions = SearchCommonOptions(
            resultSize = json.optInt("resultSize", 10)
        )
    }
}

/**
 * A single search result item.
 */
data class SearchResultItem(
    val title: String,
    val url: String,
    val text: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("url", url)
        put("text", text)
    }
}

/**
 * Full search result returned by a provider.
 */
data class SearchResult(
    val answer: String? = null,
    val items: List<SearchResultItem>,
    val images: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        if (answer != null) put("answer", answer)
        put("items", JSONArray().apply {
            items.forEachIndexed { index, item ->
                put(item.toJson().apply {
                    put("id", index.toString().take(6))
                    put("index", index + 1)
                })
            }
        })
        if (images.isNotEmpty()) {
            put("images", JSONArray(images))
        }
    }
}

/**
 * Result of scraping a URL.
 */
data class ScrapedResult(
    val url: String,
    val content: String,
    val title: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("url", url)
        put("content", content)
        if (title != null) put("title", title)
    }
}

/**
 * Enumeration of all supported search provider types.
 * Each type maps to a concrete SearchService implementation and
 * a corresponding SearchProviderConfig.
 */
enum class SearchProviderType(val displayName: String) {
    TAVILY("Tavily"),
    BRAVE("Brave"),
    METASO("Metaso"),
    EXA("Exa"),
    SERPER("Serper"),
    JINA("Jina"),
    ZHIPU("Zhipu"),
    BOCHA("Bocha"),
    DOUBAO("Doubao"),
    PERPLEXITY("Perplexity"),
    LINKUP("LinkUp"),
    SEARXNG("SearXNG"),
    BING("Bing");
}

/**
 * Per-provider configuration, serialised to/from JSON for persistence.
 * Fields vary by provider type; unused fields are simply absent.
 */
data class SearchProviderConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: SearchProviderType,
    val apiKey: String = "",
    // Tavily / LinkUp
    val depth: String = "advanced",
    // SearXNG
    val url: String = "",
    val engines: String = "",
    val language: String = "",
    // Doubao
    val mode: String = "custom",
    // Perplexity
    val maxTokens: Int? = null,
    // Bocha
    val summary: Boolean = true,
    // Jina
    val searchUrl: String = "https://s.jina.ai/",
    val scrapeUrl: String = "https://r.jina.ai/"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        if (apiKey.isNotEmpty()) put("apiKey", apiKey)
        if (depth != "advanced") put("depth", depth)
        if (url.isNotEmpty()) put("url", url)
        if (engines.isNotEmpty()) put("engines", engines)
        if (language.isNotEmpty()) put("language", language)
        if (mode != "custom") put("mode", mode)
        if (maxTokens != null) put("maxTokens", maxTokens)
        if (!summary) put("summary", false)
        if (searchUrl != "https://s.jina.ai/") put("searchUrl", searchUrl)
        if (scrapeUrl != "https://r.jina.ai/") put("scrapeUrl", scrapeUrl)
    }

    companion object {
        fun fromJson(json: JSONObject): SearchProviderConfig = SearchProviderConfig(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            type = try {
                SearchProviderType.valueOf(json.optString("type", "BING"))
            } catch (_: Exception) {
                SearchProviderType.BING
            },
            apiKey = json.optString("apiKey", ""),
            depth = json.optString("depth", "advanced"),
            url = json.optString("url", ""),
            engines = json.optString("engines", ""),
            language = json.optString("language", ""),
            mode = json.optString("mode", "custom"),
            maxTokens = json.optInt("maxTokens").takeIf { it > 0 },
            summary = json.optBoolean("summary", true),
            searchUrl = json.optString("searchUrl", "https://s.jina.ai/"),
            scrapeUrl = json.optString("scrapeUrl", "https://r.jina.ai/")
        )
    }
}
