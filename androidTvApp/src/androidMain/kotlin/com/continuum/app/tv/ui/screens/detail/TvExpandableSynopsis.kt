package com.continuum.app.tv.ui.screens.detail

import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * The hero's overview as an expand-in-place control. Mirrors tvOS
 * `TVExpandableSynopsis` 1:1.
 *
 * The overview is clamped to 3 lines (26sp white@0.82, lineSpacing ≈ 8,
 * maxWidth 1200). Pressing OK/Select toggles [expanded]: when expanded the
 * line clamp is removed AND the [tagline] (28sp serif italic white@0.85) is
 * shown above the overview (only when non-blank).
 *
 * This is a **focusable leaf** — the hero's only text focus stop, reachable by
 * pressing Up from the action row, and actionable so it never feels "stuck".
 * It owns its own focus visuals: no chrome at rest, on focus a faint
 * `onSurface@0.55` fill (`RoundedRectangle(8.dp)`, no border). The system halo
 * is suppressed (`indication = null`), matching the squared-control idiom.
 *
 * `animateContentSize` (≈120ms easeOut) animates the expand/collapse; the
 * animation is disabled when the device has motion reduced (animator duration
 * scale 0).
 */
@Composable
internal fun TvExpandableSynopsis(
    overview: String,
    tagline: String?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(overview) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)

    // Best-effort reduce-motion: animator duration scale of 0 means the user
    // (or the OS) has disabled animations.
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    val sizeModifier = if (reduceMotion) {
        Modifier
    } else {
        Modifier.animateContentSize(animationSpec = tween(durationMillis = 160))
    }

    Column(
        modifier = modifier
            .widthIn(max = 1200.dp)
            .then(
                if (isFocused) {
                    Modifier.background(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { expanded = !expanded }
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .then(sizeModifier),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (expanded && !tagline.isNullOrBlank()) {
            Text(
                text = tagline,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Text(
            text = overview,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
