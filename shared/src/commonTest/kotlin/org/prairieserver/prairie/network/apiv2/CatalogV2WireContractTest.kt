package org.prairieserver.prairie.network.apiv2

import kotlinx.serialization.decodeFromString
import org.prairieserver.prairie.network.PrairieJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogV2WireContractTest {
    @Test
    fun libraryCollectionTabDecodesGroupsAndUngroupedCards() {
        val tab = PrairieJson.decodeFromString<LibraryCollectionTabV2>(
            """
            {
              "library_id": "lib-1",
              "collections": [{
                "id": "c1", "title": "Noir", "library_id": "lib-1", "library_ids": ["lib-1", "lib-2"],
                "collection_type": "manual", "poster_url": "/p/c1.jpg", "item_count": 12, "sort_order": 3,
                "group_id": "g1"
              }],
              "groups": [{
                "id": "g1", "name": "Genres", "kind": "custom", "sort_mode": "manual", "sort_order": 0,
                "collections": [{"id": "c1", "title": "Noir", "poster_url": "/p/c1.jpg", "item_count": 12, "featured": true}]
              }],
              "ungrouped": {"sort_order": 1, "collections": [
                {"id": "c2", "title": "Kids", "poster_url": "/p/c2.jpg", "item_count": 4, "creator_profile_id": "prof"}
              ]},
              "future_field": true
            }
            """.trimIndent(),
        )
        assertEquals("lib-1", tab.libraryId)
        val curated = tab.collections.single()
        assertEquals(listOf("lib-1", "lib-2"), curated.libraryIds)
        assertEquals("g1", curated.groupId)
        assertNull(curated.posterThumbhash)
        val group = tab.groups.single()
        assertEquals("manual", group.sortMode)
        assertTrue(group.collections.single().featured)
        val ungrouped = tab.ungrouped!!
        assertEquals(1, ungrouped.sortOrder)
        assertFalse(ungrouped.collections.single().featured)
        assertEquals("prof", ungrouped.collections.single().creatorProfileId)
    }

    @Test
    fun filtersKeepTechnicalAxesOptional() {
        val filters = PrairieJson.decodeFromString<CatalogFiltersV2>(
            """
            {"genres":["Drama"],"studios":[],"networks":["HBO"],"countries":["US"],
             "original_languages":["en"],"content_ratings":["TV-MA"],"authors":[],"narrators":[],"series":[],
             "technical":{"resolutions":["2160p"],"audio_languages":["en","ja"],"subtitle_languages":["en"]}}
            """.trimIndent(),
        )
        assertEquals(listOf("TV-MA"), filters.contentRatings)
        assertEquals(listOf("en", "ja"), filters.technical?.audioLanguages)
        val bare = PrairieJson.decodeFromString<CatalogFiltersV2>(
            """{"genres":[],"studios":[],"networks":[],"countries":[],"original_languages":[],"content_ratings":[],"authors":[],"narrators":[],"series":[]}""",
        )
        assertNull(bare.technical)
    }

    @Test
    fun searchDiagnosticsAndCapabilitiesDecodeWithDefaults() {
        val diagnostics = PrairieJson.decodeFromString<CatalogSearchDiagnosticsV2>(
            """{"provider":"meili","mode":"hybrid","semantic_used":false,"fallback_reason":"embedder_offline"}""",
        )
        assertFalse(diagnostics.semanticUsed)
        assertEquals("embedder_offline", diagnostics.fallbackReason)
        assertNull(diagnostics.resultWindowLimit)

        val caps = PrairieJson.decodeFromString<CatalogSearchCapabilitiesV2>(
            """{"revision":"3","state":"ready","provider":"meili","session_ttl_seconds":600}""",
        )
        assertEquals("ready", caps.state)
        assertFalse(caps.peopleMediaScope)
        assertEquals(600, caps.sessionTTLSeconds)
    }
}
