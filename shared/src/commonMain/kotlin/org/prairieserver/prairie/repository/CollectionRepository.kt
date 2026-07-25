package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.catalog.CatalogResponse
import org.prairieserver.prairie.model.personal.Collection
import org.prairieserver.prairie.model.personal.CollectionGroup
import org.prairieserver.prairie.model.personal.CollectionsResponse
import org.prairieserver.prairie.model.personal.CreateCollectionGroupRequest
import org.prairieserver.prairie.model.personal.CreateCollectionRequest
import org.prairieserver.prairie.model.personal.ReorderCollectionGroupsRequest
import org.prairieserver.prairie.model.personal.ReorderCollectionsRequest
import org.prairieserver.prairie.model.personal.UpdateCollectionGroupRequest
import org.prairieserver.prairie.model.personal.UpdateCollectionRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.CollectionApi

class CollectionRepository(
    private val collectionApi: CollectionApi,
) {
    /** Lists all collections and their groups for the current user. */
    suspend fun listCollections(): ApiResult<CollectionsResponse> =
        collectionApi.listCollections()

    /** Creates a new collection with the given name and optional type. */
    suspend fun createCollection(
        name: String,
        collectionType: String? = null,
    ): ApiResult<Collection> =
        collectionApi.createCollection(
            CreateCollectionRequest(name = name, collectionType = collectionType),
        )

    /** Updates an existing collection. */
    suspend fun updateCollection(
        id: String,
        request: UpdateCollectionRequest,
    ): ApiResult<Collection> =
        collectionApi.updateCollection(id, request)

    /** Deletes a collection by ID. */
    suspend fun deleteCollection(id: String): ApiResult<Unit> =
        collectionApi.deleteCollection(id)

    /** Lists items in a collection with pagination. */
    suspend fun getItems(
        collectionId: String,
        offset: Int = 0,
        limit: Int = 40,
    ): ApiResult<CatalogResponse> =
        collectionApi.getCollectionItems(collectionId, offset, limit)

    /** Adds an item to a collection. */
    suspend fun addItem(collectionId: String, itemId: String): ApiResult<Unit> =
        collectionApi.addItem(collectionId, itemId)

    /** Removes an item from a collection. */
    suspend fun removeItem(collectionId: String, itemId: String): ApiResult<Unit> =
        collectionApi.removeItem(collectionId, itemId)

    /** Moves a collection into a group (or to Ungrouped when [groupId] is null). */
    suspend fun moveCollectionToGroup(id: String, groupId: String?): ApiResult<Collection> =
        collectionApi.moveCollectionToGroup(id, groupId)

    // --- Groups ---

    suspend fun createGroup(name: String): ApiResult<CollectionGroup> =
        collectionApi.createGroup(CreateCollectionGroupRequest(name = name))

    suspend fun renameGroup(id: String, name: String): ApiResult<CollectionGroup> =
        collectionApi.updateGroup(id, UpdateCollectionGroupRequest(name = name))

    suspend fun deleteGroup(id: String): ApiResult<Unit> =
        collectionApi.deleteGroup(id)

    suspend fun reorderGroups(orderedIds: List<String>): ApiResult<Unit> =
        collectionApi.reorderGroups(ReorderCollectionGroupsRequest(orderedIds = orderedIds))

    suspend fun reorderCollections(orderedIds: List<String>, groupId: String? = null): ApiResult<Unit> =
        collectionApi.reorderCollections(
            ReorderCollectionsRequest(orderedIds = orderedIds, groupId = groupId)
        )
}
