package com.continuum.app.android.ui.navigation

import kotlin.test.Test
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
    fun customContinuumCodeUrlRoutesToPairDevice() {
        assertEquals(
            "pair_device?code=ABCD-1234",
            deviceLoginPairRouteOrNull("continuum://device?code=ABCD-1234"),
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
}
