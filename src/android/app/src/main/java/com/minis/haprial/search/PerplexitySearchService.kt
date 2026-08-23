package com.minis.haprial.search

import com.minis.haprial.search.SearchHttp.await
import com.minis.haprial.search.SearchHttp.bodyString
import com.minis.haprial.search.SearchHttp.queryParamSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Perplexity search — uses the chat completions API with web search.
 */
object PerplexitySearchService : SearchService {

    override val providerType = SearchProviderType.PERPLEXITY

    override fun toolParameters(config: SearchProviderConfig): JSONObject =
        queryParamSchema()

    override suspend fun search(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): SearchResult = withContext(Dispatchers.IO) {
        val query = params.optString("query", "").ifEmpty { error("query is required") }

        val body = JSONObject().apply {
            put("model", "sonar")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", query)
                })
            })
            config.maxTokens?.let { put("max_tokens", it) }
        }

        val request = Request.Builder()
            .url("https://api.perplexity.ai/chat/completions")
            .post(body.toString().toRequestBody(SearchHttp.JSON_MEDIA_TYPE))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = SearchHttp.client.newCall(request).await()
        if (!response.isSuccessful) error("Perplexity search failed #${response.code}")

        val json = JSONObject(response.bodyString())
        val answer = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content", "")
            ?.ifEmpty { null }

        // Extract citations from the response
        val citations = json.optJSONArray("citations")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()

        val items = citations.mapIndexed { index, url ->
            SearchResultItem(
                title = "Reference ${index + 1}",
                url = url,
                text = ""
            )
        }

        SearchResult(answer = answer, items = items)
    }
}
