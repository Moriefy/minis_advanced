package com.minis.haprial.search

import com.minis.haprial.search.SearchHttp.await
import com.minis.haprial.search.SearchHttp.bodyString
import com.minis.haprial.search.SearchHttp.queryParamSchema
import com.minis.haprial.search.SearchHttp.scrapeParamSchema
import com.minis.haprial.search.SearchHttp.urlEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

object JinaSearchService : SearchService {

    override val providerType = SearchProviderType.JINA

    override fun toolParameters(config: SearchProviderConfig): JSONObject =
        queryParamSchema()

    override fun supportsScraping(config: SearchProviderConfig): Boolean = true

    override suspend fun search(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): SearchResult = withContext(Dispatchers.IO) {
        val query = params.optString("query", "").ifEmpty { error("query is required") }

        val searchUrl = config.searchUrl.ifEmpty { "https://s.jina.ai/" }
        val url = "${searchUrl.trimEnd('/')}/${query.urlEncode()}"

        val builder = Request.Builder().url(url)
        if (config.apiKey.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }
        builder.addHeader("Accept", "application/json")

        val response = SearchHttp.client.newCall(builder.build()).await()
        if (!response.isSuccessful) error("Jina search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val data = json.optJSONObject("data") ?: json
        val results = data.optJSONArray("results")?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                SearchResultItem(
                    title = item.optString("title", ""),
                    url = item.optString("url", ""),
                    text = item.optString("description", item.optString("content", ""))
                )
            }
        } ?: emptyList()

        val answer = json.optString("answer", "").ifEmpty { null }

        SearchResult(answer = answer, items = results)
    }

    override suspend fun scrape(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): ScrapedResult = withContext(Dispatchers.IO) {
        val url = params.optString("url", "").ifEmpty { error("url is required") }

        val scrapeUrl = config.scrapeUrl.ifEmpty { "https://r.jina.ai/" }
        val fullUrl = "${scrapeUrl.trimEnd('/')}/${url}"

        val builder = Request.Builder().url(fullUrl)
        if (config.apiKey.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }
        builder.addHeader("Accept", "application/json")

        val response = SearchHttp.client.newCall(builder.build()).await()
        if (!response.isSuccessful) error("Jina scrape failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val data = json.optJSONObject("data") ?: json

        ScrapedResult(
            url = url,
            content = data.optString("content", json.optString("text", "")),
            title = data.optString("title", "")
        )
    }
}
