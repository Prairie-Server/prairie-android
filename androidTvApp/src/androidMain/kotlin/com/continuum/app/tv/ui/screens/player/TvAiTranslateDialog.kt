package com.continuum.app.tv.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.model.playback.PlayerSubtitleInfo
import com.continuum.app.tv.ui.theme.DarkBackground

/** Which capture mode the dialog is in — availability comes from AiStatus. */
private enum class TvAiTranslateMode(val label: String) {
    Subtitles("From subtitles"),
    Audio("From audio"),
}

/**
 * D-pad AI translate/transcribe dialog (TvOptionDialog panel idiom). Pure
 * pickers — no text input. Mode row appears only when both modes are
 * available; otherwise the single available mode is fixed. Submitting flips
 * the dialog body to an in-dialog progress view (percent + progress_message
 * + Cancel). Completion: the VM refreshes the track list, auto-selects
 * `result_subtitle_id`, and bumps `completedNonce` — observed here to dismiss.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAiTranslateDialog(
    aiState: AiTranslateUiState,
    /** Text-based session subtitle tracks (PGS/DVD filtered out by the caller). */
    subtitleSources: List<PlayerSubtitleInfo>,
    /** ExoPlayer audio track entries (ordinal index = server audio track index). */
    audioSources: List<PlayerTrackEntry>,
    defaultTargetLanguage: String,
    onSubmit: (kind: String, sourceIndex: Int, sourceLanguage: String?, targetLanguage: String) -> Unit,
    onCancelJob: () -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
) {
    val subtitlesAvailable = aiState.status.enabled && subtitleSources.isNotEmpty()
    val audioAvailable = aiState.status.transcribeEnabled && audioSources.isNotEmpty()

    var mode by remember(subtitlesAvailable, audioAvailable) {
        mutableStateOf(if (subtitlesAvailable) TvAiTranslateMode.Subtitles else TvAiTranslateMode.Audio)
    }
    var subtitleSourcePos by remember { mutableIntStateOf(0) }
    var audioSourcePos by remember {
        mutableIntStateOf(audioSources.indexOfFirst { it.isSelected }.coerceAtLeast(0))
    }
    var targetPos by remember {
        mutableIntStateOf(
            TvSubtitleLanguageOptions.indexOf(defaultTargetLanguage.take(2).lowercase())
                .takeIf { it >= 0 } ?: 0,
        )
    }
    val firstRowFocus = remember { FocusRequester() }
    val initialNonce = remember { aiState.completedNonce }

    LaunchedEffect(Unit) { runCatching { firstRowFocus.requestFocus() } }
    LaunchedEffect(aiState.completedNonce) {
        if (aiState.completedNonce != initialNonce) onDismiss()
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 72.dp, top = 100.dp, end = 72.dp, bottom = 84.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelShape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .background(color = DarkBackground.copy(alpha = 0.68f), shape = panelShape)
                    .border(1.2.dp, Color.White.copy(alpha = 0.20f), panelShape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "TRANSLATE WITH AI",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 16.sp,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White.copy(alpha = 0.58f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                when (val phase = aiState.phase) {
                    is AiJobPhase.Running -> {
                        TvAiJobProgress(
                            progress = phase.progress,
                            message = phase.message,
                            onCancel = onCancelJob,
                            cancelFocus = firstRowFocus,
                        )
                    }
                    AiJobPhase.Submitting -> {
                        Text(
                            text = "Submitting…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                    is AiJobPhase.Failed, AiJobPhase.Idle -> {
                        if (!subtitlesAvailable && !audioAvailable) {
                            // Neither mode usable: AI configured but no
                            // translatable text tracks and transcription
                            // unavailable — explanatory empty state.
                            Text(
                                text = "No translatable subtitle tracks, and audio " +
                                    "transcription is not available on this server.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.66f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                        } else {
                            if (subtitlesAvailable && audioAvailable) {
                                TvDialogCyclerRow(
                                    title = "Mode",
                                    value = mode.label,
                                    onPrevious = {
                                        mode = if (mode == TvAiTranslateMode.Subtitles) {
                                            TvAiTranslateMode.Audio
                                        } else {
                                            TvAiTranslateMode.Subtitles
                                        }
                                    },
                                    onNext = {
                                        mode = if (mode == TvAiTranslateMode.Subtitles) {
                                            TvAiTranslateMode.Audio
                                        } else {
                                            TvAiTranslateMode.Subtitles
                                        }
                                    },
                                    modifier = Modifier.focusRequester(firstRowFocus),
                                )
                            }

                            val sourceFocusModifier =
                                if (!(subtitlesAvailable && audioAvailable)) {
                                    Modifier.focusRequester(firstRowFocus)
                                } else {
                                    Modifier
                                }
                            if (mode == TvAiTranslateMode.Subtitles) {
                                val pos = subtitleSourcePos.coerceIn(0, subtitleSources.lastIndex)
                                TvDialogCyclerRow(
                                    title = "Source subtitle",
                                    value = subtitleSourceLabel(subtitleSources[pos]),
                                    onPrevious = {
                                        subtitleSourcePos =
                                            (pos - 1 + subtitleSources.size) % subtitleSources.size
                                    },
                                    onNext = {
                                        subtitleSourcePos = (pos + 1) % subtitleSources.size
                                    },
                                    modifier = sourceFocusModifier,
                                )
                            } else {
                                val pos = audioSourcePos.coerceIn(0, audioSources.lastIndex)
                                TvDialogCyclerRow(
                                    title = "Source audio",
                                    value = audioSources[pos].displayLabel
                                        .ifBlank { "Track ${audioSources[pos].index + 1}" },
                                    onPrevious = {
                                        audioSourcePos =
                                            (pos - 1 + audioSources.size) % audioSources.size
                                    },
                                    onNext = { audioSourcePos = (pos + 1) % audioSources.size },
                                    modifier = sourceFocusModifier,
                                )
                            }

                            TvDialogCyclerRow(
                                title = "Target language",
                                value = tvLanguageDisplayName(TvSubtitleLanguageOptions[targetPos]),
                                onPrevious = {
                                    targetPos = (targetPos - 1 + TvSubtitleLanguageOptions.size) %
                                        TvSubtitleLanguageOptions.size
                                },
                                onNext = {
                                    targetPos = (targetPos + 1) % TvSubtitleLanguageOptions.size
                                },
                            )

                            // Quota applies to transcribe kinds only; admins are
                            // exempt (limited=false → no line).
                            val quotaExhausted = mode == TvAiTranslateMode.Audio &&
                                aiState.quota?.limited == true &&
                                (aiState.quota.remaining) <= 0
                            if (mode == TvAiTranslateMode.Audio &&
                                aiState.quota?.limited == true
                            ) {
                                val q = aiState.quota
                                Text(
                                    text = if (quotaExhausted) {
                                        "Transcription quota exhausted (${q.used} of ${q.limit} used ${quotaPeriodText(q.period)})"
                                    } else {
                                        "${q.remaining} of ${q.limit} transcriptions left ${quotaPeriodText(q.period)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (quotaExhausted) {
                                        Color(0xFFF59E0B)
                                    } else {
                                        Color.White.copy(alpha = 0.66f)
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }

                            if (phase is AiJobPhase.Failed) {
                                Text(
                                    text = phase.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }

                            TvDialogActionRow(
                                title = if (mode == TvAiTranslateMode.Subtitles) {
                                    "Translate"
                                } else {
                                    "Transcribe"
                                },
                                enabled = !quotaExhausted,
                                onClick = {
                                    if (phase is AiJobPhase.Failed) onClearError()
                                    val targetLanguage = TvSubtitleLanguageOptions[targetPos]
                                    if (mode == TvAiTranslateMode.Subtitles) {
                                        val src = subtitleSources[
                                            subtitleSourcePos.coerceIn(0, subtitleSources.lastIndex),
                                        ]
                                        // source_index = the session's combined
                                        // subtitle index (PlayerSubtitleInfo.index),
                                        // NOT the ExoPlayer text-group ordinal.
                                        onSubmit("translate", src.index, src.language, targetLanguage)
                                    } else {
                                        val src = audioSources[
                                            audioSourcePos.coerceIn(0, audioSources.lastIndex),
                                        ]
                                        val sameLanguage = src.language
                                            ?.take(2)
                                            ?.equals(targetLanguage.take(2), ignoreCase = true) == true
                                        onSubmit(
                                            if (sameLanguage) "transcribe" else "transcribe_translate",
                                            src.index,
                                            src.language,
                                            targetLanguage,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** In-dialog job progress: percent bar + server progress_message + Cancel row. */
@Composable
private fun TvAiJobProgress(
    progress: Double,
    message: String?,
    onCancel: () -> Unit,
    cancelFocus: FocusRequester,
) {
    val fraction = progress.coerceIn(0.0, 1.0).toFloat()

    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Translating… ${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
        )
        // Determinate bar — plain Boxes to keep the TV dialog idiom (no
        // Material phone widgets beyond what the player already uses).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.92f)),
            )
        }
        message?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.66f),
            )
        }
        TvDialogActionRow(
            title = "Cancel",
            onClick = onCancel,
            modifier = Modifier.focusRequester(cancelFocus),
        )
    }
}

private fun subtitleSourceLabel(info: PlayerSubtitleInfo): String =
    formatSubtitleTrackDisplayLabel(
        rawLabel = info.label,
        language = info.language,
        codecOrMime = info.codec,
        isForced = info.forced == true,
        index = info.index,
    )

private fun quotaPeriodText(period: String): String = when (period.lowercase()) {
    "day" -> "today"
    "week" -> "this week"
    "month" -> "this month"
    else -> "this $period"
}
