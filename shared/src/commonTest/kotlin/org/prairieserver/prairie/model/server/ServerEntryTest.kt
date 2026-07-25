package org.prairieserver.prairie.model.server

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerEntryTest {
    @Test
    fun displayNamePrefersOverrideThenFetchedThenHost() {
        assertEquals(
            "Home",
            ServerEntry(id = "1", url = "https://a.example:8090/x", userOverrideName = "Home", fetchedName = "Srv").displayName,
        )
        assertEquals(
            "Srv",
            ServerEntry(id = "1", url = "https://a.example:8090/x", fetchedName = "Srv").displayName,
        )
        assertEquals(
            "a.example:8090",
            ServerEntry(id = "1", url = "https://a.example:8090/x").displayName,
        )
        assertEquals(
            "bare",
            ServerEntry(id = "1", url = "bare").displayName,
        )
    }
}
