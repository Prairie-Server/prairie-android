package org.prairieserver.prairie.model.audiobook

import org.prairieserver.prairie.model.ebook.MediaPerson
import org.prairieserver.prairie.model.ebook.MediaRelatedContent
import org.prairieserver.prairie.model.ebook.MediaSeriesGroup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudiobookNarration(
    @SerialName("content_id") val contentId: String,
    val title: String,
    val year: Int? = null,
    val narrators: List<String> = emptyList(),
)

@Serializable
data class AudiobookMetadata(
    val authors: List<MediaPerson> = emptyList(),
    val narrators: List<MediaPerson> = emptyList(),
    val publisher: String? = null,
    @SerialName("total_duration_seconds") val totalDurationSeconds: Int? = null,
    val series: MediaSeriesGroup? = null,
    @SerialName("other_narrations") val otherNarrations: List<AudiobookNarration> = emptyList(),
    val related: MediaRelatedContent = MediaRelatedContent(),
) {
    val authorNames: String?
        get() = authors.mapNotNull { it.name.takeIf(String::isNotBlank) }
            .joinToString(", ")
            .takeIf(String::isNotBlank)

    val narratorNames: String?
        get() = narrators.mapNotNull { it.name.takeIf(String::isNotBlank) }
            .joinToString(", ")
            .takeIf(String::isNotBlank)
}
