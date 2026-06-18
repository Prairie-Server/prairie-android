package com.continuum.app.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Full-screen empty state with icon, title, and optional subtitle.
 *
 * @param title Primary message (e.g. "No items found").
 * @param subtitle Secondary explanation or suggestion.
 * @param icon Icon displayed above the title. Defaults to an inbox icon.
 * @param modifier Optional modifier for the root layout.
 */
@Composable
fun EmptyStateView(
    title: String,
    subtitle: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // iOS EmptyStateView.swift: 44pt icon at onSurface 0.3 opacity.
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )

        // iOS uses VStack(spacing: 12) between all elements.
        Spacer(modifier = Modifier.height(12.dp))

        // iOS title: .continuumSubheadline (14sp) at onSurface.
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(12.dp))

            // iOS subtitle: .continuumCaption (12sp) at secondary text, with
            // largePadding (24dp) horizontal inset.
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
