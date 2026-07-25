package org.prairieserver.prairie.tv.push

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class TvPushSurfaceSourceTest {
    @Test
    fun tvDoesNotExposePushSetupSurface() {
        val files = File("src/androidMain/kotlin/org/prairieserver/prairie/tv").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(files.contains("PushNotificationSetup"))
        assertFalse(files.contains("FirebaseMessagingService"))
    }
}
