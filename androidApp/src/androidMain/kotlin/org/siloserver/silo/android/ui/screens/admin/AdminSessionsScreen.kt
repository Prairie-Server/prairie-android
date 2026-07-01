package org.siloserver.silo.android.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.android.ui.components.SiloTopBar
import org.siloserver.silo.android.ui.components.EmptyStateView
import org.siloserver.silo.android.ui.components.ErrorView
import org.siloserver.silo.android.ui.components.LoadingIndicator
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.admin.AdminSession
import org.siloserver.silo.model.admin.SessionControlAction
import org.siloserver.silo.model.admin.SessionControlRequest
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.errorMessage
import org.siloserver.silo.repository.AdminRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

// ---------------------------------------------------------------------------
// ViewModel (co-located, LibrariesViewModel idiom; registered in AndroidModule)
// ---------------------------------------------------------------------------

data class AdminSessionsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sessions: List<AdminSession> = emptyList(),
    val error: String? = null,
)

/**
 * Owns the live admin sessions list and the per-session playback-control
 * actions (pause/resume/stop/terminate/message). Generation-gated fetches so a
 * pull-to-refresh that overlaps an in-flight load can't clobber newer data;
 * one-shot control results surface via [toasts] then trigger a refresh.
 */
class AdminSessionsViewModel(
    private val repository: AdminRepository,
) : ViewModel() {

    private var loadGeneration = 0
    private val _uiState = MutableStateFlow(AdminSessionsUiState())
    val uiState: StateFlow<AdminSessionsUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    init { load() }

    fun load() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetch(generation)
        }
    }

    fun refresh() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            fetch(generation)
            if (generation == loadGeneration) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun control(
        sessionId: String,
        action: SessionControlAction,
        request: SessionControlRequest = SessionControlRequest(),
    ) {
        viewModelScope.launch {
            when (val result = repository.sessionControl(sessionId, action, request)) {
                is ApiResult.Success -> {
                    _toasts.emit(controlSuccessMessage(action))
                    refresh()
                }
                is ApiResult.Error, is ApiResult.NetworkError ->
                    _toasts.emit(result.errorMessage("Failed to ${action.wire} session"))
            }
        }
    }

    private suspend fun fetch(generation: Int) {
        val result = repository.getSessions()
        if (generation != loadGeneration) return
        when (result) {
            is ApiResult.Success -> _uiState.update {
                it.copy(isLoading = false, sessions = result.data, error = null)
            }
            is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                it.copy(isLoading = false, error = result.errorMessage("Failed to load sessions"))
            }
        }
    }

    private fun controlSuccessMessage(action: SessionControlAction): String = when (action) {
        SessionControlAction.Pause -> "Session paused"
        SessionControlAction.Resume -> "Session resumed"
        SessionControlAction.Stop -> "Session stopped"
        SessionControlAction.Terminate -> "Session terminated"
        SessionControlAction.Message -> "Message sent"
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSessionsScreen(
    onBackClick: () -> Unit,
    viewModel: AdminSessionsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var messageTarget by remember { mutableStateOf<AdminSession?>(null) }
    var terminateTarget by remember { mutableStateOf<AdminSession?>(null) }

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = { SiloTopBar(title = "Sessions", onBackClick = onBackClick) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading && state.sessions.isEmpty() ->
                LoadingIndicator(Modifier.padding(padding))

            state.error != null && state.sessions.isEmpty() ->
                ErrorView(state.error!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))

            state.sessions.isEmpty() -> EmptyStateView(
                title = "No active sessions",
                subtitle = "Streams in progress will appear here.",
                icon = Icons.Outlined.Cast,
                modifier = Modifier.padding(padding),
            )

            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.sessions, key = { it.sessionId }) { session ->
                        SessionRow(
                            session = session,
                            onPause = {
                                viewModel.control(session.sessionId, SessionControlAction.Pause)
                            },
                            onResume = {
                                viewModel.control(session.sessionId, SessionControlAction.Resume)
                            },
                            onStop = {
                                viewModel.control(session.sessionId, SessionControlAction.Stop)
                            },
                            onMessage = { messageTarget = session },
                            onTerminate = { terminateTarget = session },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    messageTarget?.let { session ->
        MessageDialog(
            session = session,
            onDismiss = { messageTarget = null },
            onSend = { title, body ->
                viewModel.control(
                    session.sessionId,
                    SessionControlAction.Message,
                    SessionControlRequest(
                        title = title.ifBlank { null },
                        message = body,
                    ),
                )
                messageTarget = null
            },
        )
    }

    terminateTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { terminateTarget = null },
            title = { Text("Terminate session?") },
            text = {
                Text("This forcibly ends ${session.username}'s stream of ${session.mediaTitle}.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.control(session.sessionId, SessionControlAction.Terminate)
                    terminateTarget = null
                }) {
                    Text("Terminate", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { terminateTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(
    session: AdminSession,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onMessage: () -> Unit,
    onTerminate: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ThumbhashImage(
                url = session.posterUrl.ifBlank { null },
                thumbhash = null,
                contentDescription = session.mediaTitle,
                modifier = Modifier
                    .size(width = 54.dp, height = 80.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.size(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = session.mediaTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                session.seasonEpisode()?.let { se ->
                    val episodeSuffix = session.episodeName.takeIf { it.isNotBlank() }
                    Text(
                        text = if (episodeSuffix != null) "$se · $episodeSuffix" else se,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = session.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = listOfNotNull(
                        if (session.isPaused) "Paused" else "Playing",
                        session.progressLabel(),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (session.isPaused) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = session.summaryLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (session.hasPlaybackControl) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Session actions")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (session.isPaused) {
                            DropdownMenuItem(
                                text = { Text("Resume") },
                                onClick = { menuExpanded = false; onResume() },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Pause") },
                                onClick = { menuExpanded = false; onPause() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Stop") },
                            onClick = { menuExpanded = false; onStop() },
                        )
                        DropdownMenuItem(
                            text = { Text("Send message") },
                            onClick = { menuExpanded = false; onMessage() },
                        )
                        DropdownMenuItem(
                            text = { Text("Terminate", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onTerminate() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageDialog(
    session: AdminSession,
    onDismiss: () -> Unit,
    onSend: (title: String, body: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message ${session.username}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(title, body) },
                enabled = body.isNotBlank(),
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
