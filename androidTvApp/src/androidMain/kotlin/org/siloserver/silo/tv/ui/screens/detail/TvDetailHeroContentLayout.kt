package org.siloserver.silo.tv.ui.screens.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp

private enum class DetailHeroContentSlot {
    Editorial,
    Actions,
}

@Composable
internal fun TvDetailHeroContentLayout(
    editorial: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorialActionGap = 12.dp

    SubcomposeLayout(modifier = modifier) { constraints ->
        check(constraints.hasBoundedHeight) {
            "TvDetailHeroContentLayout requires a bounded hero height"
        }

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        // Compose editorial first so semantics/focus traversal keep the visual
        // order, but defer its measurement until the action budget is known.
        val editorialMeasurable = subcompose(DetailHeroContentSlot.Editorial) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomStart,
            ) {
                editorial()
            }
        }.single()
        val actionPlaceable = subcompose(DetailHeroContentSlot.Actions) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                actions()
            }
        }.single().measure(looseConstraints)

        val gapPx = editorialActionGap.roundToPx()
        val editorialMaxHeight = (
            constraints.maxHeight - actionPlaceable.height - gapPx
        ).coerceAtLeast(0)
        val editorialPlaceable = editorialMeasurable.measure(
            looseConstraints.copy(maxHeight = editorialMaxHeight),
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            val actionY = constraints.maxHeight - actionPlaceable.height
            val editorialY = (
                actionY - gapPx - editorialPlaceable.height
            ).coerceAtLeast(0)
            editorialPlaceable.placeRelative(0, editorialY)
            actionPlaceable.placeRelative(0, actionY)
        }
    }
}
