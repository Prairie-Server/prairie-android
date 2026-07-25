package org.prairieserver.prairie.common.downloads

import org.prairieserver.prairie.model.download.DownloadMediaType
import kotlin.test.Test
import kotlin.test.assertEquals

class PublicDownloadCollectionTest {

    @Test
    fun `video media routes to public movies collection`() {
        assertEquals(PublicDownloadCollection.Video, PublicDownloadCollection.forMediaType(DownloadMediaType.Movie.wire))
        assertEquals(PublicDownloadCollection.Video, PublicDownloadCollection.forMediaType(DownloadMediaType.TvShow.wire))
    }

    @Test
    fun `audiobooks route to public music collection`() {
        assertEquals(PublicDownloadCollection.Audio, PublicDownloadCollection.forMediaType(DownloadMediaType.Audiobook.wire))
    }

    @Test
    fun `reading and unknown media route to public downloads collection`() {
        assertEquals(PublicDownloadCollection.Downloads, PublicDownloadCollection.forMediaType(DownloadMediaType.Ebook.wire))
        assertEquals(PublicDownloadCollection.Downloads, PublicDownloadCollection.forMediaType(DownloadMediaType.Unknown.wire))
        assertEquals(PublicDownloadCollection.Downloads, PublicDownloadCollection.forMediaType("comic"))
        assertEquals(PublicDownloadCollection.Downloads, PublicDownloadCollection.forMediaType(null))
    }
}
