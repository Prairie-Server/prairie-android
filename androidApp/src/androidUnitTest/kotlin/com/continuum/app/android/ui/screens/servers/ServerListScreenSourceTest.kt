package com.continuum.app.android.ui.screens.servers

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ServerListScreenSourceTest {
    private val source = File(
        "src/androidMain/kotlin/com/continuum/app/android/ui/screens/servers/ServerListScreen.kt",
    ).readText()

    @Test
    fun serverActionsAreVisibleWithoutLongPress() {
        assertTrue(source.contains("Icons.Default.MoreVert"))
        assertTrue(source.contains("contentDescription = \"Server actions\""))
        assertTrue(source.contains("DropdownMenu("))
    }
}
