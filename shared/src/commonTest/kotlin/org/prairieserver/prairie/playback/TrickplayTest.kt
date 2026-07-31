package org.prairieserver.prairie.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrickplayTest {
    private fun trickplay(
        interval: Double = 10.0,
        columns: Int = 10,
        rows: Int = 10,
        count: Int = 100,
        sheets: List<TrickplaySheet> = listOf(
            TrickplaySheet(0, "https://cdn.example/sheet0.jpg"),
            TrickplaySheet(1, "https://cdn.example/sheet1.jpg"),
        ),
    ) = TrickplayInfo(
        intervalSeconds = interval,
        width = 320,
        height = 180,
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
    }

    @Test
    fun `resolveTrickplayTile uses interval columns and sheet index`() {
        // tile 15 → sheet 0, local 15 → col 5, row 1
        val tile = resolveTrickplayTile(trickplay(), 150.0)!!
        assertEquals("https://cdn.example/sheet0.jpg", tile.url)
        assertEquals(5, tile.col)
        assertEquals(1, tile.row)
        assertEquals((5f / 9f) * 100f, tile.backgroundPositionXPercent, 0.01f)
        assertEquals((1f / 9f) * 100f, tile.backgroundPositionYPercent, 0.01f)

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
}
