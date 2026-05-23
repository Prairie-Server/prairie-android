package com.continuum.app.tv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.theme.sectionEyebrow

/**
 * Editorial section header used below the detail hero. Mirrors the tvOS
 * `TVSectionHeader` — a tracked all-caps eyebrow over a 42sp display title,
 * tightly stacked with no chrome.
 */
@Composable
internal fun TvDetailSectionHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = sectionEyebrow,
            color = Color.White.copy(alpha = 0.55f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
    }
}
