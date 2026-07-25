package org.prairieserver.prairie.android.ui.navigation

import kotlin.test.Test
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
        assertEquals(
            "pair_device?token=t1",
            deviceLoginPairRouteOrNull("https://prairie.example/device?token=t1"),
        )
    }

    @Test
    fun serverHttpsAuthDeviceCodeUrlRoutesToPairDevice() {
        assertEquals(
            "pair_device?code=ABCD",
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
}
