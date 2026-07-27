package org.siloserver.silo.common.store

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerScopedJsonStatePurgerTest {

    @Test
    fun `orphan symlink is removed without touching its target`() {
        val base = Files.createTempDirectory("silo-json-purge").toFile()
        val external = Files.createTempDirectory("silo-json-external")
        val sentinel = external.resolve("keep.txt")
        Files.write(sentinel, "keep".toByteArray())
        val root = base.toPath().resolve("ebook_state")
        Files.createDirectories(root)
        val link = root.resolve("orphan-server")
        Files.createSymbolicLink(link, external)

        ServerScopedJsonStatePurger(base).deleteJsonOnlyOrphans(
            registeredServerIds = emptySet(),
            protectedServerIds = emptySet(),
        )

        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertTrue(Files.exists(sentinel), "purge must not follow a server-directory symlink")
    }
}
