package org.siloserver.silo.tv.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.tv.ui.shell.TvTopMenuLayout
import org.siloserver.silo.tv.ui.theme.SiloBlue
import org.siloserver.silo.tv.ui.theme.Spacing
import org.siloserver.silo.tv.ui.theme.sectionEyebrow

/**
 * Shared Admin screen header in the tvOS Aurora/Skyline grammar — a mono-caps
 * eyebrow above a `displaySmall` title, padded to the same safe-area / top-menu
 * inset the Settings, Requests and Inbox surfaces use. An optional one-shot
 * subtitle line surfaces transient admin messages.
 *
 * This replaces the older per-screen icon + title rows so every admin surface
 * matches the rest of the 10-foot UI.
 */
@Composable
fun TvAdminScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
) {
    Column(
        modifier = Modifier.padding(
            start = Spacing.safeArea,
            end = Spacing.safeArea,
            top = TvTopMenuLayout.contentTopInset,
            bottom = Spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = eyebrow,
            style = sectionEyebrow,
            color = SiloBlue.copy(alpha = 0.92f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
