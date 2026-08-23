package com.minis.haprial.search

import com.minis.haprial.search.SearchHttp.await
import com.minis.haprial.search.SearchHttp.bodyString
import com.minis.haprial.search.SearchHttp.queryParamSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object SerperSearchService : SearchService {

    override val providerType = SearchProviderType.SERPER

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
            put("num", commonOptions.resultSize)
        }

        val request = Request.Builder()
            .url("https://google.serper.dev/search")
            .post(body.toString().toRequestBody(SearchHttp.JSON_MEDIA_TYPE))
            .addHeader("X-API-KEY", config.apiKey)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) error("Serper search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val results = json.optJSONArray("organic")?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                SearchResultItem(
                    title = item.optString("title", ""),
                    url = item.optString("link", ""),
                    text = item.optString("snippet", "")
                )
            }
        } ?: emptyList()

        val answer = json.optJSONObject("answerBox")?.optString("answer", "")
            ?.ifEmpty { null }

        SearchResult(answer = answer, items = results)
    }
}
