package org.siloserver.silo.common.player.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MpvDeviceFloorTest {
    @Test
    fun supportedOnModern64BitDevice() {
        assertTrue(MpvDeviceFloor.isMpvSupported(sdkInt = 30, supportedAbis = listOf("arm64-v8a")))
    }

    @Test
    fun unsupportedBelowMinSdk() {
        assertFalse(MpvDeviceFloor.isMpvSupported(sdkInt = 24, supportedAbis = listOf("arm64-v8a")))
    }

    @Test
    fun unsupportedOn32BitOnlyDevice() {
        assertFalse(MpvDeviceFloor.isMpvSupported(sdkInt = 30, supportedAbis = listOf("armeabi-v7a")))
    }
}
