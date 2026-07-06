package org.siloserver.silo.android.cast

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SiloCastPhoneSourceTest {
    @Test
    fun phoneHasBrowserControllerRemoteAndPlayOnDeviceEntrypoint() {
        val controller = File("src/androidMain/kotlin/org/siloserver/silo/android/cast/SiloCastController.kt").takeIf { it.exists() }?.readText().orEmpty()
        val picker = File("src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/cast/SiloCastTargetPickerSheet.kt").takeIf { it.exists() }?.readText().orEmpty()
        val remote = File("src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/cast/SiloCastRemoteScreen.kt").takeIf { it.exists() }?.readText().orEmpty()
        val mini = File("src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/cast/SiloCastMiniBar.kt").takeIf { it.exists() }?.readText().orEmpty()
        val detail = File("src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/ItemDetailScreen.kt").readText()

        assertTrue(controller.contains("SiloCastController"))
        assertTrue(controller.contains("SiloCastNsdBrowser"))
        assertTrue(picker.contains("SiloCastTargetPickerSheet"))
        assertTrue(remote.contains("SiloCastRemoteScreen"))
        assertTrue(mini.contains("SiloCastMiniBar"))
        assertTrue(detail.contains("Play on device"))
    }
}
