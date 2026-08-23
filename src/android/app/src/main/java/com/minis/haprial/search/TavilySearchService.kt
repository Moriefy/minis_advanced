package com.minis.haprial.search

import com.minis.haprial.search.SearchHttp.await
import com.minis.haprial.search.SearchHttp.bodyString
import com.minis.haprial.search.SearchHttp.queryTopicParamSchema
import com.minis.haprial.search.SearchHttp.scrapeParamSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object TavilySearchService : SearchService {

    override val providerType = SearchProviderType.TAVILY

    override fun toolParameters(config: SearchProviderConfig): JSONObject =
        queryTopicParamSchema(
            listOf("general", "news", "finance"),
            "search topic (one of general, news, finance)"
        )

    override fun supportsScraping(config: SearchProviderConfig): Boolean = true

    override suspend fun search(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): SearchResult = withContext(Dispatchers.IO) {
        val query = params.optString("query", "").ifEmpty { error("query is required") }
        val topic = params.optString("topic", "general").let {
            if (it in listOf("general", "news", "finance")) it else "general"
        }

        val body = JSONObject().apply {
            put("query", query)
            put("max_results", commonOptions.resultSize)
            put("search_depth", config.depth.ifEmpty { "advanced" })
            put("topic", topic)
            put("include_answer", true)
            put("include_images", true)
        }

        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(body.toString().toRequestBody(SearchHttp.JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) error("Tavily search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val results = json.optJSONArray("results")?.let { arr ->
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                SearchResultItem(
                    title = item.optString("title", ""),
                    url = item.optString("url", ""),
                    text = item.optString("content", "")
                )
            }
        } ?: emptyList()

        val images = json.optJSONArray("images")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()

        SearchResult(
            answer = json.optString("answer", "").ifEmpty { null },
            items = results,
            images = images
        )
    }

    override suspend fun scrape(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): ScrapedResult = withContext(Dispatchers.IO) {
        val url = params.optString("url", "").ifEmpty { error("url is required") }

        val body = JSONObject().apply {
            put("urls", org.json.JSONArray().apply { put(url) })
        }

        val request = Request.Builder()
            .url("https://api.tavily.com/extract")
            .post(body.toString().toRequestBody(SearchHttp.JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) error("Tavily scrape failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val result = json.optJSONArray("results")?.optJSONObject(0)
        ScrapedResult(
            url = url,
            content = result?.optString("raw_content", "") ?: ""
        )
    }
}
