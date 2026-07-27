package org.prairieserver.prairie.tv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import org.prairieserver.prairie.metadata.DescriptionTranslationPhase
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.tv.material3.Icon

/**
 * TV description-translation affordance (Apple tvOS parity): a focusable
 * button under the synopsis. Translating keeps a disabled Button (not a bare
 * Row) so pressing translate doesn't remove the focused node from the graph
 * and bounce D-pad focus up to the hero.
 */
@Composable
internal fun TvDescriptionTranslationSection(
    phase: DescriptionTranslationPhase,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (phase) {
        DescriptionTranslationPhase.Translating -> androidx.tv.material3.Button(
            onClick = {},
            enabled = false,
            modifier = modifier,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Text(
                    text = "Translating description…",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
        DescriptionTranslationPhase.Failed -> androidx.tv.material3.Button(
            onClick = onTranslate,
            modifier = modifier,
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Translation failed — retry")
        }
        DescriptionTranslationPhase.Idle -> androidx.tv.material3.Button(
            onClick = onTranslate,
            modifier = modifier,
        ) {
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
