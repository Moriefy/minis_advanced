package com.minis.haprial.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.minis.haprial.search.SearchCommonOptions
import com.minis.haprial.search.SearchProviderConfig
import com.minis.haprial.search.SearchProviderType
import com.minis.haprial.search.SearchResult
import com.minis.haprial.search.SearchService
import com.minis.haprial.search.SearchSettingsStore
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Web Search settings screen.
 * Allows users to:
 * - Add/remove/reorder search providers
 * - Configure API keys and provider-specific options
 * - Set the active provider
 * - Adjust common options (result count)
 * - Test the configured provider
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { SearchSettingsStore.getInstance(context) }
    var providers by remember { mutableStateOf(store.providers) }
    var selectedIndex by remember { mutableStateOf(store.selectedIndex) }
    var commonOptions by remember { mutableStateOf(store.commonOptions) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }

    fun saveAll() {
        store.providers = providers
        store.selectedIndex = selectedIndex
        store.commonOptions = commonOptions
    }

    SettingsScaffold(
        title = "Web Search",
        onBack = {
            saveAll()
            onBack()
        },
        actions = {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add search provider")
            }
        }
    ) {
        // Active provider info
        SettingsSection(title = "Active Provider") {
            if (providers.isNotEmpty()) {
                val activeIdx = selectedIndex.coerceIn(0, providers.lastIndex)
                val active = providers[activeIdx]
                SettingsRow(
                    title = active.type.displayName,
                    subtitle = if (active.apiKey.isNotEmpty()) "API key configured" else "No API key",
                    trailing = {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Active",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Provider list
        SettingsSection(title = "Search Providers") {
            if (providers.isEmpty()) {
                SettingsRow(
                    title = "No providers configured",
                    subtitle = "Tap + to add a search provider"
                )
            } else {
                providers.forEachIndexed { index, provider ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .animateContentSize(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == selectedIndex)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        onClick = {
                            selectedIndex = index
                            store.selectedIndex = index
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = provider.type.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = when {
                                            provider.apiKey.isNotEmpty() -> "API key: ${maskKey(provider.apiKey)}"
                                            provider.type == SearchProviderType.SEARXNG -> "Self-hosted: ${provider.url.ifEmpty { "not configured" }}"
                                            else -> "No API key"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row {
                                    IconButton(onClick = { editIndex = index }) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    if (providers.size > 1) {
                                        IconButton(onClick = {
                                            store.removeProvider(index)
                                            providers = store.providers
                                            selectedIndex = store.selectedIndex
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Common options
        SettingsSection(title = "Search Options") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Result count: ${commonOptions.resultSize}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = commonOptions.resultSize.toFloat(),
                    onValueChange = {
                        commonOptions = commonOptions.copy(resultSize = it.toInt())
                        store.commonOptions = commonOptions
                    },
                    valueRange = 3f..20f,
                    steps = 16
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Test section
        if (providers.isNotEmpty()) {
            val activeProvider = providers[selectedIndex.coerceIn(0, providers.lastIndex)]
            SearchTestSection(
                config = activeProvider,
                commonOptions = commonOptions
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    // Add provider dialog
    if (showAddDialog) {
        AddSearchProviderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { type ->
                showAddDialog = false
                store.addProvider(SearchProviderConfig(type = type))
                providers = store.providers
                selectedIndex = store.selectedIndex
                // Open editor for the new provider
                editIndex = 0
            }
        )
    }

    // Edit provider bottom sheet
    editIndex?.let { idx ->
        if (idx < providers.size) {
            EditSearchProviderDialog(
                config = providers[idx],
                onDismiss = { editIndex = null },
                onSave = { updated ->
                    store.updateProvider(idx, updated)
                    providers = store.providers
                    editIndex = null
                }
            )
        }
    }
}

@Composable
private fun AddSearchProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (SearchProviderType) -> Unit
) {
    var selected by remember { mutableStateOf(SearchProviderType.TAVILY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Search Provider") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SearchProviderType.entries.forEach { type ->
                    Card(
                        onClick = { selected = type },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected == type)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected == type)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = providerHint(type),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSearchProviderDialog(
    config: SearchProviderConfig,
    onDismiss: () -> Unit,
    onSave: (SearchProviderConfig) -> Unit
) {
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var depth by remember(config) { mutableStateOf(config.depth) }
    var url by remember(config) { mutableStateOf(config.url) }
    var engines by remember(config) { mutableStateOf(config.engines) }
    var language by remember(config) { mutableStateOf(config.language) }
    var mode by remember(config) { mutableStateOf(config.mode) }
    var maxTokens by remember(config) { mutableStateOf(config.maxTokens?.toString() ?: "") }
    var summary by remember(config) { mutableStateOf(config.summary) }
    var searchUrl by remember(config) { mutableStateOf(config.searchUrl) }
    var scrapeUrl by remember(config) { mutableStateOf(config.scrapeUrl) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${config.type.displayName}") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // API key (most providers need one)
                if (config.type != SearchProviderType.SEARXNG) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Text(
                                    if (showKey) "Hide" else "Show",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    )
                }

                // Provider-specific options
                when (config.type) {
                    SearchProviderType.TAVILY -> {
                        Text("Search depth", style = MaterialTheme.typography.labelMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf("basic", "advanced").forEachIndexed { i, d ->
                                SegmentedButton(
                                    selected = depth == d,
                                    onClick = { depth = d },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)
                                ) { Text(d) }
                            }
                        }
                    }
                    SearchProviderType.SEARXNG -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("SearXNG Instance URL") },
                            placeholder = { Text("https://searx.example.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = engines,
                            onValueChange = { engines = it },
                            label = { Text("Engines (optional)") },
                            placeholder = { Text("google,bing,duckduckgo") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = language,
                            onValueChange = { language = it },
                            label = { Text("Language (optional)") },
                            placeholder = { Text("en, zh, ja...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    SearchProviderType.JINA -> {
                        OutlinedTextField(
                            value = searchUrl,
                            onValueChange = { searchUrl = it },
                            label = { Text("Search URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = scrapeUrl,
                            onValueChange = { scrapeUrl = it },
                            label = { Text("Scrape URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    SearchProviderType.DOUBAO -> {
                        Text("Search scope", style = MaterialTheme.typography.labelMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf("global", "custom").forEachIndexed { i, m ->
                                SegmentedButton(
                                    selected = mode == m,
                                    onClick = { mode = m },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)
                                ) { Text(m) }
                            }
                        }
                    }
                    SearchProviderType.PERPLEXITY -> {
                        OutlinedTextField(
                            value = maxTokens,
                            onValueChange = { maxTokens = it },
                            label = { Text("Max Tokens (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    SearchProviderType.BOCHA -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Include summary", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = summary,
                                onCheckedChange = { summary = it }
                            )
                        }
                    }
                    SearchProviderType.LINKUP -> {
                        Text("Search depth", style = MaterialTheme.typography.labelMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf("standard", "deep").forEachIndexed { i, d ->
                                SegmentedButton(
                                    selected = depth == d,
                                    onClick = { depth = d },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)
                                ) { Text(d) }
                            }
                        }
                    }
                    else -> {
                        // Exa, Serper, Brave, Zhipu, Bing, Metaso — only need API key
                    }
                }

                // Help link
                Text(
                    text = providerHint(config.type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    config.copy(
                        apiKey = apiKey,
                        depth = depth,
                        url = url,
                        engines = engines,
                        language = language,
                        mode = mode,
                        maxTokens = maxTokens.toIntOrNull(),
                        summary = summary,
                        searchUrl = searchUrl,
                        scrapeUrl = scrapeUrl
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SearchTestSection(
    config: SearchProviderConfig,
    commonOptions: SearchCommonOptions
) {
    var query by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    SettingsSection(title = "Test Search") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter test query...") },
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (query.isNotBlank() && !testing) {
                            testing = true
                            result = null
                            error = null
                            scope.launch {
                                try {
                                    val service = SearchService.getService(config)
                                    val params = JSONObject().apply { put("query", query) }
                                    result = service.search(params, commonOptions, config)
                                } catch (e: Exception) {
                                    error = e.message
                                } finally {
                                    testing = false
                                }
                            }
                        }
                    },
                    enabled = query.isNotBlank() && !testing
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Test")
                    }
                }
            }

            result?.let { res ->
                res.answer?.let { answer ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = answer.take(500),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                res.items.take(5).forEachIndexed { index, item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${index + 1}. ${item.title}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = item.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = item.text.take(150),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

private fun maskKey(key: String): String {
    if (key.length <= 8) return "****"
    return key.take(4) + "****" + key.takeLast(4)
}

private fun providerHint(type: SearchProviderType): String = when (type) {
    SearchProviderType.TAVILY -> "tavily.com — AI-optimized search with scraping"
    SearchProviderType.BRAVE -> "search.brave.com — Independent search API"
    SearchProviderType.METASO -> "metaso.cn — High-quality Chinese AI search"
    SearchProviderType.EXA -> "exa.ai — Neural search engine"
    SearchProviderType.SERPER -> "serper.dev — Google search results API"
    SearchProviderType.JINA -> "jina.ai — Reader/search API with scraping"
    SearchProviderType.ZHIPU -> "zhipu.ai — Chinese AI platform search"
    SearchProviderType.BOCHA -> "bochaai.com — Chinese web search"
    SearchProviderType.DOUBAO -> "doubao.com — ByteDance search"
    SearchProviderType.PERPLEXITY -> "perplexity.ai — AI search with citations"
    SearchProviderType.LINKUP -> "linkup.so — Structured search API"
    SearchProviderType.SEARXNG -> "Self-hosted — No API key needed"
    SearchProviderType.BING -> "bing.microsoft.com — Bing Web Search API"
}
