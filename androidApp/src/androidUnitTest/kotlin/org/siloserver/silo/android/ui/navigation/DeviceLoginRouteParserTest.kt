package org.siloserver.silo.android.ui.navigation

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceLoginRouteParserTest {

    @Test
    fun customSiloTokenUrlRoutesToPairDevice() {
        assertEquals(
            "pair_device?token=t1",
            deviceLoginPairRouteOrNull("silo://device?token=t1"),
        )
    }

    @Test
    fun customSiloCodeUrlRoutesToPairDevice() {
        assertEquals(
            "pair_device?code=ABCD-1234",
            deviceLoginPairRouteOrNull("silo://device?code=ABCD-1234"),
        )
    }

    @Test
    fun serverHttpsDeviceTokenUrlRoutesToPairDevice() {
        assertEquals(
            "pair_device?token=t1",
            deviceLoginPairRouteOrNull("https://silo.example/device?token=t1"),
        )
    }

    @Test
    fun serverHttpsAuthDeviceCodeUrlRoutesToPairDevice() {
        assertEquals(
            "pair_device?code=ABCD",
            deviceLoginPairRouteOrNull("https://silo.example/auth/device?code=ABCD"),
        )
    }

    @Test
    fun tokenWinsWhenBothTokenAndCodeExist() {
        assertEquals(
            "pair_device?token=t1",
            deviceLoginPairRouteOrNull("silo://device?token=t1&code=ABCD"),
        )
    }

    @Test
    fun unrelatedUrlReturnsNull() {
        assertNull(deviceLoginPairRouteOrNull("https://silo.example/item/abc"))
    }

    @Test
    fun blankOrMissingValuesReturnNull() {
        assertNull(deviceLoginPairRouteOrNull(null))
        assertNull(deviceLoginPairRouteOrNull(""))
        assertNull(deviceLoginPairRouteOrNull("silo://device?token=&code="))
    }

    // --- origin matching ---

    /**
     * An HTTPS device link names the server that issued the pairing request.
     * That origin used to be dropped, so the code was looked up against
     * whichever server happened to be active — normally reporting a perfectly
     * valid request as invalid, and at worst approving against the wrong
     * server.
     */
    @Test
    fun `an https device link for another server is not for the active one`() {
        assertFalse(
            deviceLoginLinkIsForActiveServer(
                rawUri = "https://server-b.example/auth/device?code=ABCD",
                activeServerUrl = "https://server-a.example",
            ),
        )
    }

    @Test
    fun `an https device link for the active server is accepted`() {
        assertTrue(
            deviceLoginLinkIsForActiveServer(
                rawUri = "https://server-a.example/auth/device?code=ABCD",
                activeServerUrl = "https://server-a.example",
            ),
        )
    }

    @Test
    fun `a port difference is a different origin`() {
        assertFalse(
            deviceLoginLinkIsForActiveServer(
                rawUri = "https://silo.example:8443/device?code=ABCD",
                activeServerUrl = "https://silo.example",
            ),
        )
    }

    @Test
    fun `matching host and port is the same origin`() {
        assertTrue(
            deviceLoginLinkIsForActiveServer(
                rawUri = "https://silo.example:8443/device?code=ABCD",
                activeServerUrl = "https://silo.example:8443/",
            ),
        )
    }

    /** An app-scheme link names no server, so it is about the active one. */
    @Test
    fun `an app scheme device link names no server`() {
        assertTrue(
            deviceLoginLinkIsForActiveServer(
                rawUri = "silo://device?code=ABCD",
                activeServerUrl = "https://server-a.example",
            ),
        )
        assertEquals(DeviceLoginScope.Unscoped, deviceLoginScope("silo://device?code=ABCD"))
    }

    /**
     * With no server configured there is nothing to contradict, and the user is
     * about to set one up — almost certainly the one they just scanned. Dropping
     * the link here would turn a valid action into silence.
     */
    @Test
    fun `an https device link with no active server is allowed through setup`() {
        assertTrue(
            deviceLoginLinkIsForActiveServer(
                rawUri = "https://server-b.example/device?code=ABCD",
                activeServerUrl = null,
            ),
        )
    }

    /** Default ports are equivalent to omitting them. */
    @Test
    fun `an explicit default port is the same origin`() {
        assertTrue(
            deviceLoginLinkIsForActiveServer(
                rawUri = "https://silo.example:443/device?code=ABCD",
                activeServerUrl = "https://silo.example",
            ),
        )
    }

    /**
     * A device-shaped link with no readable host must not fall back to "use
     * whichever server is active" — that is the behaviour being removed.
     */
    @Test
    fun `an https device link with no host is refused`() {
        assertEquals(DeviceLoginScope.Invalid, deviceLoginScope("https:///device?code=ABCD"))
        assertFalse(
            deviceLoginLinkIsForActiveServer(
                rawUri = "https:///device?code=ABCD",
                activeServerUrl = "https://server-a.example",
            ),
        )
    }

    /** Port 0 is not a valid origin and must not pass as "default port". */
    @Test
    fun `port zero is not an origin`() {
        assertEquals(DeviceLoginScope.Invalid, deviceLoginScope("https://silo.example:0/device?code=A"))
        assertFalse(
            deviceLoginLinkIsForActiveServer(
                rawUri = "https://silo.example:0/device?code=A",
                activeServerUrl = "https://silo.example",
            ),
        )
    }

    /**
     * The origin has to survive the wait through setup/login: a pairing request
     * queued with no server configured must not fire against whatever server
     * the user happens to end up on.
     */
    @Test
    fun `a required origin only matches its own server`() {
        val origin = deviceLoginRequiredOrigin("https://server-b.example/device?code=A")
        assertEquals("https://server-b.example", origin)
        assertTrue(deviceLoginOriginMatchesServer(origin!!, "https://server-b.example"))
        assertFalse(deviceLoginOriginMatchesServer(origin, "https://server-a.example"))
        assertFalse(deviceLoginOriginMatchesServer(origin, null))
    }

    @Test
    fun `an app scheme link requires no origin`() {
        assertNull(deviceLoginRequiredOrigin("silo://device?code=A"))
    }
}
