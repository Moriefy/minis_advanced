package com.minis.haprial.ui.chat

// [T-android-split-chat] Small UI-state toggle methods extracted from
// ChatViewModel as extension functions (verbatim): tool-detail sheet,
// browser sheet, memory sheet, attachment list. The 4 backing state fields
// were flipped private->internal. No logic change.

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.lazy.LazyListState
import com.minis.haprial.agent.Level
import com.minis.haprial.agent.ToolLoopDetector
import com.minis.haprial.browser.BrowserActionInput
import com.minis.haprial.browser.BrowserTabPool
import com.minis.haprial.data.db.MessageEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import com.minis.haprial.data.BPETokenizer
import com.minis.haprial.data.ContextOffload
import com.minis.haprial.data.ContextPolicy
import com.minis.haprial.logging.AppLogger
import com.minis.haprial.data.FileMentionIndex
import com.minis.haprial.data.db.CompactMarkerEntity
import com.minis.haprial.data.model.AgentContentPart
import com.minis.haprial.data.model.AgentToolDefinition
import com.minis.haprial.data.model.LLMMessage
import com.minis.haprial.data.model.LLMModel
import com.minis.haprial.data.model.LLMStreamChunk
import com.minis.haprial.data.model.LLMUsage
import com.minis.haprial.data.model.ModelGroup
import com.minis.haprial.data.model.ThinkingLevel
import com.minis.haprial.R
import com.minis.haprial.data.repository.ChatRepository
import com.minis.haprial.data.repository.MemoryRepository
import com.minis.haprial.data.repository.ProviderRepository
import com.minis.haprial.provider.ImageBudget
import com.minis.haprial.provider.LLMProvider
import com.minis.haprial.provider.ProviderFactory
import com.minis.haprial.sandbox.ExecutionCoordinator
import com.minis.haprial.terminal.MinisOpenUrlBroker
import com.minis.haprial.terminal.MinisUrlMarker
import com.minis.haprial.tools.AgentTools
import com.minis.haprial.tools.FileEditTool
import com.minis.haprial.tools.FileReadTool
import com.minis.haprial.tools.FileWriteTool
import com.minis.haprial.tools.MemoryTools
import com.minis.haprial.tools.ReadImageTool
import com.minis.haprial.tools.ToolExecutionResult
import com.minis.haprial.offload.OffloadPermissionManager
import com.minis.haprial.service.SessionActivityTracker
import com.minis.haprial.service.SessionConcurrencyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal fun ChatViewModel.openToolDetail(toolBlockId: String) {
    _selectedToolDetailId.value = toolBlockId
}

internal fun ChatViewModel.closeToolDetail() {
    _selectedToolDetailId.value = null
}

internal fun ChatViewModel.toggleBrowserSheet() {
    val opening = !_showBrowserSheet.value
    if (opening) browserTabPool.ensureTabForUI()
    _showBrowserSheet.value = opening
}

internal fun ChatViewModel.dismissBrowserSheet() {
    _showBrowserSheet.value = false
}

/**
 * Open the session browser sheet, focused on the tab whose URL matches
 * [url]. If no pool tab currently has that URL, a new tab is created and
 * loaded. Used by the tool-call preview's globe button so the agent's
 * existing browser_use page is reused when available instead of spawning
 * a duplicate tab.
 */
internal fun ChatViewModel.openBrowserSheetForUrl(url: String) {
    if (url.isBlank()) {
        browserTabPool.ensureTabForUI()
    } else {
        browserTabPool.selectOrCreateTabForURL(url)
    }
    _showBrowserSheet.value = true
}

internal fun ChatViewModel.toggleMemorySheet() {
    _showMemorySheet.value = !_showMemorySheet.value
}

internal fun ChatViewModel.dismissMemorySheet() {
    _showMemorySheet.value = false
}

internal fun ChatViewModel.addAttachment(attachment: InputAttachment) {
    _attachments.value = _attachments.value + attachment
}

internal fun ChatViewModel.removeAttachment(id: String) {
    _attachments.value = _attachments.value.filter { it.id != id }
}

internal fun ChatViewModel.clearAttachments() {
    _attachments.value = emptyList()
}
