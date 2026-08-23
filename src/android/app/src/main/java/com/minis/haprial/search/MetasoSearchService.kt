package com.minis.haprial.search

import com.minis.haprial.search.SearchHttp.await
import com.minis.haprial.search.SearchHttp.bodyString
import com.minis.haprial.search.SearchHttp.queryParamSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Metaso (秘塔) search — high-quality Chinese search with AI-generated answers.
 * API: https://metaso.cn/
 */
object MetasoSearchService : SearchService {

    override val providerType = SearchProviderType.METASO

    override fun toolParameters(config: SearchProviderConfig): JSONObject =
        queryParamSchema()

    override suspend fun search(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): SearchResult = withContext(Dispatchers.IO) {
        val query = params.optString("query", "").ifEmpty { error("query is required") }

        val body = JSONObject().apply {
            put("q", query)
            put("scope", "webpage")
            put("size", commonOptions.resultSize)
            put("includeSummary", false)
        }

        val request = Request.Builder()
            .url("https://metaso.cn/api/open/search/v2")
            .post(body.toString().toRequestBody(SearchHttp.JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) {
            val errBody = response.bodyString()
            error("Metaso search failed #${response.code}: $errBody")
        }

        val json = JSONObject(response.bodyString())
        // v2 API returns data in nested structure
        val data = json.optJSONObject("data") ?: json
        val webpages = data.optJSONArray("webpages") ?: data.optJSONArray("results")

        val items = webpages?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                SearchResultItem(
                    title = item.optString("title", ""),
                    url = item.optString("link", item.optString("url", "")),
                    text = item.optString("snippet", item.optString("content", ""))
                )
            }
        } ?: emptyList()

        val answer = data.optString("answer", "").ifEmpty {
            data.optString("text", "").ifEmpty { null }
        }

        SearchResult(
            answer = answer,
            items = items
        )
    }
}
