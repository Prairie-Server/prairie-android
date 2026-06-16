package com.continuum.app.common.player.backend

/**
 * Provisional device-class floor for enabling the MPV backend under Auto.
 * Conservative by design: refined by the Phase-1 Track-A device matrix
 * (docs/superpowers/notes/2026-06-16-track-a-device-matrix-findings.md).
 * Pure (primitive inputs) so it is unit-testable without Android; production
 * call sites pass [android.os.Build.VERSION.SDK_INT] and
 * [android.os.Build.SUPPORTED_ABIS].
 */
object MpvDeviceFloor {
    /** Provisional minimum SDK for MPV; the matrix may lower this toward 24. */
    const val MIN_SDK_FOR_MPV = 26

    fun isMpvSupported(sdkInt: Int, supportedAbis: List<String>): Boolean {
        if (sdkInt < MIN_SDK_FOR_MPV) return false
        // Require a 64-bit ABI for the initial rollout; ARMv7-only TV boxes are
        // revisited after the device matrix proves the native libs there.
        return supportedAbis.any { it == "arm64-v8a" || it == "x86_64" }
    }
}
