package com.continuum.app.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/**
 * The Aurora first-run chrome — eyebrow, glass panel, cream primary button and
 * ghost button — ported from silo-apple's `AuroraStyle.swift`. A warm,
 * cinematic skin (gold accent, plum-night) layered over the onboarding screens.
 */

val AuroraInk = Color(0xFFF3EFE9)
val AuroraAccent = Color(0xFFF3D3A0)
private val AuroraGlassTint = Color(0xFF171019)
private val AuroraCreamTop = Color(0xFFFDF7EC)
private val AuroraCreamBottom = Color(0xFFF1E3CD)
private val AuroraCreamInk = Color(0xFF20160A)
private val AuroraNightBottom = Color(0xFF070509)

/** Gold-hairline + mono-caps step label, e.g. "STEP 01 — CONNECT". */
@Composable
fun AuroraEyebrow(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(listOf(AuroraAccent, AuroraAccent.copy(alpha = 0f))),
                ),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            letterSpacing = 3.5.sp,
            color = AuroraAccent,
        )
    }
}

/**
 * Liquid-glass panel chrome (translucent plum tint + gradient hairline + top
 * sheen + soft drop shadow; optional gold halo). Compose has no backdrop blur,
 * so the tint is kept translucent enough for the aurora to glow through.
 */
fun Modifier.auroraGlass(cornerRadius: Dp = 28.dp, emphasized: Boolean = false): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(elevation = 60.dp, shape = shape, clip = false)
        .clip(shape)
        .background(AuroraGlassTint.copy(alpha = 0.62f))
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
            ),
        )
        .border(
            width = 1.dp,
            brush = if (emphasized) {
                Brush.verticalGradient(
                    listOf(AuroraAccent.copy(alpha = 0.6f), AuroraAccent.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)),
                )
            } else {
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.02f)),
                )
            },
            shape = shape,
        )
}

/** Warm cream pill with a gold focus glow — the Aurora primary action. */
@Composable
fun AuroraPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "auroraPrimaryScale")
    val glow by animateDpAsState(if (isFocused) 26.dp else 14.dp, label = "auroraPrimaryGlow")

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = glow,
                shape = shape,
                clip = false,
                ambientColor = if (isFocused) AuroraAccent else Color.Black,
                spotColor = if (isFocused) AuroraAccent else Color.Black,
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(AuroraCreamTop, AuroraCreamBottom)))
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = shape,
            )
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = AuroraCreamInk, modifier = Modifier.size(24.dp))
            }
            Text(text = label, color = AuroraCreamInk, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
        }
    }
}

/** Tertiary ghost button (e.g. "Use a password instead", "Change server"). */
@Composable
fun AuroraGhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val fill by animateColorAsState(if (isFocused) AuroraInk else Color.White.copy(alpha = 0.06f), label = "auroraGhostFill")
    val content = if (isFocused) AuroraNightBottom else AuroraInk.copy(alpha = 0.62f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.14f),
                shape = shape,
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = content, fontWeight = FontWeight.Medium, fontSize = 22.sp)
    }
}
