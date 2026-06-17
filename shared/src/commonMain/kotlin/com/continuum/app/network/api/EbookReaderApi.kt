package com.continuum.app.network.api

import com.continuum.app.model.ebook.EbookAnnotation
import com.continuum.app.model.ebook.EbookAnnotationListResponse
import com.continuum.app.model.ebook.EbookConversionCapability
import com.continuum.app.model.ebook.EbookReaderConfig
import com.continuum.app.model.ebook.EbookReaderProgress
import com.continuum.app.model.ebook.SaveEbookAnnotationRequest
import com.continuum.app.model.ebook.SaveEbookProgressRequest
import com.continuum.app.model.ebook.SaveEbookReaderConfigRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.AuthScopeSnapshot
import com.continuum.app.network.authScope
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart

open class EbookReaderApi(private val client: HttpClient) {
    fun readPath(contentId: String, fileId: Int): String =
        "/api/v1/ebooks/${contentId.encodeURLPathPart()}/files/$fileId/read"

    open suspend fun getConversionCapability(): ApiResult<EbookConversionCapability> = safeApiCall {
        client.get("/api/v1/ebooks/capability")
    }

    open suspend fun getProgress(
        contentId: String,
        scope: AuthScopeSnapshot? = null,
    ): ApiResult<EbookReaderProgress> = safeApiCall {
        client.get("/api/v1/ebooks/${contentId.encodeURLPathPart()}/progress") {
            scope?.let { authScope(it) }
        }
    }

    open suspend fun saveProgress(
        contentId: String,
        request: SaveEbookProgressRequest,
        scope: AuthScopeSnapshot? = null,
    ): ApiResult<EbookReaderProgress> = safeApiCall {
        client.put("/api/v1/ebooks/${contentId.encodeURLPathPart()}/progress") {
            scope?.let { authScope(it) }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getReaderConfig(contentId: String): ApiResult<EbookReaderConfig> = safeApiCall {
        client.get("/api/v1/ebooks/${contentId.encodeURLPathPart()}/reader-config")
    }

    suspend fun saveReaderConfig(
        contentId: String,
        request: SaveEbookReaderConfigRequest,
    ): ApiResult<EbookReaderConfig> = safeApiCall {
        client.put("/api/v1/ebooks/${contentId.encodeURLPathPart()}/reader-config") {
            setBody(request)
        }
    }

    suspend fun listAnnotations(contentId: String): ApiResult<EbookAnnotationListResponse> = safeApiCall {
        client.get("/api/v1/ebooks/${contentId.encodeURLPathPart()}/annotations")
    }

    suspend fun createAnnotation(
        contentId: String,
        request: SaveEbookAnnotationRequest,
    ): ApiResult<EbookAnnotation> = safeApiCall {
        client.post("/api/v1/ebooks/${contentId.encodeURLPathPart()}/annotations") {
            setBody(request)
        }
    }

    suspend fun updateAnnotation(
        contentId: String,
        annotationId: String,
        request: SaveEbookAnnotationRequest,
    ): ApiResult<EbookAnnotation> = safeApiCall {
        client.patch("/api/v1/ebooks/${contentId.encodeURLPathPart()}/annotations/${annotationId.encodeURLPathPart()}") {
            setBody(request)
        }
    }

    suspend fun deleteAnnotation(contentId: String, annotationId: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/ebooks/${contentId.encodeURLPathPart()}/annotations/${annotationId.encodeURLPathPart()}")
    }
}
