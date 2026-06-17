package com.continuum.app.tv.ui.screens.detail

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.continuum.app.model.catalog.FileVersion
import com.continuum.app.tv.ui.components.TvHeroActionPill
import com.continuum.app.tv.ui.components.TvPillVariant
import com.continuum.app.tv.ui.components.TvDialogOption
import com.continuum.app.tv.ui.components.TvOptionDialog

/**
 * Compact version selector for multi-file items. Every file is its own option
 * (parity with the phone's VersionPickerSheet) — files that share a resolution
 * are NOT collapsed, so the user can pick a specific encode (e.g. two 2160p HDR
 * files with different audio/source). A codec · audio · size detail line
 * disambiguates same-quality files.
 */
@Composable
fun TvVersionPicker(
    versions: List<FileVersion>,
    fallbackLabel: String? = null,
    selectedFileId: Int?,
    lastFileId: Int?,
    onSelectedFileId: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onDirectionDown: (() -> Boolean)? = null,
) {
    val qualityOptions = remember(versions) { versions.toQualityOptions() }
    if (qualityOptions.isEmpty() && fallbackLabel.isNullOrBlank()) return

    var dialogOpen by remember { mutableStateOf(false) }

    val selectedOption = qualityOptions.firstOrNull { selectedFileId != null && it.fileIds.contains(selectedFileId) }
    val effectiveOption = selectedOption
        ?: qualityOptions.firstOrNull { lastFileId != null && it.fileIds.contains(lastFileId) }
        ?: qualityOptions.firstOrNull()

    val buttonLabel = selectedOption?.label
        ?: effectiveOption?.label
        ?: fallbackLabel
        ?: "Auto"

    TvVersionPill(
        label = buttonLabel,
        canOpen = qualityOptions.isNotEmpty(),
        onClick = { if (qualityOptions.isNotEmpty()) dialogOpen = true },
        modifier = modifier,
        focusRequester = focusRequester,
        upFocusRequester = upFocusRequester,
        downFocusRequester = downFocusRequester,
        onDirectionDown = onDirectionDown,
    )

    if (dialogOpen) {
        val options = buildList {
            add(
                TvDialogOption(
                    key = "auto",
                    title = "Auto",
                    subtitle = effectiveOption?.let { "Currently picks ${it.label}" }
                        ?: "Best match for this device",
                    selected = selectedFileId == null,
                    onClick = {
                        dialogOpen = false
                        onSelectedFileId(null)
                    },
                ),
            )
            qualityOptions.forEach { option ->
                add(
                    TvDialogOption(
                        key = option.key,
                        title = option.label,
                        subtitle = option.detail,
                        selected = selectedFileId != null && option.fileIds.contains(selectedFileId),
                        onClick = {
                            dialogOpen = false
                            onSelectedFileId(option.representativeFileId)
                        },
                    ),
                )
            }
        }

        TvOptionDialog(
            title = "Quality",
            options = options,
            onDismiss = { dialogOpen = false },
        )
    }
}

@Composable
private fun TvVersionPill(
    label: String,
    canOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onDirectionDown: (() -> Boolean)? = null,
) {
    TvHeroActionPill(
        label = label,
        icon = Icons.Filled.Tune,
        trailingIcon = if (canOpen) Icons.Filled.KeyboardArrowDown else null,
        variant = TvPillVariant.Hollow,
        onClick = onClick,
        modifier = modifier
            .widthIn(min = 150.dp)
            .then(
                if (onDirectionDown != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            onDirectionDown()
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                },
            ),
        focusRequester = focusRequester,
        upFocusRequester = upFocusRequester,
        downFocusRequester = downFocusRequester,
        heightOverride = 48.dp,
        horizontalPaddingOverride = 24.dp,
    )
}

internal fun List<FileVersion>.hasTvVersionChoices(): Boolean = toQualityOptions().isNotEmpty()

private data class TvQualityOption(
    val key: String,
    val label: String,
    val detail: String?,
    val representativeFileId: Int,
    val fileIds: Set<Int>,
    val rank: Int,
    val order: Int,
)

private fun List<FileVersion>.toQualityOptions(): List<TvQualityOption> =
    mapIndexed { index, version ->
        // Keep every file as its own option — no collapsing by quality key.
        val detailParts = buildList {
            version.videoCodecLabel()?.let { add(it) }
            version.audioCodecLabel()?.let { add(it) }
            version.fileSizeLabel()?.let { add(it) }
        }
        TvQualityOption(
            key = "file-${version.fileId}",
            label = version.qualityLabel(),
            detail = detailParts.joinToString(" · ").ifBlank { null },
            representativeFileId = version.fileId,
            fileIds = setOf(version.fileId),
            rank = version.resolutionRank(),
            order = index,
        )
    }.sortedWith(
        compareByDescending<TvQualityOption> { it.rank }
            .thenBy { it.order },
    )

private fun FileVersion.fileSizeLabel(): String? {
    if (fileSize <= 0) return null
    val sizeMb = fileSize / (1024.0 * 1024.0)
    return if (sizeMb >= 1024) "%.1f GB".format(sizeMb / 1024.0) else "%.0f MB".format(sizeMb)
}

private fun FileVersion.qualityLabel(): String {
    val parts = buildList {
        resolution?.takeIf { it.isNotBlank() }?.let { add(it) }
        val hdrLabel = hdrFormatLabel()
        if (hdrLabel != null) {
            add(hdrLabel)
        } else if (hdr) {
            add("HDR")
        }
        audioCodecLabel()?.let { add(it) }
    }
    return parts.joinToString(" - ").ifBlank {
        videoCodecLabel() ?: fileName ?: "Version $fileId"
    }
}

private fun FileVersion.videoCodecLabel(): String? {
    val codec = codecVideo?.trim().orEmpty()
    if (codec.isBlank()) return null
    val normalized = codec.lowercase()
    return when {
        normalized.contains("hevc") || normalized.contains("h265") -> "HEVC"
        normalized.contains("av1") -> "AV1"
        normalized.contains("vp9") -> "VP9"
        normalized.contains("h264") || normalized.contains("avc") -> "H264"
        else -> codec.uppercase()
    }
}

private fun FileVersion.hdrFormatLabel(): String? {
    val format = videoTracks
        ?.firstOrNull { it.hdrFormat?.isNotBlank() == true }
        ?.hdrFormat
        ?.trim()
        .orEmpty()
    val normalized = format.lowercase()
    return when {
        normalized.contains("dolby") || normalized.contains("dv") -> "Dolby Vision"
        normalized.contains("hdr10+") || normalized.contains("hdr10plus") -> "HDR10+"
        normalized.contains("hdr10") -> "HDR10"
        normalized.contains("hlg") -> "HLG"
        hdr -> "HDR"
        else -> null
    }
}

private fun FileVersion.audioCodecLabel(): String? {
    val trackCodec = audioTracks
        ?.firstOrNull { it.isDefault }
        ?.codec
        ?: audioTracks?.firstOrNull()?.codec
    val codec = trackCodec?.trim().takeUnless { it.isNullOrBlank() }
        ?: codecAudio?.trim().takeUnless { it.isNullOrBlank() }
        ?: return null
    val normalized = codec.lowercase()
    return when {
        normalized.contains("truehd") -> "TrueHD"
        normalized.contains("atmos") -> "Atmos"
        normalized.contains("dts-hd") || normalized.contains("dtshd") -> "DTS-HD"
        normalized.contains("dts") -> "DTS"
        normalized.contains("eac3") || normalized.contains("ec-3") -> "EAC3"
        normalized.contains("ac3") || normalized.contains("ac-3") -> "AC3"
        normalized.contains("aac") -> "AAC"
        normalized.contains("flac") -> "FLAC"
        normalized.contains("opus") -> "Opus"
        normalized.contains("mp3") -> "MP3"
        else -> codec.uppercase()
    }
}

private fun FileVersion.resolutionRank(): Int = when {
    normalizedResolution(resolution).contains("2160") -> 4
    normalizedResolution(resolution).contains("1080") -> 3
    normalizedResolution(resolution).contains("720") -> 2
    normalizedResolution(resolution).contains("480") -> 1
    else -> 0
}

private fun normalizedResolution(value: String?): String = value?.trim()?.lowercase().orEmpty()
