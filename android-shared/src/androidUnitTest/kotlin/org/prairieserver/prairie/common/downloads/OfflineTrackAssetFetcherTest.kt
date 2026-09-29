package org.prairieserver.prairie.common.downloads

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineTrackAssetFetcherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val manifest = """
        {
          "download_id": "dl_1",
          "selected_audio_track_index": 1,
          "audio_tracks": [
            {"index": 0, "language": "eng", "codec": "aac", "channels": 2, "default": true},
            {"index": 1, "language": "jpn", "codec": "aac", "channels": 2}
          ],
          "subtitles": [
            {"language": "eng", "format": "ass", "fetch_url": "/api/v2/downloads/dl_1/subtitles/embedded:2"},
            {"language": "fre", "format": "srt", "external": true, "fetch_url": "/api/v2/downloads/dl_1/subtitles/external:0"},
            {"language": "eng", "format": "sup", "forced": true, "fetch_url": "/api/v2/downloads/dl_1/subtitles/embedded:4"},
            {"language": "ger", "format": "sub", "fetch_url": "/api/v2/downloads/dl_1/subtitles/external:1"},
            {"language": "spa", "format": "srt", "fetch_url": "https://elsewhere.example/sub.srt"}
          ]
        }
    """.trimIndent()

    private val requested = mutableListOf<String>()

    private fun client(
        manifestStatus: HttpStatusCode = HttpStatusCode.OK,
        transientManifestFailures: Int = 0,
    ) = HttpClient(
        MockEngine { request ->
            val path = request.url.encodedPath
            requested += path
            when {
                path.endsWith("/manifest") && requested.count { it.endsWith("/manifest") } <= transientManifestFailures ->
                    respond("", HttpStatusCode.ServiceUnavailable)
                path.endsWith("/manifest") -> respond(manifest, manifestStatus)
                path.endsWith("/subtitles/embedded:2") -> respond("[Script Info]\nTitle: x\n")
                // The server failed to read the external sidecar.
                path.endsWith("/subtitles/external:0") -> respond("", HttpStatusCode.NotFound)
                path.endsWith("/subtitles/embedded:4") -> respond(byteArrayOf(0x50, 0x47, 0x00, 0x01))
                else -> respond("", HttpStatusCode.InternalServerError)
            }
        },
    ) {
        install(HttpTimeout)
        defaultRequest { url("https://silo.example/") }
    }

    @Test
    fun capturesAudioTracksAndSavesEveryFetchableSidecar() = runBlocking {
        val storage = DownloadStorage(tmp.newFolder("filesDir"))

        val info = assertNotNull(
            OfflineTrackAssetFetcher(client(), storage).fetch("dl_1", "srv", "prof", 42) {},
        )

        assertEquals(listOf("eng", "jpn"), info.audioTracks.map { it.language })
        assertEquals(1, info.defaultAudioPosition())
        // The failed SRT, the unmountable .sub and the foreign URL are skipped
        // without failing the rest.
        assertEquals(listOf("ass", "pgs"), info.subtitles.map { it.format })
        assertEquals(listOf(false, true), info.subtitles.map { it.forced })
        val ass = File(info.subtitles[0].path)
        val pgs = File(info.subtitles[1].path)
        assertTrue(ass.isFile && ass.name.endsWith(".ass"))
        assertTrue(pgs.isFile && pgs.name.endsWith(".sup"))
        assertEquals(storage.offlineSubtitleDirectory("srv", "prof", 42), ass.parentFile)
        assertFalse(requested.any { it.contains("sub.srt") })
        assertTrue(ass.parentFile!!.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun deletingTheDownloadRemovesItsSidecars() = runBlocking {
        val storage = DownloadStorage(tmp.newFolder("filesDir"))
        val info = assertNotNull(
            OfflineTrackAssetFetcher(client(), storage).fetch("dl_1", "srv", "prof", 42) {},
        )

        storage.delete("srv", "prof", 42)

        assertTrue(info.subtitles.none { File(it.path).exists() })
    }

    @Test
    fun aMissingManifestCapturesNothing() = runBlocking {
        val storage = DownloadStorage(tmp.newFolder("filesDir"))

        assertNull(
            OfflineTrackAssetFetcher(client(HttpStatusCode.NotFound), storage).fetch("dl_1", "srv", "prof", 42) {},
        )
        assertEquals(listOf("/api/v2/downloads/dl_1/manifest"), requested)
    }

    @Test
    fun aTransientManifestFailureIsRetried() = runBlocking {
        val storage = DownloadStorage(tmp.newFolder("filesDir"))

        val info = assertNotNull(
            OfflineTrackAssetFetcher(client(transientManifestFailures = 2), storage, manifestRetryDelayMs = 0)
                .fetch("dl_1", "srv", "prof", 42) {},
        )

        assertEquals(3, requested.count { it.endsWith("/manifest") })
        assertEquals(2, info.audioTracks.size)
    }

    @Test
    fun onlyVideoDownloadsCaptureTracks() {
        assertTrue(OfflineTrackAssetFetcher.appliesTo("movie"))
        assertTrue(OfflineTrackAssetFetcher.appliesTo("tv"))
        assertFalse(OfflineTrackAssetFetcher.appliesTo("audiobook"))
        assertFalse(OfflineTrackAssetFetcher.appliesTo("ebook"))
    }
}
