package org.prairieserver.prairie.android.ui.screens.collections

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectionsBrowseOnlySourceTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun collectionsScreenDoesNotExposeAuthoringControls() {
        val source = source("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/collections/CollectionsScreen.kt")

        listOf(
            "CreateCollectionSheet",
            "showCreateSheet",
            "createCollection",
            "deleteCollection",
            "moveCollection",
            "openGroupAction",
            "createGroup",
            "renameGroup",
            "deleteGroup",
            "GroupActionDialog",
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), "$forbidden must not be reachable from mobile Collections.")
        }
        assertTrue(source.contains("CollectionsScreen("), "Collections browse surface must remain.")
    }

    @Test
    fun collectionDetailDoesNotExposeDeleteOrEditActions() {
        val source = source("src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/collections/CollectionDetailScreen.kt")

        listOf("deleteCollection", "updateCollection", "Edit Collection", "Delete Collection").forEach { forbidden ->
            assertFalse(source.contains(forbidden), "$forbidden must not be reachable from mobile Collection detail.")
        }
        assertTrue(source.contains("CollectionDetailScreen("), "Collection detail browse surface must remain.")
    }
}
