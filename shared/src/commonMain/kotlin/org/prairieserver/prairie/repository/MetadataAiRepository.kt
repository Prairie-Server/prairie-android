package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.metadata.MetadataAiStatus
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.MetadataAiApi

/** Thin pass-through over [MetadataAiApi], matching the RequestsRepository shape. */
class MetadataAiRepository(
    private val api: MetadataAiApi,
) {
    suspend fun status(): ApiResult<MetadataAiStatus> = api.status()

    suspend fun translateDescription(contentId: String, targetLanguage: String, scope: org.prairieserver.prairie.network.AuthScopeSnapshot? = null): ApiResult<org.prairieserver.prairie.model.metadata.MetadataTranslationJob> =
        api.translateDescription(contentId, targetLanguage, scope)

    suspend fun captureAuthority() = api.captureAuthority()
    suspend fun isCurrent(scope: org.prairieserver.prairie.network.AuthScopeSnapshot?) = api.isCurrent(scope)
    suspend fun refreshDetail(contentId: String, scope: org.prairieserver.prairie.network.AuthScopeSnapshot?) = api.refreshDetail(contentId, scope)
}
