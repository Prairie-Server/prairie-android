package org.siloserver.silo.android.push

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidPushSourceTest {
    @Test
    fun phonePushHasTokenProviderMessagingServiceAndGenericPresenter() {
        val provider = File("src/androidMain/kotlin/org/siloserver/silo/android/push/AndroidPushTokenProvider.kt")
            .takeIf { it.exists() }
            ?.readText()
            .orEmpty()
        val service = File("src/androidMain/kotlin/org/siloserver/silo/android/push/SiloFirebaseMessagingService.kt")
            .takeIf { it.exists() }
            ?.readText()
            .orEmpty()
        val presenter = File("src/androidMain/kotlin/org/siloserver/silo/android/push/PushNotificationPresenter.kt")
            .takeIf { it.exists() }
            ?.readText()
            .orEmpty()
        val manifest = File("src/androidMain/AndroidManifest.xml").readText()

        assertTrue(provider.contains("AndroidPushTokenProvider"))
        assertTrue(service.contains("FirebaseMessagingService") || service.contains("PushMessageHandler"))
        assertTrue(service.contains("delivery_id"))
        assertTrue(presenter.contains("fetch") || presenter.contains("notificationsRepository"))
        assertTrue(manifest.contains("POST_NOTIFICATIONS"))
    }
}
