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
 * Bocha (博查) search — Chinese web search API.
 */
object BochaSearchService : SearchService {

    override val providerType = SearchProviderType.BOCHA

    override fun toolParameters(config: SearchProviderConfig): JSONObject =
        queryParamSchema()

    override suspend fun search(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): SearchResult = withContext(Dispatchers.IO) {
        val query = params.optString("query", "").ifEmpty { error("query is required") }

        val body = JSONObject().apply {
            put("query", query)
            put("freshness", "noLimit")
            put("summary", config.summary)
            put("count", commonOptions.resultSize)
        }

        val request = Request.Builder()
            .url("https://api.bochaai.com/v1/web-search")
            .post(body.toString().toRequestBody(SearchHttp.JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) error("Bocha search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val data = json.optJSONObject("data") ?: json
        val webPages = data.optJSONObject("webPages")
        val results = webPages?.optJSONArray("value")?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                SearchResultItem(
                    title = item.optString("name", ""),
                    url = item.optString("url", ""),
                    text = item.optString("snippet", "")
                )
            }
        } ?: emptyList()

        val answer = data.optString("answer", "").ifEmpty { null }

        SearchResult(answer = answer, items = results)
    }
}
