package com.continuum.app.tv.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.admin.AdminSession
import com.continuum.app.model.admin.SessionControlAction
import com.continuum.app.model.admin.SessionControlRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.errorMessage
import com.continuum.app.repository.AdminRepository
import com.continuum.app.tv.ui.components.TvDialogOption
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.components.TvOptionDialog
import com.continuum.app.tv.ui.components.TvTextInputDialog
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

// ---------------------------------------------------------------------------
// ViewModel (co-located, mirrors the phone's AdminSessionsViewModel)
// ---------------------------------------------------------------------------

data class TvAdminSessionsUiState(
    val isLoading: Boolean = true,
    val sessions: List<AdminSession> = emptyList(),
    val error: String? = null,
    /** One-shot user-facing message after a control action. */
    val message: String? = null,
)

/**
 * Owns the live admin sessions list and per-session playback-control actions
 * (pause/resume/stop/terminate). Mirrors the phone's co-located
 * `AdminSessionsViewModel`: generation-gated fetches so a refresh that overlaps
 * an in-flight load can't clobber newer data; control results surface via a
 * one-shot [TvAdminSessionsUiState.message] then trigger a refresh.
 *
 * Reuses the same shared [AdminRepository] the phone uses. Registered in
 * AndroidTvModule.
 */
class TvAdminSessionsViewModel(
    private val repository: AdminRepository,
) : ViewModel() {

    private var loadGeneration = 0
    private val _uiState = MutableStateFlow(TvAdminSessionsUiState())
    val uiState: StateFlow<TvAdminSessionsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetch(generation)
        }
    }

    fun refresh() = load()

    fun control(
        sessionId: String,
        action: SessionControlAction,
        request: SessionControlRequest = SessionControlRequest(),
    ) {
        viewModelScope.launch {
            when (val result = repository.sessionControl(sessionId, action, request)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(message = controlSuccessMessage(action)) }
                    load()
                }
                is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                    it.copy(message = result.errorMessage("Failed to ${action.wire} session"))
                }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

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

/**
 * TV admin sessions management — live "now playing" list with per-session
 * controls. Mirrors the phone's `AdminSessionsScreen` (pause/resume/stop/
 * terminate via a per-row menu); TV adapts the per-row menu to a focusable
 * [TvOptionDialog]. The "Send message" action is touch-keyboard heavy on the
 * phone and is deferred on TV.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAdminSessionsScreen(
    onBack: () -> Unit,
    viewModel: TvAdminSessionsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var actionsTarget by remember { mutableStateOf<AdminSession?>(null) }
    var terminateTarget by remember { mutableStateOf<AdminSession?>(null) }
    var messageTarget by remember { mutableStateOf<AdminSession?>(null) }

    BackHandler(enabled = true) { onBack() }

    // Consume one-shot control messages so the flag doesn't stick (no snackbar
    // on TV — the list refresh after a successful action is the feedback).
    androidx.compose.runtime.LaunchedEffect(state.message) {
        if (state.message != null) viewModel.consumeMessage()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TvAdminScreenHeader(eyebrow = "ADMIN", title = "Sessions", subtitle = state.message)

        when {
            state.isLoading && state.sessions.isEmpty() -> TvLoadingScreen()

            state.error != null && state.sessions.isEmpty() -> TvErrorScreen(
                message = state.error!!,
                onRetry = viewModel::load,
            )

            state.sessions.isEmpty() -> TvErrorScreen(
                message = "No active sessions.",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.safeArea, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.sessions, key = { it.sessionId }) { session ->
                    SessionRow(
                        session = session,
                        onClick = {
                            if (session.hasPlaybackControl) actionsTarget = session
                        },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    actionsTarget?.let { session ->
        val options = buildList {
            if (session.isPaused) {
                add(
                    TvDialogOption(
                        key = "resume",
                        title = "Resume",
                        onClick = {
                            actionsTarget = null
                            viewModel.control(session.sessionId, SessionControlAction.Resume)
                        },
                    ),
                )
            } else {
                add(
                    TvDialogOption(
                        key = "pause",
                        title = "Pause",
                        onClick = {
                            actionsTarget = null
                            viewModel.control(session.sessionId, SessionControlAction.Pause)
                        },
                    ),
                )
            }
            add(
                TvDialogOption(
                    key = "stop",
                    title = "Stop",
                    onClick = {
                        actionsTarget = null
                        viewModel.control(session.sessionId, SessionControlAction.Stop)
                    },
                ),
            )
            add(
                TvDialogOption(
                    key = "terminate",
                    title = "Terminate",
                    subtitle = "Forcibly end this stream",
                    onClick = {
                        actionsTarget = null
                        terminateTarget = session
                    },
                ),
            )
            add(
                TvDialogOption(
                    key = "message",
                    title = "Send message",
                    subtitle = "Show a message on this device",
                    onClick = {
                        actionsTarget = null
                        messageTarget = session
                    },
                ),
            )
            add(
                TvDialogOption(
                    key = "cancel",
                    title = "Cancel",
                    onClick = { actionsTarget = null },
                ),
            )
        }
        TvOptionDialog(
            title = "${session.username} • ${session.mediaTitle}",
            options = options,
            onDismiss = { actionsTarget = null },
        )
    }

    terminateTarget?.let { session ->
        TvOptionDialog(
            title = "Terminate session?",
            options = listOf(
                TvDialogOption(
                    key = "confirm",
                    title = "Terminate",
                    subtitle = "Ends ${session.username}'s stream",
                    onClick = {
                        viewModel.control(session.sessionId, SessionControlAction.Terminate)
                        terminateTarget = null
                    },
                ),
                TvDialogOption(
                    key = "cancel",
                    title = "Keep playing",
                    onClick = { terminateTarget = null },
                ),
            ),
            onDismiss = { terminateTarget = null },
        )
    }

    messageTarget?.let { session ->
        TvTextInputDialog(
            title = "Send message",
            label = "Message to ${session.username}",
            confirmLabel = "Send",
            onConfirm = { text ->
                viewModel.control(
                    session.sessionId,
                    SessionControlAction.Message,
                    SessionControlRequest(message = text),
                )
                messageTarget = null
            },
            onDismiss = { messageTarget = null },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SessionRow(session: AdminSession, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = RoundedCornerShape(16.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 1100.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ThumbhashImage(
                url = session.posterUrl.ifBlank { null },
                thumbhash = null,
                contentDescription = session.mediaTitle,
                modifier = Modifier
                    .size(width = 66.dp, height = 98.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = session.mediaTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                session.tvSeasonEpisode()?.let { se ->
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
                        session.tvProgressLabel(),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (session.isPaused) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = session.tvSummaryLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// TV-local formatters — mirror the phone's AdminSessionFormatters (which live
// in the androidApp package and aren't reachable here). Kept intentionally
// minimal: the high-value labels for a 10-foot list.
// ---------------------------------------------------------------------------

private fun AdminSession.tvSeasonEpisode(): String? =
    if (seasonNumber != null && episodeNumber != null) "S${seasonNumber}E$episodeNumber" else null

private fun playMethodLabel(playMethod: String): String = when (playMethod.lowercase().replace("_", "").replace(" ", "")) {
    "directplay" -> "Direct Play"
    "directstream" -> "Direct Stream"
    "transcode" -> "Transcode"
    else -> playMethod.ifBlank { "Playing" }
}

private fun AdminSession.tvSummaryLine(): String {
    val resolution = targetResolution.ifBlank { sourceVideoResolution }
    val bitrateKbps = (targetBitrateKbps ?: streamBitrateKbps ?: sourceBitrateKbps)?.takeIf { it > 0 }
    val bitrate = bitrateKbps?.let {
        if (it >= 1000) "%.1f Mbps".format(it / 1000.0) else "$it Kbps"
    }
    return listOfNotNull(
        playMethodLabel(playMethod),
        bitrate,
        resolution.takeIf { it.isNotBlank() },
        nodeDisplayName.takeIf { it.isNotBlank() },
    ).joinToString(" • ")
}

private fun AdminSession.tvProgressLabel(): String {
    val pos = formatClock(positionSeconds)
    val dur = (fileDuration ?: 0).toDouble()
    return if (dur <= 0.0) pos else "$pos / ${formatClock(dur)}"
}

private fun formatClock(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0) return "0:00"
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
