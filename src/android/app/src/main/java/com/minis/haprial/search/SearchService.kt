package com.minis.haprial.search

import org.json.JSONObject

/**
 * Unified interface for all search providers.
 * Each implementation is a singleton object.
 */
interface SearchService {

    /** Provider type this service handles. */
    val providerType: SearchProviderType

    /**
     * Returns the tool parameter schema for the agent.
     * The schema tells the LLM what parameters this search accepts (e.g. "query", "topic").
     */
    fun toolParameters(config: SearchProviderConfig): JSONObject

    /**
     * Whether this provider supports scraping individual URLs.
     */
    fun supportsScraping(config: SearchProviderConfig): Boolean = false

    /**
     * Execute a search query.
     * @param params The agent-provided parameters (e.g. {"query": "..."})
     * @param commonOptions Shared options like result count
     * @param config Provider-specific configuration (API keys, etc.)
     */
    suspend fun search(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): SearchResult

    /**
     * Scrape a URL for its content (if supported).
     */
    suspend fun scrape(
        params: JSONObject,
        commonOptions: SearchCommonOptions,
        config: SearchProviderConfig
    ): ScrapedResult {
        throw UnsupportedOperationException("Scraping is not supported for ${providerType.displayName}")
    }

    companion object {
        /**
         * Resolve a SearchService for a given provider config.
         */
        fun getService(config: SearchProviderConfig): SearchService = when (config.type) {
            SearchProviderType.TAVILY -> TavilySearchService
            SearchProviderType.BRAVE -> BraveSearchService
            SearchProviderType.METASO -> MetasoSearchService
            SearchProviderType.EXA -> ExaSearchService
            SearchProviderType.SERPER -> SerperSearchService
            SearchProviderType.JINA -> JinaSearchService
            SearchProviderType.ZHIPU -> ZhipuSearchService
            SearchProviderType.BOCHA -> BochaSearchService
            SearchProviderType.DOUBAO -> DoubaoSearchService
            SearchProviderType.PERPLEXITY -> PerplexitySearchService
            SearchProviderType.LINKUP -> LinkUpSearchService
            SearchProviderType.SEARXNG -> SearXNGSearchService
            SearchProviderType.BING -> BingSearchService
        }
    }
}
