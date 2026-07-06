package org.siloserver.silo.tv.ui.screens.collections

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvCollectionsBrowseOnlySourceTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun tvCollectionsScreenDoesNotExposeAuthoringControls() {
        val source = source("src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/collections/TvCollectionsScreen.kt")

        listOf(
            "TvCreateCollectionDialog",
            "showCreateSheet",
            "createCollection",
            "deleteCollection",
            "moveCollection",
            "Create Collection",
            "New Collection",
            "openGroupAction",
            "createGroup",
            "renameGroup",
            "deleteGroup",
            "TvTextInputDialog",
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), "$forbidden must not be reachable from TV Collections.")
        }
        assertTrue(source.contains("TvCollectionsScreen("), "TV Collections browse surface must remain.")
    }

    @Test
    fun tvCollectionDetailDoesNotExposeDeleteOrEditActions() {
        val source = source("src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/collections/TvCollectionDetailScreen.kt")

        listOf("deleteCollection", "updateCollection", "Edit Collection", "Delete Collection").forEach { forbidden ->
            assertFalse(source.contains(forbidden), "$forbidden must not be reachable from TV Collection detail.")
        }
        assertTrue(source.contains("TvCollectionDetailScreen("), "TV Collection detail browse surface must remain.")
    }
}
