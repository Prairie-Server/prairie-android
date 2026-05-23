package com.continuum.app.network.api

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import com.continuum.app.model.catalog.CatalogResponse
import com.continuum.app.model.personal.Collection
import com.continuum.app.model.personal.CollectionGroup
import com.continuum.app.model.personal.CollectionsResponse
import com.continuum.app.model.personal.CreateCollectionGroupRequest
import com.continuum.app.model.personal.CreateCollectionRequest
import com.continuum.app.model.personal.ReorderCollectionGroupsRequest
import com.continuum.app.model.personal.ReorderCollectionsRequest
import com.continuum.app.model.personal.UpdateCollectionGroupRequest
import com.continuum.app.model.personal.UpdateCollectionRequest
import com.continuum.app.network.ApiResult
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class CollectionApi(private val client: HttpClient) {

    suspend fun listCollections(): ApiResult<CollectionsResponse> = safeApiCall {
        client.get("/api/v1/collections")
    }

    suspend fun createCollection(request: CreateCollectionRequest): ApiResult<Collection> = safeApiCall {
        client.post("/api/v1/collections") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateCollection(
        id: String,
        request: UpdateCollectionRequest
    ): ApiResult<Collection> = safeApiCall {
        client.put("/api/v1/collections/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    /**
     * Moves a collection between groups. Sends `group_id` explicitly as
     * the JSON literal `null` for the Ungrouped target — the shared
     * [com.continuum.app.network.ContinuumJson] is configured with
     * `explicitNulls = false`, which would otherwise drop the field from
     * the body and leave the server unable to distinguish "clear group"
     * from "don't change group".
     */
    suspend fun moveCollectionToGroup(
        id: String,
        groupId: String?,
    ): ApiResult<Collection> = safeApiCall {
        client.put("/api/v1/collections/$id") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("group_id", groupId?.let(::JsonPrimitive) ?: JsonNull)
            })
        }
    }

    suspend fun deleteCollection(id: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/collections/$id")
    }

    suspend fun getCollectionItems(
        id: String,
        offset: Int = 0,
        limit: Int = 40
    ): ApiResult<CatalogResponse> = safeApiCall {
        client.get("/api/v1/collections/$id/items") {
            parameter("offset", offset)
            parameter("limit", limit)
        }
    }

    suspend fun addItem(
        collectionId: String,
        itemId: String
    ): ApiResult<Unit> = safeApiCall {
        client.put("/api/v1/collections/$collectionId/items/$itemId")
    }

    suspend fun removeItem(
        collectionId: String,
        itemId: String
    ): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/collections/$collectionId/items/$itemId")
    }

    // --- Collection groups ---

    suspend fun createGroup(request: CreateCollectionGroupRequest): ApiResult<CollectionGroup> = safeApiCall {
        client.post("/api/v1/collections/groups") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateGroup(
        id: String,
        request: UpdateCollectionGroupRequest,
    ): ApiResult<CollectionGroup> = safeApiCall {
        client.put("/api/v1/collections/groups/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteGroup(id: String): ApiResult<Unit> = safeApiCall {
        client.delete("/api/v1/collections/groups/$id")
    }

    suspend fun reorderGroups(request: ReorderCollectionGroupsRequest): ApiResult<Unit> = safeApiCall {
        client.put("/api/v1/collections/groups/order") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun reorderCollections(request: ReorderCollectionsRequest): ApiResult<Unit> = safeApiCall {
        client.put("/api/v1/collections/order") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
