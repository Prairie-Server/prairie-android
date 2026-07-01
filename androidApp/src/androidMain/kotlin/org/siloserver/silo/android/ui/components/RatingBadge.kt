package org.siloserver.silo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * The source of a rating value, which determines the badge's visual style.
 */
enum class RatingSource(val label: String, val color: Color) {
    IMDB("IMDb", Color(0xFFF5C518)),
    TMDB("TMDb", Color(0xFF01D277)),
    RT_CRITIC("Critics", Color(0xFFFA320A)),
    RT_AUDIENCE("Audience", Color(0xFFFA320A)),
}

/**
 * A compact badge showing a rating value with its source label.
 *
 * @param value The numeric rating value (e.g. 7.8 for IMDb, 85 for RT).
 * @param source Which rating service this value comes from.
 * @param modifier Compose modifier.
 */
@Composable
fun RatingBadge(
    value: Double,
    source: RatingSource,
    modifier: Modifier = Modifier,
) {
    val displayValue = when (source) {
        RatingSource.RT_CRITIC, RatingSource.RT_AUDIENCE -> "${value.toInt()}%"
        else -> String.format(Locale.US, "%.1f", value)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(source.color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = source.color,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = displayValue,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = source.color,
        )
        Text(
            text = source.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.88f),
        )
    }
}
