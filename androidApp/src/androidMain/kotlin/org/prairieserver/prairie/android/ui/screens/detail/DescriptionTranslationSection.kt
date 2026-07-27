package org.prairieserver.prairie.android.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.prairieserver.prairie.metadata.DescriptionTranslationPhase

/**
 * Viewer-facing description-translation affordance rendered under the
 * overview (Apple parity: DescriptionTranslationView). Only composed while
 * the item carries a `pending_translation_language` and the server's
 * metadata-AI on-view mode is button/auto.
 */
@Composable
fun DescriptionTranslationSection(
    phase: DescriptionTranslationPhase,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (phase) {
        DescriptionTranslationPhase.Translating -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = "Translating description…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DescriptionTranslationPhase.Failed -> TextButton(onClick = onTranslate, modifier = modifier) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Translation failed — retry")
        }
        DescriptionTranslationPhase.Idle -> TextButton(onClick = onTranslate, modifier = modifier) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Translate description")
        }
    }
}
