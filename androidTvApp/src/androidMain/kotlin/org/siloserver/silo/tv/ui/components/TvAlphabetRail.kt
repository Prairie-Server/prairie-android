package org.siloserver.silo.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/**
 * Right-edge vertical A–Z jump rail for the library Browse grid. 1:1 with
 * tvOS `TVAlphabetRail.swift`: holding the remote's d-pad through tens of
 * thousands of titles is not a real navigation strategy, so the rail lets the
 * user jump straight to a title prefix.
 *
 * Letters: `All`, `#`, then A–Z. `#` jumps to titles whose sort-title begins
 * with a non-alpha character. Letters are monospaced so every advance width is
 * equal and the column reads as an even rail. The selected / focused letter
 * inverts to a white rounded chip.
 *
 * The rail collapses to a thin edge peek (~18dp) when no letter holds focus
 * and expands (~96dp) when focus enters it, so the grid can breathe while
 * unfocused. On select it calls [onSelect] with the chosen prefix (null for
 * "All", "#" for the symbol bucket, else the letter), which the screen routes
 * to the browse name-prefix filter / grid jump.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAlphabetRail(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val letters = remember {
        buildList {
            add("All")
            add("#")
            ('A'..'Z').forEach { add(it.toString()) }
        }
    }

    var expanded by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (expanded) 96.dp else 18.dp,
        animationSpec = tween(durationMillis = 180),
        label = "alphabetRailWidth",
    )

    LazyColumn(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            // Expand while focus is anywhere in the rail; collapse to the edge
            // peek once focus leaves. hasFocus stays true moving between letters,
            // so there's no collapse/expand flicker during traversal.
            .onFocusChanged { expanded = it.hasFocus },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        items(letters.size) { index ->
            val letter = letters[index]
            val isSelected = when (letter) {
                "All" -> selected == null
                "#" -> selected == "#"
                else -> selected == letter
            }
            LetterButton(
                letter = letter,
                isSelected = isSelected,
                expanded = expanded,
                onClick = {
                    onSelect(
                        when (letter) {
                            "All" -> null
                            "#" -> "#"
                            else -> letter
                        },
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LetterButton(
    letter: String,
    isSelected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val chip = isFocused || isSelected
    val containerColor = if (chip) Color.White else Color.Transparent
    val contentColor = when {
        chip -> Color.Black
        else -> Color.White.copy(alpha = 0.55f)
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White,
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.size(if (expanded) 38.dp else 12.dp),
    ) {
        Box(modifier = Modifier.size(if (expanded) 38.dp else 12.dp), contentAlignment = Alignment.Center) {
            Text(
                text = letter,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (expanded) 15.sp else 8.sp,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
