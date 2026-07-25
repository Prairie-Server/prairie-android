package org.prairieserver.prairie.android.ui.screens.servers

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ServerListScreenSourceTest {
    private val source = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/servers/ServerListScreen.kt",
    ).readText()

    @Test
    fun serverActionsAreVisibleWithoutLongPress() {
        assertTrue(source.contains("Icons.Default.MoreVert"))
        assertTrue(source.contains("contentDescription = \"Server actions\""))
        assertTrue(source.contains("DropdownMenu("))
    }
}
