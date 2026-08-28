package org.prairieserver.prairie.android.ui.screens.player

import org.prairieserver.prairie.android.ui.util.formatBytes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.prairieserver.prairie.model.catalog.FileVersion
import org.prairieserver.prairie.playback.QualityMenuOption

/**
 * Bottom sheet for selecting encode quality (Auto / Original / ladder rungs)
 * and optionally a file version when multiple encodes exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelector(
    versions: List<FileVersion>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    qualityOptions: List<QualityMenuOption> = emptyList(),
    selectedQualityId: String = "auto",
    onSelectQuality: (String) -> Unit = {},
    tabletopPaneHeight: Dp? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        tabletopPaneHeight = tabletopPaneHeight,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Cap below the top edge + keep content flings from
                // dismissing the sheet — see PlayerSheetSupport.
                .playerSheetContent(tabletopPaneHeight)
                .nestedScroll(PlayerSheetFlingGuard)
                .padding(bottom = 32.dp),
        ) {
            PlayerSheetHeader(
                title = if (qualityOptions.isNotEmpty() && versions.size > 1) {
                    "Quality & Version"
                } else if (qualityOptions.isNotEmpty()) {
                    "Quality"
                } else {
                    "Quality"
                },
                subtitle = when {
                    qualityOptions.isNotEmpty() && versions.size > 1 ->
                        "Choose a transcode rung or source version"
                    qualityOptions.isNotEmpty() ->
                        "Choose a transcode rung"
                    versions.size > 1 ->
                        "Choose a source version"
                    else ->
                        null
                },
                onDismiss = onDismiss,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (qualityOptions.isNotEmpty()) {
                LazyColumn {
                    items(qualityOptions, key = { it.id }) { option ->
                        QualityOptionRow(
                            label = option.label,
                            detail = option.sublabel.ifBlank { null },
                            isSelected = option.id.equals(selectedQualityId, ignoreCase = true),
                            onClick = {
                                onSelectQuality(option.id)
                                onDismiss()
                            },
                        )
                    }
                }
            }

            if (versions.size > 1) {
                if (qualityOptions.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Version",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                LazyColumn {
                    itemsIndexed(
                        versions,
                        contentType = { _, _ -> "quality-version" },
                    ) { index, version ->
                        val label = buildString {
                            version.resolution?.let { append(it) } ?: append("Unknown")
                            if (version.hdr) append(" HDR")
                        }
                        val detail = buildString {
                            version.codecVideo?.uppercase()?.let { append(it) }
                            version.codecAudio?.uppercase()?.let {
                                if (isNotEmpty()) append(" + ")
                                append(it)
                            }
                            if (version.fileSize > 0) {
                                if (isNotEmpty()) append(" - ")
                                append(formatBytes(version.fileSize))
                            }
                        }.ifEmpty { null }
                        QualityOptionRow(
                            label = label,
                            detail = detail,
                            isSelected = selectedIndex == index,
                            onClick = {
                                onSelect(index)
                                onDismiss()
                            },
                        )
                    }
                }
            } else if (qualityOptions.isEmpty()) {
                Text(
                    text = "No alternate qualities available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun QualityOptionRow(
    label: String,
    detail: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
