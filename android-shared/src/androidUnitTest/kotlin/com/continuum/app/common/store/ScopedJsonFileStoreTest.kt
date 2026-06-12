package com.continuum.app.common.store

import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Payload(val name: String, val count: Int)

class ScopedJsonFileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `write then read round trips and creates scoped directories`() {
        val store = ScopedJsonFileStore(tmp.newFolder("root"), tag = "Test")
        val file = store.fileFor("srv", "prof", "content")

        store.write(file, Payload(name = "a", count = 1))

        assertTrue(file.path.endsWith("srv/prof/content.json"))
        assertEquals(Payload(name = "a", count = 1), store.read<Payload>(file))
    }

    @Test
    fun `read returns null for missing or corrupt files`() {
        val store = ScopedJsonFileStore(tmp.newFolder("root"), tag = "Test")
        val file = store.fileFor("srv", "prof", "content")

        assertNull(store.read<Payload>(file))

        file.parentFile?.mkdirs()
        file.writeText("{ not json")
        assertNull(store.read<Payload>(file))
    }

    @Test
    fun `atomic write replaces existing content and leaves no tmp file`() {
        val store = ScopedJsonFileStore(tmp.newFolder("root"), tag = "Test")
        val file = store.fileFor("srv", "prof", "content")

        store.write(file, Payload(name = "a", count = 1))
        store.write(file, Payload(name = "b", count = 2))

        assertEquals(Payload(name = "b", count = 2), store.read<Payload>(file))
        assertFalse(File(file.parentFile, "${file.name}.tmp").exists())
    }
}
