package org.prairieserver.prairie.playback

import kotlinx.serialization.encodeToString
import org.prairieserver.prairie.model.catalog.FileVersion
import org.prairieserver.prairie.network.PrairieJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrickplayTest {
    private fun trickplay(
        interval: Double = 10.0,
        columns: Int = 10,
        rows: Int = 10,
        count: Int = 100,
        width: Int = 320,
        height: Int = 180,
        sheets: List<TrickplaySheet> = listOf(
            TrickplaySheet(0, "https://cdn.example/sheet0.jpg"),
            TrickplaySheet(1, "https://cdn.example/sheet1.jpg"),
        ),
    ) = TrickplayInfo(
        intervalSeconds = interval,
        width = width,
        height = height,
        tileColumns = columns,
        tileRows = rows,
        thumbnailCount = count,
        sheets = sheets,
    )

    @Test
    fun `resolveTrickplayTile returns null when absent`() {
        assertNull(resolveTrickplayTile(null, 30.0))
        assertNull(resolveTrickplayTile(trickplay(count = 0), 30.0))
        assertNull(resolveTrickplayTile(trickplay(sheets = emptyList()), 30.0))
    }

    @Test
    fun `resolveTrickplayTile picks first tile at t0`() {
        val tile = resolveTrickplayTile(trickplay(), 0.0)!!
        assertEquals("https://cdn.example/sheet0.jpg", tile.url)
        assertEquals(0, tile.col)
        assertEquals(0, tile.row)
        assertEquals(0f, tile.backgroundPositionXPercent)
        assertEquals(0f, tile.backgroundPositionYPercent)
        assertEquals(10, tile.columns)
        assertEquals(10, tile.rows)
        assertEquals(320, tile.width)
        assertEquals(180, tile.height)
    }

    @Test
    fun `resolveTrickplayTile uses interval columns and sheet index`() {
        // tile 15 → sheet 0, local 15 → col 5, row 1
        val tile = resolveTrickplayTile(trickplay(), 150.0)!!
        assertEquals("https://cdn.example/sheet0.jpg", tile.url)
        assertEquals(5, tile.col)
        assertEquals(1, tile.row)
        assertEquals((5f / 9f) * 100f, tile.backgroundPositionXPercent, absoluteTolerance = 0.01f)
        assertEquals((1f / 9f) * 100f, tile.backgroundPositionYPercent, absoluteTolerance = 0.01f)

        // tile 100 would be sheet 1; clamp to thumbnail_count-1 = 99 → sheet 0
        // with count=100, tilesPerSheet=100, tile 99 is still sheet 0
        val lastOnFirst = resolveTrickplayTile(trickplay(count = 100), 9999.0)!!
        assertEquals("https://cdn.example/sheet0.jpg", lastOnFirst.url)

        // With 50 tiles per sheet (5x10), tile 55 → sheet 1
        val nextSheet = resolveTrickplayTile(
            trickplay(columns = 5, rows = 10, count = 200),
            550.0, // floor(550/10)=55
        )!!
        assertEquals("https://cdn.example/sheet1.jpg", nextSheet.url)
        assertEquals(0, nextSheet.col) // 55 % 50 = 5; 5 % 5 = 0
        assertEquals(1, nextSheet.row) // 5 / 5 = 1
    }

    @Test
    fun `resolveTrickplayTile applies defaults for missing geometry`() {
        val tile = resolveTrickplayTile(
            trickplay(
                interval = 0.0,
                columns = 0,
                rows = 0,
                width = 0,
                height = 0,
                count = 20,
                sheets = listOf(TrickplaySheet(0, "https://cdn.example/sheet0.jpg")),
            ),
            25.0, // floor(25/10)=2 with default interval
        )!!
        assertEquals(10, tile.columns)
        assertEquals(10, tile.rows)
        assertEquals(320, tile.width)
        assertEquals(180, tile.height) // round(320 * 9/16)
        assertEquals(2, tile.col)
        assertEquals(0, tile.row)
    }

    @Test
    fun `resolveTrickplayTile zeroes background percent for single column or row`() {
        val tile = resolveTrickplayTile(
            trickplay(
                columns = 1,
                rows = 1,
                count = 1,
                sheets = listOf(TrickplaySheet(0, "https://cdn.example/one.jpg")),
            ),
            0.0,
        )!!
        assertEquals(0f, tile.backgroundPositionXPercent)
        assertEquals(0f, tile.backgroundPositionYPercent)
        assertEquals(0, tile.col)
        assertEquals(0, tile.row)
    }

    @Test
    fun `resolveTrickplayTile clamps negative scrub time to first tile`() {
        val tile = resolveTrickplayTile(trickplay(), -5.0)!!
        assertEquals(0, tile.col)
        assertEquals(0, tile.row)
    }

    @Test
    fun `resolveTrickplayTile returns null for missing sheet url`() {
        assertNull(
            resolveTrickplayTile(
                trickplay(sheets = listOf(TrickplaySheet(0, ""))),
                0.0,
            ),
        )
        assertNull(
            resolveTrickplayTile(
                trickplay(sheets = listOf(TrickplaySheet(2, "https://cdn.example/sheet2.jpg"))),
                0.0,
            ),
        )
    }

    @Test
    fun `TrickplayInfo round-trips on FileVersion`() {
        val info = TrickplayInfo(
            intervalSeconds = 10.0,
            width = 320,
            height = 180,
            tileColumns = 10,
            tileRows = 10,
            thumbnailCount = 50,
            sheets = listOf(TrickplaySheet(0, "/api/v1/trickplay/sheet0.jpg")),
        )
        val encoded = PrairieJson.encodeToString(
            FileVersion(fileId = 7, trickplay = info),
        )
        assertTrue("trickplay" in encoded)
        assertTrue("interval_seconds" in encoded)
        assertTrue("tile_columns" in encoded)
        val decoded = PrairieJson.decodeFromString<FileVersion>(encoded)
        assertEquals(info, decoded.trickplay)

        val without = PrairieJson.decodeFromString<FileVersion>("""{"file_id":7}""")
        assertNull(without.trickplay)
    }
}
