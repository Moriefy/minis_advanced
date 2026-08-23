package com.minis.haprial.search

import com.minis.haprial.search.SearchHttp.await
import com.minis.haprial.search.SearchHttp.bodyString
import com.minis.haprial.search.SearchHttp.queryParamSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object ExaSearchService : SearchService {

    override val providerType = SearchProviderType.EXA

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
            put("numResults", commonOptions.resultSize)
            put("type", "auto")
            put("contents", JSONObject().apply {
                put("text", JSONObject().apply { put("maxCharacters", 500) })
            })
        }

        val request = Request.Builder()
            .url("https://api.exa.ai/search")
            .post(body.toString().toRequestBody(SearchHttp.JSON_MEDIA_TYPE))
            .addHeader("x-api-key", config.apiKey)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) error("Exa search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val results = json.optJSONArray("results")?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                SearchResultItem(
                    title = item.optString("title", ""),
                    url = item.optString("url", ""),
                    text = item.optString("text", item.optString("highlight", ""))
                )
            }
        } ?: emptyList()

        SearchResult(items = results)
    }
}
