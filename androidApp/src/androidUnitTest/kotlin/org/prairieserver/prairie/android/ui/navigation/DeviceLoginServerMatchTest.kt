package org.prairieserver.prairie.android.ui.navigation

import org.prairieserver.prairie.model.server.ServerEntry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A pairing code is only meaningful on the server that issued it. Looking it up
 * against whichever server happens to be active reports a valid request as
 * invalid; refusing it silently is just as bad a dead end. These pin the third
 * option — deliver it, and say which server it belongs to.
 */
class DeviceLoginServerMatchTest {

    private val serverA = ServerEntry(id = "a", url = "https://a.example", fetchedName = "Server A")
    private val serverB = ServerEntry(id = "b", url = "https://b.example", fetchedName = "Server B")
    private val entries = listOf(serverA, serverB)

    @Test
    fun `a link for the active server proceeds`() {
        assertEquals(
            DeviceLoginServerMatch.Active,
            deviceLoginServerMatch(
                requiredOrigin = "https://a.example",
                activeServerUrl = "https://a.example",
                entries = entries,
            ),
        )
    }

    @Test
    fun `a link for another configured server offers the switch`() {
        assertEquals(
            DeviceLoginServerMatch.SwitchRequired(serverB),
            deviceLoginServerMatch(
                requiredOrigin = "https://b.example",
                activeServerUrl = "https://a.example",
                entries = entries,
            ),
        )
    }

    @Test
    fun `a link for a server the user does not have says so`() {
        assertEquals(
            DeviceLoginServerMatch.UnknownServer("https://c.example"),
            deviceLoginServerMatch(
                requiredOrigin = "https://c.example",
                activeServerUrl = "https://a.example",
                entries = entries,
            ),
        )
    }

    /** An app-scheme link names no server, so it is about the active one. */
    @Test
    fun `a link naming no server proceeds`() {
        assertEquals(
            DeviceLoginServerMatch.Active,
            deviceLoginServerMatch(
                requiredOrigin = null,
                activeServerUrl = "https://a.example",
                entries = entries,
            ),
        )
    }

    /** Nothing to contradict yet — the user is on their way to adding one. */
    @Test
    fun `a link proceeds when no server is configured`() {
        assertEquals(
            DeviceLoginServerMatch.Active,
            deviceLoginServerMatch(
                requiredOrigin = "https://b.example",
                activeServerUrl = null,
                entries = emptyList(),
            ),
        )
    }

    /** Default ports must not make the same server look like a different one. */
    @Test
    fun `an explicit default port still matches`() {
        assertEquals(
            DeviceLoginServerMatch.Active,
            deviceLoginServerMatch(
                requiredOrigin = "https://a.example:443",
                activeServerUrl = "https://a.example",
                entries = entries,
            ),
        )
    }
}
