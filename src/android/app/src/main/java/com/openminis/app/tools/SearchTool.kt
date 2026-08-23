package com.openminis.app.tools

import com.openminis.app.data.model.AgentToolDefinition
import com.openminis.app.data.model.AgentToolParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Android search tool — ports the search service abstraction from iOS.
 * Supports Brave, Tavily, Exa, Jina, Serper, Bing, SearXNG, Firecrawl, Perplexity.
 */
object SearchTool {

    // MARK: - Tool Definitions

    fun searchWebDefinition(): AgentToolDefinition = AgentToolDefinition(
        name = "search_web",
        description = "Search the web for up-to-date or specific information using a search API. " +
            "Use this when you need current facts, latest news, or verification. " +
            "Generate focused keywords and run multiple searches if needed. " +
            "Returns: items with title, url, text; optional answer summary. " +
            "After using results, cite sources with the url.",
        parameters = mapOf(
            "tool_title" to AgentToolParam(
                type = "string",
                description = "A concise 5-10 word summary of what this search does, shown to the user."
            ),
            "query" to AgentToolParam(
                type = "string",
                description = "The search query. Use specific, focused keywords for best results."
            )
        ),
        required = listOf("tool_title", "query"),
        propertyOrdering = listOf("tool_title", "query")
    )

    fun scrapeWebDefinition(): AgentToolDefinition = AgentToolDefinition(
        name = "scrape_web",
        description = "Scrape a URL for detailed page content using the search service. " +
            "Use when search snippets are not enough and you need the full page content.",
        parameters = mapOf(
            "tool_title" to AgentToolParam(
                type = "string",
                description = "A concise 5-10 word summary of what this scrape does."
            ),
            "url" to AgentToolParam(
                type = "string",
                description = "The URL to scrape for content."
            )
        ),
        required = listOf("tool_title", "url"),
        propertyOrdering = listOf("tool_title", "url")
    )

    // MARK: - Search Service Config

    data class SearchConfig(
        val type: String, // "brave", "tavily", "exa", "jina", "serper", "bing", "searxng", "firecrawl", "perplexity"
        val apiKey: String = "",
        val customUrl: String = "",
        val depth: String = "advanced"
    )

    // MARK: - Execute

    suspend fun execute(
        toolName: String,
        args: JSONObject,
        config: SearchConfig?
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        val cfg = config ?: return@withContext ToolExecutionResult(
            output = "Error: No search service configured. Please configure a search provider in Settings.",
            success = false
        )

        when (toolName) {
            "search_web" -> executeSearch(args, cfg)
            "scrape_web" -> executeScrape(args, cfg)
            else -> ToolExecutionResult("Unknown search tool: $toolName", false)
        }
    }

    private suspend fun executeSearch(args: JSONObject, config: SearchConfig): ToolExecutionResult {
        val query = args.optString("query", "")
        if (query.isEmpty()) return ToolExecutionResult("Error: Missing required 'query' parameter.", false)

        return try {
            val result = when (config.type) {
                "brave" -> searchBrave(query, config)
                "tavily" -> searchTavily(query, config)
                "exa" -> searchExa(query, config)
                "jina" -> searchJina(query, config)
                "serper" -> searchSerper(query, config)
                "bing" -> searchBing(query, config)
                "searxng" -> searchSearXNG(query, config)
                "firecrawl" -> searchFirecrawl(query, config)
                "perplexity" -> searchPerplexity(query, config)
                else -> return ToolExecutionResult("Error: Unknown search provider: ${config.type}", false)
            }
            ToolExecutionResult(result, true)
        } catch (e: Exception) {
            ToolExecutionResult("Error: ${e.message}", false)
        }
    }

    private suspend fun executeScrape(args: JSONObject, config: SearchConfig): ToolExecutionResult {
        val url = args.optString("url", "")
        if (url.isEmpty()) return ToolExecutionResult("Error: Missing required 'url' parameter.", false)

        return try {
            val result = when (config.type) {
                "tavily" -> scrapeTavily(url, config)
                "jina" -> scrapeJina(url, config)
                else -> return ToolExecutionResult("Error: Scraping not supported for ${config.type}", false)
            }
            ToolExecutionResult(result, true)
        } catch (e: Exception) {
            ToolExecutionResult("Error: ${e.message}", false)
        }
    }

    // MARK: - Provider Implementations

    private fun searchBrave(query: String, config: SearchConfig): String {
        if (config.apiKey.isEmpty()) throw IllegalStateException("Brave API key not configured")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://api.search.brave.com/res/v1/web/search?q=$encoded&count=10")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("X-Subscription-Token", config.apiKey)
        val body = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(body)
        val web = json.optJSONObject("web")
        val results = web?.optJSONArray("results") ?: return "No results found."
        return formatResults(results, "title", "url", "description")
    }

    private fun searchTavily(query: String, config: SearchConfig): String {
        if (config.apiKey.isEmpty()) throw IllegalStateException("Tavily API key not configured")
        val body = JSONObject().apply {
            put("query", query)
            put("max_results", 10)
            put("search_depth", config.depth.ifEmpty { "advanced" })
            put("include_answer", true)
        }
        val url = URL("https://api.tavily.com/search")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        conn.doOutput = true
        conn.outputStream.write(body.toString().toByteArray())
        val resp = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(resp)
        val answer = json.optString("answer", "")
        val results = json.optJSONArray("results") ?: return "No results found."
        val items = formatResults(results, "title", "url", "content")
        return if (answer.isNotEmpty()) "## Answer\n$answer\n\n## Results\n$items" else "## Results\n$items"
    }

    private fun searchExa(query: String, config: SearchConfig): String {
        if (config.apiKey.isEmpty()) throw IllegalStateException("Exa API key not configured")
        val body = JSONObject().apply {
            put("query", query)
            put("numResults", 10)
            put("type", "neural")
        }
        val url = URL("https://api.exa.ai/search")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-api-key", config.apiKey)
        conn.doOutput = true
        conn.outputStream.write(body.toString().toByteArray())
        val resp = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(resp)
        val results = json.optJSONArray("results") ?: return "No results found."
        return formatResults(results, "title", "url", "text")
    }

    private fun searchJina(query: String, config: SearchConfig): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://s.jina.ai/$encoded")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/json")
        if (config.apiKey.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        val resp = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(resp)
        val data = json.optJSONArray("data") ?: return "No results found."
        return formatResults(data, "title", "url", "description")
    }

    private fun searchSerper(query: String, config: SearchConfig): String {
        if (config.apiKey.isEmpty()) throw IllegalStateException("Serper API key not configured")
        val body = JSONObject().apply { put("q", query); put("num", 10) }
        val url = URL("https://google.serper.dev/search")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-API-KEY", config.apiKey)
        conn.doOutput = true
        conn.outputStream.write(body.toString().toByteArray())
        val resp = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(resp)
        val results = json.optJSONArray("organic") ?: return "No results found."
        return formatResults(results, "title", "link", "snippet")
    }

    private fun searchBing(query: String, config: SearchConfig): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://www.bing.com/search?q=$encoded&count=10")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        val html = conn.inputStream.bufferedReader().readText()
        return parseBingHTML(html)
    }

    private fun searchSearXNG(query: String, config: SearchConfig): String {
        if (config.customUrl.isEmpty()) throw IllegalStateException("SearXNG base URL not configured")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("${config.customUrl}/search?q=$encoded&format=json")
        val conn = url.openConnection() as HttpURLConnection
        val resp = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(resp)
        val results = json.optJSONArray("results") ?: return "No results found."
        return formatResults(results, "title", "url", "content")
    }

    private fun searchFirecrawl(query: String, config: SearchConfig): String {
        if (config.apiKey.isEmpty()) throw IllegalStateException("Firecrawl API key not configured")
        val body = JSONObject().apply { put("query", query); put("limit", 10) }
        val url = URL("https://api.firecrawl.dev/v1/search")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        conn.doOutput = true
        conn.outputStream.write(body.toString().toByteArray())
        val resp = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(resp)
        val data = json.optJSONArray("data") ?: return "No results found."
        return formatResults(data, "title", "url", "markdown")
    }

    private fun searchPerplexity(query: String, config: SearchConfig): String {
        if (config.apiKey.isEmpty()) throw IllegalStateException("Perplexity API key not configured")
        val body = JSONObject().apply {
            put("model", "sonar")
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", query) })
            })
        }
        val url = URL("https://api.perplexity.ai/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        conn.doOutput = true
        conn.outputStream.write(body.toString().toByteArray())
        val resp = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(resp)
        val choices = json.optJSONArray("choices")
        val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "") ?: ""
        return if (content.isNotEmpty()) "## Answer\n$content" else "No results found."
    }

    // MARK: - Scraping

    private fun scrapeTavily(url: String, config: SearchConfig): String {
        if (config.apiKey.isEmpty()) throw IllegalStateException("Tavily API key not configured")
        val body = JSONObject().apply { put("urls", org.json.JSONArray().apply { put(url) }) }
        val endpoint = URL("https://api.tavily.com/extract")
        val conn = endpoint.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        conn.doOutput = true
        conn.outputStream.write(body.toString().toByteArray())
        val resp = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(resp)
        val results = json.optJSONArray("results") ?: return "No content scraped."
        val sb = StringBuilder()
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            sb.appendLine("## ${r.optString("url", url)}")
            sb.appendLine(r.optString("rawContent", ""))
        }
        return sb.toString()
    }

    private fun scrapeJina(url: String, config: SearchConfig): String {
        val encoded = URLEncoder.encode(url, "UTF-8")
        val endpoint = URL("https://r.jina.ai/$encoded")
        val conn = endpoint.openConnection() as HttpURLConnection
        if (config.apiKey.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        return conn.inputStream.bufferedReader().readText()
    }

    // MARK: - Helpers

    private fun formatResults(
        results: org.json.JSONArray,
        titleKey: String,
        urlKey: String,
        textKey: String
    ): String {
        val sb = StringBuilder()
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val title = r.optString(titleKey, "")
            val url = r.optString(urlKey, "")
            val text = r.optString(textKey, "")
            if (title.isNotEmpty() && url.isNotEmpty()) {
                sb.appendLine("${i + 1}. **$title**")
                sb.appendLine("   URL: $url")
                if (text.isNotEmpty()) sb.appendLine("   $text")
                sb.appendLine()
            }
        }
        return sb.toString().ifEmpty { "No results found." }
    }

    private fun parseBingHTML(html: String): String {
        val sb = StringBuilder()
        val regex = Regex("""<li class="b_algo".*?<h2><a href="([^"]+)"[^>]*>(.*?)</a></h2>.*?<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(html).take(10)
        for ((i, match) in matches.withIndex()) {
            val url = match.groupValues[1]
            val title = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            val text = match.groupValues[3].replace(Regex("<[^>]+>"), "").trim()
            if (title.isNotEmpty() && url.isNotEmpty()) {
                sb.appendLine("${i + 1}. **$title**")
                sb.appendLine("   URL: $url")
                if (text.isNotEmpty()) sb.appendLine("   $text")
                sb.appendLine()
            }
        }
        return sb.toString().ifEmpty { "No results found." }
    }
}
