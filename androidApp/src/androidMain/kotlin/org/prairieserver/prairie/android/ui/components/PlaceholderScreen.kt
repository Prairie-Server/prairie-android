package org.prairieserver.prairie.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Generic placeholder screen used during development.
 * Other agents replace calls to this composable with real screen content.
 *
 * @param screenName Name shown in the center and in the top bar.
 * @param onBack When non-null, a top bar with back navigation is shown.
 */
@Composable
fun PlaceholderScreen(
    screenName: String,
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            if (onBack != null) {
                PrairieTopBar(
                    title = screenName,
                    onBackClick = onBack,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = screenName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Coming soon",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
