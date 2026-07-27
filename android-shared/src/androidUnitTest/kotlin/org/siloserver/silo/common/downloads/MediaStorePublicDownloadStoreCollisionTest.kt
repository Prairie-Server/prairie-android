package org.siloserver.silo.common.downloads

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaStorePublicDownloadStoreCollisionTest {

    private lateinit var provider: RelativePathProvider
    private lateinit var store: MediaStorePublicDownloadStore

    @Before
    fun setUp() {
        provider = Robolectric.setupContentProvider(RelativePathProvider::class.java, "media")
        provider.put(ENCODED_QUESTION_PATH, displayName = "Question.epub", size = 13L)
        store = MediaStorePublicDownloadStore(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `raw reserved namespace cannot read encoded sibling`() {
        assertNull(store.locateByFileId("~Pw", "prof", 42))
        assertNotNull(store.locateByFileId("?", "prof", 42))
    }

    @Test
    fun `raw reserved namespace cannot count encoded sibling`() {
        assertEquals(0L, store.totalBytesUsed("~Pw", "prof"))
        assertEquals(13L, store.totalBytesUsed("?", "prof"))
    }

    @Test
    fun `raw reserved namespace cannot delete encoded sibling`() {
        assertFalse(store.delete("~Pw", "prof", 42, uriString = null))
        assertNotNull(store.locateByFileId("?", "prof", 42))
    }

    class RelativePathProvider : ContentProvider() {
        private val rows = linkedMapOf<String, Row>()

        fun put(path: String, displayName: String, size: Long) {
            rows[path] = Row(displayName, size)
        }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val columns: Array<String> = projection?.map { it }?.toTypedArray() ?: emptyArray()
            val cursor = MatrixCursor(columns)
            matchingPaths(selection, selectionArgs).forEachIndexed { index, path ->
                val row = checkNotNull(rows[path])
                cursor.addRow(columns.map { column ->
                    when (column) {
                        MediaStore.MediaColumns._ID -> (index + 1).toLong()
                        MediaStore.MediaColumns.DISPLAY_NAME -> row.displayName
                        MediaStore.MediaColumns.SIZE -> row.size
                        else -> null
                    }
                })
            }
            return cursor
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            val matches = matchingPaths(selection, selectionArgs)
            matches.forEach(rows::remove)
            return matches.size
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun getType(uri: Uri): String? = null

        private fun matchingPaths(selection: String?, selectionArgs: Array<out String>?): List<String> {
            val argument = selectionArgs?.singleOrNull() ?: return emptyList()
            return if (selection?.contains(" LIKE ") == true) {
                val prefix = argument.removeSuffix("%")
                    .replace("\\_", "_")
                    .replace("\\%", "%")
                    .replace("\\\\", "\\")
                rows.keys.filter { path -> path.startsWith(prefix) }
            } else {
                rows.keys.filter { path -> path == argument }
            }
        }

        private data class Row(val displayName: String, val size: Long)
    }

    companion object {
        private const val ENCODED_QUESTION_PATH = "Download/Silo/~Pw/prof/42/"
    }
}
