package org.siloserver.silo.android.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val RatingStarTint = Color(0xFFFFC107)

/**
 * Bottom sheet for the personal 1-5 star rating. Five large tappable
 * stars reflecting the current rating, plus a "Remove rating" action
 * shown only when a rating is set. Styled after the picker sheets in
 * MediaSelectors.kt (header + divider + surface container).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingSheet(
    currentRating: Int?,
    onSetRating: (Int) -> Unit,
    onClearRating: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Rate",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..5).forEach { star ->
                val filled = currentRating != null && star <= currentRating
                IconButton(
                    onClick = { onSetRating(star) },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Rate $star of 5",
                        tint = if (filled) RatingStarTint else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        if (currentRating != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearRating)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Remove rating",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
