package com.minis.haprial.tools

import android.content.Context
import com.minis.haprial.data.model.AgentToolDefinition
import com.minis.haprial.data.model.AgentToolParam
import com.minis.haprial.search.SearchHttp
import com.minis.haprial.search.SearchResult
import com.minis.haprial.search.SearchService
import com.minis.haprial.search.SearchSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Agent tool for web search, powered by configurable search providers.
 * Ported from RikkaHub's search module with full customisation support.
 */
object SearchWebTool {

    const val NAME_SEARCH = "search_web"
    const val NAME_SCRAPE = "scrape_web"

    /**
     * Returns the tool definition for the agent, using the currently
     * active search provider's parameter schema.
     */
    fun searchDefinition(context: Context): AgentToolDefinition {
        val store = SearchSettingsStore.getInstance(context)
        val config = store.getActiveProvider()
        val providerName = config.type.displayName

        return AgentToolDefinition(
            name = NAME_SEARCH,
            description = "Search the web for up-to-date or specific information using $providerName. " +
                "Use this when the user asks for the latest news, current facts, or needs verification. " +
                "Generate focused keywords and run multiple searches if needed. " +
                "Today is ${LocalDate.now()}.",
            parameters = buildSearchParameters(config),
            required = listOf("tool_title", "query"),
            propertyOrdering = listOf("tool_title", "query", "topic")
        )
    }

    /**
     * Returns the scrape tool definition if the active provider supports it.
     */
    fun scrapeDefinition(context: Context): AgentToolDefinition? {
        val store = SearchSettingsStore.getInstance(context)
        val config = store.getActiveProvider()
        val service = SearchService.getService(config)
        if (!service.supportsScraping(config)) return null

        return AgentToolDefinition(
            name = NAME_SCRAPE,
            description = "Scrape a URL for detailed page content. " +
                "Use this when the user requests content from a specific page or when search snippets are insufficient.",
            parameters = mapOf(
                "tool_title" to AgentToolParam("string", "A concise summary of what this tool call does"),
                "url" to AgentToolParam("string", "The URL to scrape")
            ),
            required = listOf("tool_title", "url"),
            propertyOrdering = listOf("tool_title", "url")
        )
    }

    /**
     * Execute a web search.
     */
    suspend fun executeSearch(argsJson: String, context: Context): ToolExecutionResult {
        return try {
            val args = JSONObject(argsJson)
            val query = args.optString("query", "").ifEmpty {
                return ToolExecutionResult("Error: query parameter is required", false)
            }
            val toolTitle = args.optString("tool_title", "Searching: $query")

            val store = SearchSettingsStore.getInstance(context)
            val config = store.getActiveProvider()
            val commonOptions = store.commonOptions
            val service = SearchService.getService(config)

            if (config.apiKey.isEmpty() && config.type != com.minis.haprial.search.SearchProviderType.SEARXNG) {
                return ToolExecutionResult(
                    "Error: No API key configured for ${config.type.displayName}. " +
                    "Go to Settings → Web Search to configure your search provider.",
                    false,
                    toolTitle = toolTitle
                )
            }

            val result = service.search(args, commonOptions, config)
            val output = formatSearchResult(result)

            ToolExecutionResult(
                output = output,
                success = true,
                toolTitle = toolTitle
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                output = "Search error: ${e.message}",
                success = false,
                toolTitle = "Search failed"
            )
        }
    }

    /**
     * Execute a URL scrape.
     */
    suspend fun executeScrape(argsJson: String, context: Context): ToolExecutionResult {
        return try {
            val args = JSONObject(argsJson)
            val url = args.optString("url", "").ifEmpty {
                return ToolExecutionResult("Error: url parameter is required", false)
            }
            val toolTitle = args.optString("tool_title", "Scraping: $url")

            val store = SearchSettingsStore.getInstance(context)
            val config = store.getActiveProvider()
            val service = SearchService.getService(config)

            val result = service.scrape(args, store.commonOptions, config)
            val output = JSONObject().apply {
                put("url", result.url)
                put("content", result.content)
                if (result.title != null) put("title", result.title)
            }.toString(2)

            ToolExecutionResult(
                output = output,
                success = true,
                toolTitle = toolTitle
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                output = "Scrape error: ${e.message}",
                success = false,
                toolTitle = "Scrape failed"
            )
        }
    }

    private fun buildSearchParameters(config: com.minis.haprial.search.SearchProviderConfig): Map<String, AgentToolParam> {
        val params = mutableMapOf<String, AgentToolParam>(
            "tool_title" to AgentToolParam(
                "string",
                "A concise 5-10 word summary of what this tool call does, shown to the user"
            ),
            "query" to AgentToolParam(
                "string",
                "search keyword"
            )
        )

        // Some providers support additional parameters
        when (config.type) {
            com.minis.haprial.search.SearchProviderType.TAVILY -> {
                params["topic"] = AgentToolParam(
                    "string",
                    "search topic (one of general, news, finance)",
                    enumValues = listOf("general", "news", "finance")
                )
            }
            com.minis.haprial.search.SearchProviderType.DOUBAO -> {
                params["scope"] = AgentToolParam(
                    "string",
                    "search scope (global for worldwide, custom for configured)",
                    enumValues = listOf("global", "custom")
                )
            }
            com.minis.haprial.search.SearchProviderType.SEARXNG -> {
                params["categories"] = AgentToolParam(
                    "string",
                    "search categories (e.g. general, images, news, science)"
                )
            }
            else -> { /* standard query-only providers */ }
        }

        return params
    }

    private fun formatSearchResult(result: SearchResult): String {
        return result.toJson().toString(2)
    }
}
