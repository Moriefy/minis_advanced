package com.openminis.app.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openminis.app.search.SearchServiceManager

/**
 * Search toggle button for the chat input bar — mirrors iOS SearchToggleButton.
 * Sits in the bottom row between "/" and the edit-exit pill.
 */
@Composable
fun SearchToggleButton(
    searchManager: SearchServiceManager,
    modifier: Modifier = Modifier
) {
    var showSettings by remember { mutableStateOf(false) }

    val isActive = searchManager.searchEnabled
    val bgColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "searchBg"
    )
    val fgColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "searchFg"
    )

    InputCircleButton(
        onClick = { showSettings = true },
        modifier = modifier,
        backgroundColor = bgColor,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = "Search",
            tint = fgColor,
            modifier = Modifier.size(20.dp),
        )
    }

    if (showSettings) {
        SearchSettingsDialog(
            searchManager = searchManager,
            onDismiss = { showSettings = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSettingsDialog(
    searchManager: SearchServiceManager,
    onDismiss: () -> Unit
) {
    var enabled by remember { mutableStateOf(searchManager.searchEnabled) }
    var selectedIndex by remember { mutableStateOf(searchManager.selectedIndex) }
    val configs = remember { searchManager.configs }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Search Settings",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Web Search", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Use search API instead of browser automation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                if (enabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Search Provider",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    configs.forEachIndexed { index, config ->
                        val isSelected = selectedIndex == index
                        val bgColor by animateColorAsState(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            label = "providerBg"
                        )
                        Surface(
                            onClick = { selectedIndex = index },
                            shape = RoundedCornerShape(12.dp),
                            color = bgColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        SearchServiceManager.DISPLAY_NAMES[config.type] ?: config.type,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (config.apiKey.isNotEmpty()) {
                                        Text(
                                            "API key configured",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Info text
                Text(
                    "When enabled, the agent will use search APIs for web queries. " +
                        "Browser automation remains available for interactive browsing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        searchManager.searchEnabled = enabled
                        searchManager.selectedIndex = selectedIndex
                        onDismiss()
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
