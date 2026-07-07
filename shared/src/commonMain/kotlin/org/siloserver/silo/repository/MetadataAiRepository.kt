package org.siloserver.silo.repository

import org.siloserver.silo.model.metadata.MetadataAiStatus
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.api.MetadataAiApi

/** Thin pass-through over [MetadataAiApi], matching the RequestsRepository shape. */
class MetadataAiRepository(
    private val api: MetadataAiApi,
) {
    suspend fun status(): ApiResult<MetadataAiStatus> = api.status()

    suspend fun translateDescription(contentId: String, targetLanguage: String): ApiResult<Unit> =
        api.translateDescription(contentId, targetLanguage)
}
