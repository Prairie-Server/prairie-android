package org.siloserver.silo.android.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A badge for displaying content ratings (PG-13, R, TV-MA, etc.).
 *
 * Renders as a bordered outline badge to distinguish it from genre chips.
 *
 * @param rating The content rating string (e.g. "PG-13", "R", "TV-MA").
 * @param modifier Compose modifier.
 */
@Composable
fun ContentRatingBadge(
    rating: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text = rating,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.86f),
        modifier = modifier
            .clip(shape)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.34f),
                shape = shape,
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
