package com.continuum.app.tv.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.theme.AccentLavender
import com.continuum.app.tv.ui.theme.FocusedContainer
import com.continuum.app.tv.ui.theme.FocusedContent

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAlphabetRail(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusedLetter by remember { mutableStateOf<String?>(null) }
    val letters = remember {
        buildList {
            add("All")
            add("#")
            ('A'..'Z').forEach { add(it.toString()) }
        }
    }

    LazyColumn(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(999.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .padding(horizontal = 3.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(letters, key = { it }) { letter ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            val railFocused = focusedLetter != null

            Surface(
                onClick = {
                    onSelect(
                        when (letter) {
                            "All" -> null
                            "#" -> "#"
                            else -> letter
                        },
                    )
                },
                interactionSource = interactionSource,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected(letter, selected)) {
                        AccentLavender.copy(alpha = 0.28f)
                    } else {
                        Color.Transparent
                    },
                    contentColor = Color.White.copy(alpha = 0.65f),
                    focusedContainerColor = FocusedContainer,
                    focusedContentColor = FocusedContent,
                    pressedContainerColor = FocusedContainer,
                    pressedContentColor = FocusedContent,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier
                    .width(36.dp)
                    .height(if (railFocused) 26.dp else 20.dp)
                    .onFocusChanged { focusState ->
                        if (focusState.hasFocus) {
                            focusedLetter = letter
                        } else if (focusedLetter == letter) {
                            focusedLetter = null
                        }
                    },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter,
                        style = if (railFocused) {
                            MaterialTheme.typography.labelLarge
                        } else {
                            MaterialTheme.typography.labelSmall
                        },
                        maxLines = 1,
                        color = when {
                            isFocused -> FocusedContent
                            isSelected(letter, selected) -> Color.White
                            else -> Color.White.copy(alpha = if (railFocused) 0.62f else 0.36f)
                        },
                    )
                }
            }
        }
    }
}

private fun isSelected(letter: String, selected: String?): Boolean = when (letter) {
    "All" -> selected == null
    "#" -> selected == "#"
    else -> selected == letter
}
