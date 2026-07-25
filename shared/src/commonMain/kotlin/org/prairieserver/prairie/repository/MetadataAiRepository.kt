package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.metadata.MetadataAiStatus
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.MetadataAiApi

/** Thin pass-through over [MetadataAiApi], matching the RequestsRepository shape. */
class MetadataAiRepository(
    private val api: MetadataAiApi,
) {
    suspend fun status(): ApiResult<MetadataAiStatus> = api.status()

    suspend fun translateDescription(contentId: String, targetLanguage: String): ApiResult<Unit> =
        api.translateDescription(contentId, targetLanguage)
}
