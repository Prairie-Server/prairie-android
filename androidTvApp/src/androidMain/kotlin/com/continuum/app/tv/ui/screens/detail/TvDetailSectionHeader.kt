package com.continuum.app.tv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Editorial section header used below the detail hero. Mirrors the tvOS
 * `TVSectionHeader` — a 20sp bold tracked all-caps eyebrow over a 42sp
 * semibold display title, tightly stacked with no chrome.
 *
 * Sizes are scoped locally (rather than via the shared `sectionEyebrow` /
 * `displaySmall` tokens) so the larger detail-page treatment doesn't shift the
 * other screens that share those tokens at their smaller scale.
 */
@Composable
internal fun TvDetailSectionHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 16.sp,
                letterSpacing = 3.sp,
            ),
            color = Color.White.copy(alpha = 0.55f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                lineHeight = 23.sp,
            ),
            color = Color.White,
        )
    }
}
