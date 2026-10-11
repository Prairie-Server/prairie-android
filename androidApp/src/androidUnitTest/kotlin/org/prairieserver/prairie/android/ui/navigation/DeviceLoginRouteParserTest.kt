package org.prairieserver.prairie.android.ui.navigation

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceLoginRouteParserTest {

    @Test
    fun customPrairieTokenUrlRoutesToPairDevice() {
        assertEquals(
            "pair_device?token=t1",
            deviceLoginPairRouteOrNull("prairie://device?token=t1"),
        )
    }

    @Test
    fun customPrairieCodeUrlRoutesToPairDevice() {
        assertEquals(
            "pair_device?code=ABCD-1234",
            deviceLoginPairRouteOrNull("prairie://device?code=ABCD-1234"),
        )
    }

    @Test
    fun serverHttpsDeviceTokenUrlRoutesToPairDevice() {
        // The issuing origin rides along so the pairing screen can refuse a
        // code that belongs to a server the user is not currently on.
        assertEquals(
            "pair_device?token=t1&serverOrigin=https%3A%2F%2Fprairie.example",
            deviceLoginPairRouteOrNull("https://prairie.example/device?token=t1"),
        )
    }

    @Test
    fun serverHttpsAuthDeviceCodeUrlRoutesToPairDevice() {
        assertEquals(
            "pair_device?code=ABCD&serverOrigin=https%3A%2F%2Fprairie.example",
            deviceLoginPairRouteOrNull("https://prairie.example/auth/device?code=ABCD"),
        )
    }

    @Test
    fun tokenWinsWhenBothTokenAndCodeExist() {
        assertEquals(
            "pair_device?token=t1",
            deviceLoginPairRouteOrNull("prairie://device?token=t1&code=ABCD"),
        )
    }

    @Test
    fun unrelatedUrlReturnsNull() {
        assertNull(deviceLoginPairRouteOrNull("https://prairie.example/item/abc"))
    }

    @Test
    fun blankOrMissingValuesReturnNull() {
        assertNull(deviceLoginPairRouteOrNull(null))
        assertNull(deviceLoginPairRouteOrNull(""))
        assertNull(deviceLoginPairRouteOrNull("prairie://device?token=&code="))
    }

    // --- origin ---

    @Test
    fun `an app scheme device link names no server`() {
        assertEquals(DeviceLoginScope.Unscoped, deviceLoginScope("prairie://device?code=ABCD"))
        // No origin in the route either.
        assertEquals("pair_device?code=ABCD", deviceLoginPairRouteOrNull("prairie://device?code=ABCD"))
    }

    @Test
    fun `an https device link carries its issuing origin`() {
        assertEquals(
            DeviceLoginScope.Origin("https://server-b.example"),
            deviceLoginScope("https://server-b.example/device?code=ABCD"),
        )
    }

    /**
     * A device-SHAPED link whose origin cannot be read must not parse at all.
     * It used to produce a route with no origin, which downstream reads as
     * "names no server" and pairs against whichever server is active — the
     * exact bypass the origin check exists to stop.
     */
    @Test
    fun `an https device link with no host does not parse`() {
        assertEquals(DeviceLoginScope.Invalid, deviceLoginScope("https:///device?code=ABCD"))
        assertNull(deviceLoginPairRouteOrNull("https:///device?code=ABCD"))
    }

    /** Port 0 is not a valid origin, so the link is not usable either. */
    @Test
    fun `a device link with an invalid port does not parse`() {
        assertEquals(
            DeviceLoginScope.Invalid,
            deviceLoginScope("https://prairie.example:0/device?code=A"),
        )
        assertNull(deviceLoginPairRouteOrNull("https://prairie.example:0/device?code=A"))
    }

    @Test
    fun `an app link without server is scoped by its url origin`() {
        assertEquals(
            "pair_device?code=48217730&serverOrigin=https%3A%2F%2Fhome.example%3A8443",
            deviceLoginPairRouteOrNull("prairie://device?url=https%3A%2F%2FHome.example%3A8443%2F&code=48217730"),
        )
        // The default port normalizes away, as for an https link.
        assertEquals(
            "pair_device?code=48217730&serverOrigin=https%3A%2F%2Fhome.example",
            deviceLoginPairRouteOrNull("prairie://device?url=https%3A%2F%2Fhome.example%3A443&code=48217730"),
        )
    }

    @Test
    fun `an app link without server ignores an unreadable url`() {
        assertEquals(
            "pair_device?code=48217730",
            deviceLoginPairRouteOrNull("prairie://device?url=javascript%3Aalert(1)&code=48217730"),
        )
        assertEquals(
            "pair_device?code=48217730",
            deviceLoginPairRouteOrNull("prairie://device?url=https%3A%2F%2F&code=48217730"),
        )
    }
}
