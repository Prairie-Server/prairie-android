package org.prairieserver.prairie.model.personal

import org.prairieserver.prairie.network.PrairieJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalModelsSerializationTest {
    @Test
    fun roundTripsCollectionGraph() {
        val collection = Collection(
            id = "c1",
            name = "Favs",
            description = "d",
            collectionType = "manual",
            isShared = true,
            sortOrder = 2,
            groupId = "g1",
            itemCount = 3,
        )
        val group = CollectionGroup(id = "g1", name = "G", sortOrder = 1)
        val response = CollectionsResponse(collections = listOf(collection), groups = listOf(group))
        val encoded = PrairieJson.encodeToString(response)
        val decoded = PrairieJson.decodeFromString<CollectionsResponse>(encoded)
        assertEquals(collection, decoded.collections.single())
        assertEquals(group, decoded.groups.single())

        val create = CreateCollectionRequest(name = "N", collectionType = "smart")
        assertTrue(PrairieJson.encodeToString(create).contains("smart"))
        val update = UpdateCollectionRequest(name = "N2", includeInServerCollections = true)
        assertTrue(PrairieJson.encodeToString(update).contains("include_in_server_collections"))
        assertTrue(PrairieJson.encodeToString(CreateCollectionGroupRequest(name = "G")).contains("G"))
        assertTrue(PrairieJson.encodeToString(UpdateCollectionGroupRequest(name = "G2")).contains("G2"))
        assertTrue(PrairieJson.encodeToString(ReorderCollectionsRequest(listOf("c1"), "g1")).contains("ordered_ids"))
        assertTrue(PrairieJson.encodeToString(ReorderCollectionGroupsRequest(listOf("g1"))).contains("g1"))
        val item = CollectionItem(collectionId = "c1", mediaItemId = "m1", position = 4)
        assertEquals("m1", PrairieJson.decodeFromString<CollectionItem>(PrairieJson.encodeToString(item)).mediaItemId)
    }
}
