package com.minis.haprial.search

import com.minis.haprial.search.SearchHttp.await
import com.minis.haprial.search.SearchHttp.bodyString
import com.minis.haprial.search.SearchHttp.queryParamSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object LinkUpSearchService : SearchService {

    override val providerType = SearchProviderType.LINKUP

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
            put("depth", config.depth.ifEmpty { "standard" })
            put("outputType", "searchResults")
        }

        val request = Request.Builder()
            .url("https://api.linkup.so/v1/search")
            .post(body.toString().toRequestBody(SearchHttp.JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) error("LinkUp search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val results = json.optJSONArray("results")?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                SearchResultItem(
                    title = item.optString("title", ""),
                    url = item.optString("url", ""),
                    text = item.optString("content", item.optString("description", ""))
                )
            }
        } ?: emptyList()

        val answer = json.optString("answer", "").ifEmpty { null }

        SearchResult(answer = answer, items = results)
    }
}
