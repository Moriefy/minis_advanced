package com.minis.haprial.search

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Shared HTTP utilities for search services.
 */
internal object SearchHttp {

    val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun Call.await(): Response = suspendCoroutine { cont ->
        enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }

    fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

    /** Helper to build standard query parameters for the agent tool schema. */
    fun queryParamSchema(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("query", JSONObject().apply {
                put("type", "string")
                put("description", "search keyword")
            })
        })
        put("required", JSONArray().apply { put("query") })
    }

    /** Helper to build query + topic parameters. */
    fun queryTopicParamSchema(topicValues: List<String>, topicDesc: String): JSONObject =
        JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("query", JSONObject().apply {
                    put("type", "string")
                    put("description", "search keyword")
                })
                put("topic", JSONObject().apply {
                    put("type", "string")
                    put("description", topicDesc)
                    put("enum", JSONArray(topicValues))
                })
            })
            put("required", JSONArray().apply { put("query") })
        }

    /** Helper for scrape tool parameter. */
    fun scrapeParamSchema(): JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("url", JSONObject().apply {
                put("type", "string")
                put("description", "url to scrape")
            })
        })
        put("required", JSONArray().apply { put("url") })
    }

    fun Response.bodyString(): String = this.body?.string() ?: ""
}
