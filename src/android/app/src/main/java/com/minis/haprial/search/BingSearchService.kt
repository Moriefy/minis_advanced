package com.minis.haprial.search

import com.minis.haprial.search.SearchHttp.await
import com.minis.haprial.search.SearchHttp.bodyString
import com.minis.haprial.search.SearchHttp.queryParamSchema
import com.minis.haprial.search.SearchHttp.urlEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Bing search via the Bing Web Search API.
 * Requires a Bing API key (Azure Cognitive Services).
 */
object BingSearchService : SearchService {

    override val providerType = SearchProviderType.BING

    override fun toolParameters(config: SearchProviderConfig): JSONObject =
        queryParamSchema()

    override suspend fun search(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): SearchResult = withContext(Dispatchers.IO) {
        val query = params.optString("query", "").ifEmpty { error("query is required") }

        val url = "https://api.bing.microsoft.com/v7.0/search" +
                "?q=${query.urlEncode()}&count=${commonOptions.resultSize}"

        val request = Request.Builder()
            .url(url)
            .addHeader("Ocp-Apim-Subscription-Key", config.apiKey)
            .addHeader("Accept", "application/json")
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) error("Bing search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val webPages = json.optJSONObject("webPages")
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

        val answer = json.optJSONObject("rankingResponse")
            ?.optJSONObject("mainline")
            ?.optJSONArray("items")
            ?.optJSONObject(0)
            ?.optString("answerType", "")
            ?.ifEmpty { null }

        SearchResult(items = results)
    }
}
