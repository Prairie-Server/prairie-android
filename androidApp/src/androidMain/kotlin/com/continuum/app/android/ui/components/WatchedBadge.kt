package com.continuum.app.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.theme.SuccessGreen

/**
 * Small checkmark overlay badge indicating an item has been watched.
 *
 * Intended to be placed in the top-end corner of a media poster via a Box
 * with Alignment.TopEnd.
 *
 * @param modifier Compose modifier for positioning.
 */
@Composable
fun WatchedBadge(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(SuccessGreen),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Watched",
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}
