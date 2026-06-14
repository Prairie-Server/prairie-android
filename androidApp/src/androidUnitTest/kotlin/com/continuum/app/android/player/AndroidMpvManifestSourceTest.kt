package com.continuum.app.android.player

import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidMpvManifestSourceTest {
    @Test
    fun mobileManifestOverridesMpvMinSdkBecauseServiceRuntimeGatesMpv() {
        val manifest = java.io.File("src/androidMain/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("xmlns:tools=\"http://schemas.android.com/tools\""))
        assertTrue(manifest.contains("tools:overrideLibrary=\"dev.jdtech.mpv\""))
    }

    @Test
    fun mobilePlaybackServiceDocumentsIntentionalMediaSessionExport() {
        val manifest = java.io.File("src/androidMain/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("androidx.media3.session.MediaSessionService"))
        assertTrue(manifest.contains("tools:ignore=\"ExportedService\""))
    }
}
