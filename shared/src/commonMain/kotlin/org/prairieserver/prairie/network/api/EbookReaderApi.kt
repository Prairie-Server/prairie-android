package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.ebook.EbookConversionCapability
import org.prairieserver.prairie.model.ebook.EbookReaderProgress
import org.prairieserver.prairie.model.ebook.SaveEbookProgressRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.apiv2.EbookReaderV2Api
import io.ktor.http.encodeURLPathPart

open class EbookReaderApi(private val v2: EbookReaderV2Api) {
    fun readPath(contentId: String, fileId: Int): String =
        "/api/v2/ebooks/${contentId.encodeURLPathPart()}/files/$fileId/read"

    open suspend fun getConversionCapability(): ApiResult<EbookConversionCapability> = v2.capability()

    open suspend fun getProgress(
        contentId: String,
        scope: AuthScopeSnapshot? = null,
    ): ApiResult<EbookReaderProgress> = v2.progress(contentId, scope)

    open suspend fun saveProgress(
        contentId: String,
        request: SaveEbookProgressRequest,
        scope: AuthScopeSnapshot? = null,
    ): ApiResult<EbookReaderProgress> = v2.saveProgress(contentId, request, scope)
}
