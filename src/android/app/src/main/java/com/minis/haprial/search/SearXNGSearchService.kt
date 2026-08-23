package com.minis.haprial.search

import com.minis.haprial.search.SearchHttp.await
import com.minis.haprial.search.SearchHttp.bodyString
import com.minis.haprial.search.SearchHttp.urlEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URI

/**
 * SearXNG — self-hosted meta search engine.
 * Requires a user-configured SearXNG instance URL.
 */
object SearXNGSearchService : SearchService {

    override val providerType = SearchProviderType.SEARXNG

    override fun toolParameters(config: SearchProviderConfig): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("query", JSONObject().apply {
                put("type", "string")
                put("description", "search keyword")
            })
            put("categories", JSONObject().apply {
                put("type", "string")
                put("description", "search categories (e.g. general, images, news, science)")
            })
        })
        put("required", org.json.JSONArray().apply { put("query") })
    }

    override suspend fun search(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): SearchResult = withContext(Dispatchers.IO) {
        val query = params.optString("query", "").ifEmpty { error("query is required") }
        val categories = params.optString("categories", "general")
        val baseUrl = config.url.ifEmpty { error("SearXNG instance URL is required") }

        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append("/search?q=${query.urlEncode()}")
            append("&format=json")
            append("&pageno=1")
            if (categories.isNotEmpty()) append("&categories=${categories.urlEncode()}")
            if (config.engines.isNotEmpty()) append("&engines=${config.engines.urlEncode()}")
            if (config.language.isNotEmpty()) append("&language=${config.language.urlEncode()}")
        }

        val builder = Request.Builder().url(url).get()
        if (config.apiKey.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }

        val response = SearchHttp.client.newCall(builder.build()).await()
        if (!response.isSuccessful) error("SearXNG search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val results = json.optJSONArray("results")?.let { arr ->
            (0 until arr.length()).take(commonOptions.resultSize).map { i ->
                val item = arr.getJSONObject(i)
                SearchResultItem(
                    title = item.optString("title", ""),
                    url = item.optString("url", ""),
                    text = item.optString("content", "")
                )
            }
        } ?: emptyList()

        val answer = json.optString("answer", "").ifEmpty { null }

        SearchResult(answer = answer, items = results)
    }
}
