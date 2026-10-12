package org.prairieserver.prairie.network.apiv2

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.prairieserver.prairie.model.catalog.BrowseItem
import org.prairieserver.prairie.model.catalog.Season
import org.prairieserver.prairie.model.catalog.hasMachineTranslatedOverview
import org.prairieserver.prairie.model.catalog.pendingEpisodeTranslationLanguage
import org.prairieserver.prairie.network.PrairieJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MachineTranslationDecodingTest {
    private fun obj(json: String): JsonObject = PrairieJson.parseToJsonElement(json).jsonObject

    @Test
    fun detailCarriesMachineTranslatedFieldsAndPendingLanguage() {
        val detail = PrairieJson.decodeFromString<ItemDetailReadV2>(
            """{"content_id":"m1","type":"movie","title":"Film","overview":"Tekst","tagline":"Regel",
               "pending_translation_language":"nl","machine_translated_fields":["overview","tagline"],
               "cast":[],"crew":[],"versions":[],"subtitles":[]}""",
        ).toDomain()

        assertEquals(listOf("overview", "tagline"), detail.machineTranslatedFields)
        assertEquals("nl", detail.pendingTranslationLanguage)
        assertTrue(hasMachineTranslatedOverview(detail.machineTranslatedFields))
    }

    @Test
    fun absentOrNullFieldsDecodeAsNotTranslated() {
        val absent = PrairieJson.decodeFromString<ItemDetailReadV2>(
            """{"content_id":"m1","type":"movie","title":"Film","cast":[],"crew":[],"versions":[],"subtitles":[]}""",
        ).toDomain()
        val nulled = PrairieJson.decodeFromString<ItemDetailReadV2>(
            """{"content_id":"m1","type":"movie","title":"Film","machine_translated_fields":null,
               "cast":[],"crew":[],"versions":[],"subtitles":[]}""",
        ).toDomain()

        assertEquals(emptyList(), absent.machineTranslatedFields)
        assertEquals(emptyList(), nulled.machineTranslatedFields)
        assertFalse(hasMachineTranslatedOverview(absent.machineTranslatedFields))
    }

    @Test
    fun episodeRowsCarryBothFields() {
        val rows = listOf(
            """{"content_id":"e1","season_number":1,"episode_number":1,"overview":"Vertaald","machine_translated_fields":["overview"]}""",
            """{"content_id":"e2","season_number":1,"episode_number":2,"overview":"Original","pending_translation_language":"nl"}""",
        ).map { PrairieJson.decodeFromString<EpisodeListItemReadV2>(it).toDomain() }

        assertEquals(listOf("overview"), rows[0].machineTranslatedFields)
        assertNull(rows[0].pendingTranslationLanguage)
        assertEquals("nl", rows[1].pendingTranslationLanguage)
        assertEquals(emptyList(), rows[1].machineTranslatedFields)
        assertEquals("nl", rows.pendingEpisodeTranslationLanguage())
        assertNull(rows.take(1).pendingEpisodeTranslationLanguage())
    }

    @Test
    fun seasonRowsCarryMachineTranslatedFields() {
        val season = PrairieJson.decodeFromString<Season>(
            """{"content_id":"s1","season_number":1,"overview":"Vertaald","machine_translated_fields":["overview"]}""",
        )

        assertEquals(listOf("overview"), season.machineTranslatedFields)
    }

    @Test
    fun sectionCardsCarryBothFields() {
        val section = decodeSectionV2(
            obj(
                """{"id":"featured","section_type":"featured","title":"Featured","items":[
                    {"content_id":"m1","type":"movie","title":"Film","overview":"Vertaald","machine_translated_fields":["overview"]},
                    {"content_id":"m2","type":"movie","title":"Other","overview":"Original","pending_translation_language":"nl"}
                ]}""",
            ),
        )

        assertEquals(listOf("overview"), section.items[0].machineTranslatedFields)
        assertNull(section.items[0].pendingTranslationLanguage)
        assertEquals("nl", section.items[1].pendingTranslationLanguage)
    }

    @Test
    fun catalogCardsCarryBothFields() {
        val card = PrairieJson.decodeFromString<BrowseItem>(
            """{"content_id":"m1","type":"movie","title":"Film","pending_translation_language":"nl","machine_translated_fields":["tagline"]}""",
        )

        assertEquals("nl", card.pendingTranslationLanguage)
        assertEquals(listOf("tagline"), card.machineTranslatedFields)
    }

    @Test
    fun anAiTaglineAloneDoesNotMarkAProviderOverview() {
        val detail = PrairieJson.decodeFromString<ItemDetailReadV2>(
            """{"content_id":"m1","type":"movie","title":"Film","overview":"Anbieter: Text","tagline":"[German] Line",
               "machine_translated_fields":["tagline"],"cast":[],"crew":[],"versions":[],"subtitles":[]}""",
        ).toDomain()

        // Overview-only surfaces (detail heroes, episode rows, marquee, cards).
        assertFalse(hasMachineTranslatedOverview(detail.machineTranslatedFields))
    }
}
