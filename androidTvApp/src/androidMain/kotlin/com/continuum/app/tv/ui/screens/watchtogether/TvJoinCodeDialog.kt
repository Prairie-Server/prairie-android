package com.continuum.app.tv.ui.screens.watchtogether

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.screens.player.TvDialogActionRow
import com.continuum.app.tv.ui.theme.DarkBackground
import com.continuum.app.tv.ui.theme.FocusedContainer
import com.continuum.app.tv.ui.theme.FocusedContent

/**
 * Pure 8-char D-pad join-code accumulator. Appends only A–Z / 0–9 (uppercasing
 * letters), caps at [LENGTH], and supports backspace/clear. Kept free of any
 * Android types so it is unit-testable without Robolectric.
 */
data class JoinCodeState(val code: String = "") {
    val isComplete: Boolean get() = code.length == LENGTH
    fun append(c: Char): JoinCodeState {
        if (code.length >= LENGTH) return this
        val up = c.uppercaseChar()
        return if (up in 'A'..'Z' || up in '0'..'9') copy(code = code + up) else this
    }
    fun backspace() = if (code.isEmpty()) this else copy(code = code.dropLast(1))
    fun clear() = JoinCodeState()
    companion object { const val LENGTH = 8 }
}

/** Keys laid out in the focusable A–Z / 0–9 grid, six per row. */
private val JoinCodeKeys: List<Char> = ('A'..'Z') + ('0'..'9')
private const val KEYS_PER_ROW = 6

/**
 * D-pad join-by-code dialog. Panel + row idiom mirrors
 * [com.continuum.app.tv.ui.screens.player.TvSubtitleSearchDialog]. Shows a large
 * mono display of the running code (placeholder dashes for unfilled slots), a
 * focusable A–Z/0–9 key grid (each key appends into a [JoinCodeState]), a Delete
 * row (backspace), and a "Join" [TvDialogActionRow] enabled when the code is
 * complete and not busy.
 */
@Composable
fun TvJoinCodeDialog(
    isBusy: Boolean,
    error: String?,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var state by remember { mutableStateOf(JoinCodeState()) }
    val firstKeyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKeyFocus.requestFocus() } }

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
                .padding(start = 72.dp, top = 80.dp, end = 72.dp, bottom = 60.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelShape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .background(color = DarkBackground.copy(alpha = 0.68f), shape = panelShape)
                    .border(1.2.dp, Color.White.copy(alpha = 0.20f), panelShape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "JOIN BY CODE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White.copy(alpha = 0.58f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                // Large mono display: filled chars + dashes for empty slots.
                val display = (0 until JoinCodeState.LENGTH).joinToString(" ") { i ->
                    state.code.getOrNull(i)?.toString() ?: "–"
                }
                Text(
                    text = display,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 32.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )

                // Focusable A–Z / 0–9 grid.
                JoinCodeKeys.chunked(KEYS_PER_ROW).forEachIndexed { rowIndex, rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowKeys.forEachIndexed { colIndex, ch ->
                            JoinCodeKey(
                                char = ch,
                                enabled = !isBusy,
                                onClick = { state = state.append(ch) },
                                modifier = if (rowIndex == 0 && colIndex == 0) {
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(firstKeyFocus)
                                } else {
                                    Modifier.weight(1f)
                                },
                            )
                        }
                        // Pad the final partial row so keys keep a consistent width.
                        repeat(KEYS_PER_ROW - rowKeys.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }

                TvDialogActionRow(
                    title = "Delete",
                    enabled = !isBusy && state.code.isNotEmpty(),
                    onClick = { state = state.backspace() },
                )

                TvDialogActionRow(
                    title = if (isBusy) "Joining…" else "Join",
                    enabled = state.isComplete && !isBusy,
                    onClick = { onJoin(state.code) },
                )

                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

/** Single focusable key tile in the join-code grid. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun JoinCodeKey(
    char: Char,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)

    Surface(
        onClick = { if (enabled) onClick() },
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
            disabledContainerColor = Color.White.copy(alpha = 0.03f),
            disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, DarkBackground.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.18f),
                elevation = 14.dp,
            ),
        ),
        modifier = modifier
            .height(48.dp)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.98f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = char.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isFocused) FocusedContent else Color.White,
            )
        }
    }
}
