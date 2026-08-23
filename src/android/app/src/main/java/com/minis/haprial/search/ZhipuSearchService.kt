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
 * Zhipu (智谱) web search API.
 */
object ZhipuSearchService : SearchService {

    override val providerType = SearchProviderType.ZHIPU

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
            put("search_params", JSONObject().apply {
                put("count", commonOptions.resultSize)
            })
        }

        val request = Request.Builder()
            .url("https://open.bigmodel.cn/api/paas/v4/web/search")
            .post(body.toString().toRequestBody(SearchHttp.JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) error("Zhipu search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val results = json.optJSONArray("search_result")?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                SearchResultItem(
                    title = item.optString("title", ""),
                    url = item.optString("link", ""),
                    text = item.optString("content", "")
                )
            }
        } ?: emptyList()

        SearchResult(items = results)
    }
}
