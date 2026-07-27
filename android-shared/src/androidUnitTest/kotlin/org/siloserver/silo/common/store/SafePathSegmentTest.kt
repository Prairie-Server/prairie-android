package org.siloserver.silo.common.store

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafePathSegmentTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `safe identity segment stays unchanged`() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"

        assertEquals(uuid, safePathSegment(uuid))
    }

    @Test
    fun `unsafe identity segment is encoded without path separators`() {
        val encoded = safePathSegment("../other")

        assertEquals("~Li4vb3RoZXI", encoded)
        assertNotEquals("../other", encoded)
        assertTrue(encoded.startsWith("~"))
        assertTrue('/' !in encoded)
        assertTrue('\\' !in encoded)
    }

    @Test
    fun `dot segments are encoded`() {
        assertNotEquals(".", safePathSegment("."))
        assertNotEquals("..", safePathSegment(".."))
    }

    @Test
    fun `overlong encoded segment is bounded and deterministic`() {
        val value = " unsafe/".repeat(100)

        assertEquals(safePathSegment(value), safePathSegment(value))
        assertTrue(safePathSegment(value).length <= 120)
    }

    @Test
    fun `contained child rejects traversal input`() {
        val root = tmp.newFolder("root")

        assertNull(containedChild(root, "../other"))
        assertNull(containedChild(root, ".."))
    }

    @Test
    fun `contained child encodes non traversal unsafe input under root`() {
        val root = tmp.newFolder("root").canonicalFile
        val child = containedChild(root, "server id", "profile%id")

        assertTrue(child != null)
        assertTrue(child.canonicalPath.startsWith(root.canonicalPath + java.io.File.separator))
        assertTrue("server id" !in child.path)
        assertTrue("profile%id" !in child.path)
    }

    @Test
    fun `reserved encoded namespace is never probed as a raw legacy segment`() {
        val root = tmp.newFolder("root")

        assertNull(containedLegacyChild(root, "~Pw"))
        assertNull(containedLegacyChild(root, "server", "~Pw.json"))
    }
}
