package com.continuum.app.repository

import com.continuum.app.model.ebook.SaveEbookAnnotationRequest
import com.continuum.app.model.ebook.SaveEbookProgressRequest
import com.continuum.app.model.ebook.SaveEbookReaderConfigRequest
import com.continuum.app.network.api.EbookReaderApi

class EbookReaderRepository(private val api: EbookReaderApi) {
    fun readPath(contentId: String, fileId: Int): String =
        api.readPath(contentId, fileId)

    suspend fun getProgress(contentId: String) =
        api.getProgress(contentId)

    suspend fun saveProgress(contentId: String, request: SaveEbookProgressRequest) =
        api.saveProgress(contentId, request)

    suspend fun getReaderConfig(contentId: String) =
        api.getReaderConfig(contentId)

    suspend fun saveReaderConfig(contentId: String, request: SaveEbookReaderConfigRequest) =
        api.saveReaderConfig(contentId, request)

    suspend fun listAnnotations(contentId: String) =
        api.listAnnotations(contentId)

    suspend fun createBookmark(contentId: String, location: String) =
        api.createAnnotation(
            contentId = contentId,
            request = SaveEbookAnnotationRequest(kind = "bookmark", location = location),
        )

    suspend fun updateAnnotation(contentId: String, annotationId: String, request: SaveEbookAnnotationRequest) =
        api.updateAnnotation(contentId, annotationId, request)

    suspend fun deleteAnnotation(contentId: String, annotationId: String) =
        api.deleteAnnotation(contentId, annotationId)
}
