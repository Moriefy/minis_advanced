package com.minis.haprial.search

import com.minis.haprial.search.SearchHttp.await
import com.minis.haprial.search.SearchHttp.bodyString
import com.minis.haprial.search.SearchHttp.queryParamSchema
import com.minis.haprial.search.SearchHttp.queryTopicParamSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Doubao (豆包) search by ByteDance.
 */
object DoubaoSearchService : SearchService {

    override val providerType = SearchProviderType.DOUBAO

    override fun toolParameters(config: SearchProviderConfig): JSONObject =
        queryTopicParamSchema(
            listOf("global", "custom"),
            "search scope (global for worldwide, custom for configured scope)"
        )

    override suspend fun search(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): SearchResult = withContext(Dispatchers.IO) {
        val query = params.optString("query", "").ifEmpty { error("query is required") }

        val body = JSONObject().apply {
            put("query", query)
            put("count", commonOptions.resultSize)
            put("scope", config.mode.ifEmpty { "custom" })
        }

        val request = Request.Builder()
            .url("https://api.doubao.com/search/v1/web")
            .post(body.toString().toRequestBody(SearchHttp.JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) error("Doubao search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val data = json.optJSONObject("data") ?: json
        val results = data.optJSONArray("results")?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                SearchResultItem(
                    title = item.optString("title", ""),
                    url = item.optString("url", item.optString("link", "")),
                    text = item.optString("snippet", item.optString("content", ""))
                )
            }
        } ?: emptyList()

        SearchResult(items = results)
    }
}
