package org.siloserver.silo.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.siloserver.silo.model.metadata.MetadataAiStatus
import org.siloserver.silo.model.metadata.TranslateDescriptionRequest
import org.siloserver.silo.network.ApiResult

/**
 * Viewer-facing metadata AI (description translation). Mirrors silo-apple's
 * ContinuumAI metadata surface; the admin metadata-translation job endpoints
 * are intentionally NOT exposed here.
 */
interface MetadataAiApi {

    /** GET /api/v1/metadata/ai/status — `{enabled:false, on_view:"off"}` when unconfigured. */
    suspend fun status(): ApiResult<MetadataAiStatus>

    /**
     * POST /api/v1/items/{id}/translate-description — 202 queues (duplicate
     * viewers collapse onto one job); 503 unconfigured; 404 unknown item.
     * Completion has no job endpoint: re-fetch item detail until
     * `pending_translation_language` clears.
     */
    suspend fun translateDescription(contentId: String, targetLanguage: String): ApiResult<Unit>
}

class DefaultMetadataAiApi(private val client: HttpClient) : MetadataAiApi {

    override suspend fun status(): ApiResult<MetadataAiStatus> = safeApiCall {
        client.get("/api/v1/metadata/ai/status")
    }

    override suspend fun translateDescription(
        contentId: String,
        targetLanguage: String,
    ): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/items/$contentId/translate-description") {
            contentType(ContentType.Application.Json)
            setBody(TranslateDescriptionRequest(targetLanguage = targetLanguage))
        }
    }
}
