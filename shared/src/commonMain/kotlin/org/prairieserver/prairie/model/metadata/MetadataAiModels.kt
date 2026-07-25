package org.prairieserver.prairie.model.metadata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How viewer-facing description translation surfaces on detail screens.
 * Mirrors prairie-apple's MetadataAIStatus.onView; the server emits
 * `off | button | auto` and unknown future values coerce to [Off] (the
 * shared client Json uses coerceInputValues).
 */
@Serializable
enum class MetadataAiOnView {
    @SerialName("off")
    Off,

    @SerialName("button")
    Button,

    @SerialName("auto")
    Auto,
}

/** GET /api/v1/metadata/ai/status — `{enabled:false, on_view:"off"}` when unconfigured. */
@Serializable
data class MetadataAiStatus(
    val enabled: Boolean = false,
    @SerialName("on_view") val onView: MetadataAiOnView = MetadataAiOnView.Off,
)

/** POST /api/v1/items/{id}/translate-description body. */
@Serializable
data class TranslateDescriptionRequest(
    @SerialName("target_language") val targetLanguage: String,
)
