package org.prairieserver.prairie.common.downloads

import android.os.Environment
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `PublicDownloadCollection.relativeRoot` is written as literals so the enum can
 * be constructed in plain JVM unit tests, where `android.os` statics are
 * unavailable and would fail class initialisation.
 *
 * MediaProvider validates `RELATIVE_PATH` against its own allow-list, so a value
 * that drifts from the platform constant is rejected with
 * `IllegalArgumentException` — which `DownloadWorker` treats as permanent
 * failure. That is exactly how the plural "Downloads" made every ebook download
 * fail on API 29+. Pin the literals to the real constants here, under
 * Robolectric, so the two can never diverge again.
 */
@RunWith(RobolectricTestRunner::class)
class PublicDownloadRelativeRootTest {

    @Test
    fun `relative roots match the platform directory constants`() {
        assertEquals(Environment.DIRECTORY_MOVIES, PublicDownloadCollection.Video.relativeRoot)
        assertEquals(Environment.DIRECTORY_MUSIC, PublicDownloadCollection.Audio.relativeRoot)
        assertEquals(
            Environment.DIRECTORY_DOWNLOADS,
            PublicDownloadCollection.Downloads.relativeRoot,
            "MediaStore rejects any RELATIVE_PATH root outside its allow-list",
        )
    }

    @Test
    fun `the downloads root is singular, unlike its on-disk directory name`() {
        // The two genuinely differ: "Download" is MediaStore's collection root,
        // "Downloads" is the file-backed layout used on legacy storage.
        assertEquals("Download", PublicDownloadCollection.Downloads.relativeRoot)
        assertEquals("Downloads", PublicDownloadCollection.Downloads.directoryName)
    }
}
