package org.siloserver.silo.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.tv.ui.theme.Spacing

/**
 * Bottom-anchored slide-up filter sheet for the library detail screen.
 * Mirrors the tvOS TVLibraryFilterSheet pattern: a 60%-height surface
 * with Genre / Year / Sort / Alphabet sections, focus-trapped, Back to
 * dismiss.
 *
 * Sections are slotted by the caller via [content] so this component
 * stays generic; the library detail screen composes the actual filter
 * sections inside it.
 */
@Composable
fun TvFilterSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180)),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top scrim — dims the content above the sheet. Click is not
            // captured (TV has no click) — Back dismisses.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.40f)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .align(Alignment.TopStart),
            )

            // Sheet surface — bottom 60%, slides up from below.
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 280),
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 220),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.60f)
                    .align(Alignment.BottomStart),
            ) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(visible) {
                    if (visible) {
                        runCatching { focusRequester.requestFocus() }
                    }
                }

                BackHandler(enabled = visible, onBack = onDismiss)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(
                            horizontal = Spacing.safeArea,
                            vertical = Spacing.xl,
                        )
                        .focusGroup()
                        .focusRequester(focusRequester),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    Text(
                        text = "Filters",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    content()
                }
            }
        }
    }
}
