package com.minis.haprial.search

import com.minis.haprial.search.SearchHttp.await
import com.minis.haprial.search.SearchHttp.bodyString
import com.minis.haprial.search.SearchHttp.queryParamSchema
import com.minis.haprial.search.SearchHttp.urlEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object BraveSearchService : SearchService {

    override val providerType = SearchProviderType.BRAVE

    override fun toolParameters(config: SearchProviderConfig): JSONObject =
        queryParamSchema()

    override suspend fun search(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): SearchResult = withContext(Dispatchers.IO) {
        val query = params.optString("query", "").ifEmpty { error("query is required") }

        val url = "https://api.search.brave.com/res/v1/web/search" +
                "?q=${query.urlEncode()}&count=${commonOptions.resultSize}"

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("X-Subscription-Token", config.apiKey)
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) error("Brave search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val web = json.optJSONObject("web")
        val results = web?.optJSONArray("results")?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                SearchResultItem(
                    title = item.optString("title", ""),
                    url = item.optString("url", ""),
                    text = item.optString("description", "")
                )
            }
        } ?: emptyList()

        SearchResult(items = results)
    }
}
