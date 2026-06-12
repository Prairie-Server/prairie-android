// shared/src/commonTest/kotlin/com/continuum/app/model/admin/AdminModelsSerializationTest.kt
package com.continuum.app.model.admin

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminModelsSerializationTest {

    // Mirrors ContinuumJson (network/ContinuumHttpClientImpl.kt).
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `decodes admin stats with watch provider activity`() {
        val payload = """
            {
              "total_items": 1200, "total_files": 1500, "total_users": 8,
              "total_movies": 400, "total_movie_files": 410,
              "total_shows": 80, "total_show_files": 1090,
              "active_streams": 3, "total_storage_bytes": 987654321012,
              "watch_provider_activity": {
                "trakt_connected_profiles": 5, "trakt_enabled_profiles": 4,
                "trakt_export_enabled": 3, "trakt_scrobble_enabled": 2,
                "last_sync_completed_at": "2026-06-12T08:00:00Z",
                "sync_runs_24h": 12, "sync_errors_24h": 1,
                "imported_watched_24h": 30, "imported_progress_24h": 7,
                "exported_watched_24h": 9, "pending_exports": 2,
                "failed_exports": 0, "open_scrobbles": 1, "scrobbles_24h": 14
              }
            }
        """.trimIndent()

        val stats = json.decodeFromString(AdminStats.serializer(), payload)

        assertEquals(1200, stats.totalItems)
        assertEquals(987654321012L, stats.totalStorageBytes)
        assertEquals(3, stats.activeStreams)
        assertEquals(5L, stats.watchProviderActivity.traktConnectedProfiles)
        assertEquals(14L, stats.watchProviderActivity.scrobbles24h)
        assertEquals("2026-06-12T08:00:00Z", stats.watchProviderActivity.lastSyncCompletedAt)
    }

    @Test
    fun `decodes admin stats when watch provider activity omitted defaults to empty`() {
        val payload = """
            {"total_items":0,"total_files":0,"total_users":0,"total_movies":0,
             "total_movie_files":0,"total_shows":0,"total_show_files":0,
             "active_streams":0,"total_storage_bytes":0}
        """.trimIndent()

        val stats = json.decodeFromString(AdminStats.serializer(), payload)

        assertEquals(0L, stats.watchProviderActivity.traktConnectedProfiles)
        assertNull(stats.watchProviderActivity.lastSyncCompletedAt)
    }

    @Test
    fun `decodes admin user with optional last_active_at present`() {
        val payload = """
            {
              "id": 7, "username": "alice", "email": "a@x.io", "role": "user",
              "permissions": ["request"], "enabled": true,
              "library_ids": [1,2], "max_playback_quality": "1080p",
              "max_streams": 2, "max_transcodes": 1, "max_profiles": 5,
              "download_allowed": true, "download_transcode_allowed": false,
              "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-06-01T00:00:00Z",
              "last_active_at": "2026-06-12T07:00:00Z"
            }
        """.trimIndent()

        val u = json.decodeFromString(AdminUser.serializer(), payload)

        assertEquals(7, u.id)
        assertEquals(listOf("request"), u.permissions)
        assertEquals(listOf(1, 2), u.libraryIds)
        assertEquals("1080p", u.maxPlaybackQuality)
        assertEquals(2, u.maxStreams)
        assertEquals("2026-06-12T07:00:00Z", u.lastActiveAt)
    }

    @Test
    fun `decodes admin user with last_active_at absent`() {
        val payload = """
            {"id":1,"username":"root","email":"r@x.io","role":"admin",
             "permissions":[],"enabled":true,"library_ids":[],
             "max_playback_quality":"original","max_streams":0,"max_transcodes":0,
             "max_profiles":0,"download_allowed":false,"download_transcode_allowed":false,
             "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}
        """.trimIndent()

        val u = json.decodeFromString(AdminUser.serializer(), payload)
        assertNull(u.lastActiveAt)
        assertTrue(u.permissions.isEmpty())
    }

    @Test
    fun `decodes a bare array of admin users`() {
        val payload = """
            [{"id":1,"username":"root","email":"r@x.io","role":"admin","permissions":[],
              "enabled":true,"library_ids":[],"max_playback_quality":"original",
              "max_streams":0,"max_transcodes":0,"max_profiles":0,
              "download_allowed":false,"download_transcode_allowed":false,
              "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}]
        """.trimIndent()

        val users = json.decodeFromString(ListSerializer(AdminUser.serializer()), payload)
        assertEquals(1, users.size)
        assertEquals("root", users[0].username)
    }

    @Test
    fun `create user request omits null optional fields when encoded`() {
        val req = CreateUserRequest(
            username = "bob",
            email = "b@x.io",
            password = "secret",
            role = "user",
            permissions = listOf("request"),
            createDefaultProfile = true,
            libraryIds = listOf(3),
            maxPlaybackQuality = "1080p",
        )

        val encoded = json.encodeToString(CreateUserRequest.serializer(), req)

        assertTrue("\"username\":\"bob\"" in encoded)
        assertTrue("\"create_default_profile\":true" in encoded)
        assertTrue("\"library_ids\":[3]" in encoded)
        // explicitNulls = false → omitted optionals absent
        assertTrue("max_streams" !in encoded)
        assertTrue("default_profile_name" !in encoded)
        assertTrue("download_allowed" !in encoded)
    }

    @Test
    fun `update user request encodes only set fields (partial PUT)`() {
        val req = UpdateUserRequest(enabled = false, maxStreams = 4)
        val encoded = json.encodeToString(UpdateUserRequest.serializer(), req)
        assertTrue("\"enabled\":false" in encoded)
        assertTrue("\"max_streams\":4" in encoded)
        assertTrue("username" !in encoded)
        assertTrue("permissions" !in encoded)
        assertTrue("password" !in encoded)
    }

    @Test
    fun `decodes a rich playback session row with full transcode detail`() {
        val payload = """
            {
              "session_id": "sess-9", "user_id": 3, "username": "alice",
              "profile_id": "prof-2", "profile_name": "Alice",
              "media_file_id": 88, "requested_media_file_id": 88,
              "content_id": "c-1", "media_title": "Cold Harbor", "media_type": "episode",
              "series_name": "Severance", "episode_name": "Cold Harbor",
              "season_number": 2, "episode_number": 10,
              "poster_url": "https://cdn/p.jpg",
              "play_method": "transcode", "reporting_node": "node-a",
              "node_display_name": "Node A", "file_duration": 3600,
              "started_at": "2026-06-12T09:00:00Z", "updated_at": "2026-06-12T09:10:00Z",
              "position_seconds": 612.5, "is_paused": false,
              "has_playback_control": true, "client_ip": "10.0.0.5",
              "audio_track_index": 1, "transcode_audio": true, "stream_bitrate_kbps": 8000,
              "target_resolution": "1080p", "target_video_codec": "h264",
              "target_audio_codec": "aac", "target_bitrate_kbps": 8000,
              "transcode_hw_accel": "vaapi",
              "source_container": "mkv", "source_bitrate_kbps": 20000,
              "source_video_codec": "hevc", "source_video_resolution": "2160p",
              "source_audio_codec": "truehd", "source_audio_channels": 8,
              "source_audio_language": "eng", "source_audio_title": "Surround",
              "source_audio_layout": "7.1",
              "requested_video_codec": "h264", "requested_video_resolution": "1080p",
              "video_decision": "transcode", "audio_decision": "transcode"
            }
        """.trimIndent()

        val s = json.decodeFromString(AdminSession.serializer(), payload)

        assertEquals("sess-9", s.sessionId)
        assertEquals("transcode", s.playMethod)
        assertEquals(true, s.hasPlaybackControl)
        assertEquals(612.5, s.positionSeconds)
        assertEquals(8000, s.streamBitrateKbps)
        assertEquals("2160p", s.sourceVideoResolution)
        assertEquals(8, s.sourceAudioChannels)
        assertEquals("h264", s.targetVideoCodec)
        assertEquals(3600, s.fileDuration)
    }

    @Test
    fun `decodes minimal session row defaulting omitted fields`() {
        val payload = """
            {"session_id":"s1","user_id":1,"username":"u","profile_id":"p",
             "media_file_id":1,"requested_media_file_id":1,"media_title":"M",
             "media_type":"movie","play_method":"direct","reporting_node":"n",
             "started_at":"2026-06-12T09:00:00Z","updated_at":"2026-06-12T09:00:00Z",
             "position_seconds":0,"is_paused":false,"has_playback_control":false,
             "audio_track_index":0,"transcode_audio":false}
        """.trimIndent()

        val s = json.decodeFromString(AdminSession.serializer(), payload)
        assertEquals("s1", s.sessionId)
        assertNull(s.streamBitrateKbps)
        assertNull(s.fileDuration)
        assertEquals("", s.profileName)
        assertNull(s.seasonNumber)
    }

    @Test
    fun `decodes app log page with entries and next_cursor`() {
        val payload = """
            {
              "entries": [
                {"id": 101, "timestamp": "2026-06-12T09:00:00Z", "level": "info",
                 "component": "scanner", "message": "scan complete",
                 "request_id": "req-1", "user_id": 3, "session_id": "sess-1",
                 "playback_session_id": "ps-1", "client_ip": "10.0.0.1",
                 "node_id": "node-a", "attrs": {"folder": "movies", "count": 12}}
              ],
              "next_cursor": "Y3Vyc29y"
            }
        """.trimIndent()

        val page = json.decodeFromString(AdminLogPage.serializer(), payload)

        assertEquals(1, page.entries.size)
        assertEquals(101L, page.entries[0].id)
        assertEquals("scanner", page.entries[0].component)
        assertEquals(3, page.entries[0].userId)
        assertEquals("Y3Vyc29y", page.nextCursor)
        assertTrue(page.entries[0].attrs!!.containsKey("folder"))
    }

    @Test
    fun `decodes app log page without next_cursor and minimal entry`() {
        val payload = """
            {"entries":[{"id":1,"timestamp":"2026-06-12T09:00:00Z","level":"warn",
              "component":"http","message":"slow"}]}
        """.trimIndent()

        val page = json.decodeFromString(AdminLogPage.serializer(), payload)
        assertNull(page.nextCursor)
        assertNull(page.entries[0].requestId)
        assertNull(page.entries[0].userId)
        assertNull(page.entries[0].attrs)
    }

    @Test
    fun `decodes audit log page`() {
        val payload = """
            {
              "entries": [
                {"id": 5, "timestamp": "2026-06-12T09:00:00Z", "client_ip": "10.0.0.2",
                 "user_id": 3, "impersonator_user_id": 1, "session_id": "sess-2",
                 "request_id": "req-9", "method": "POST", "path": "/api/v1/admin/users",
                 "path_pattern": "/api/v1/admin/users", "status_code": 201,
                 "user_agent": "silo/1.0", "duration_ms": 42}
              ],
              "next_cursor": "Y3Vy"
            }
        """.trimIndent()

        val page = json.decodeFromString(AdminAuditPage.serializer(), payload)

        assertEquals(5L, page.entries[0].id)
        assertEquals("POST", page.entries[0].method)
        assertEquals(201, page.entries[0].statusCode)
        assertEquals(1, page.entries[0].impersonatorUserId)
        assertEquals("Y3Vy", page.nextCursor)
    }

    @Test
    fun `scan request encodes library_id and omits null path`() {
        val req = ScanRequest(libraryId = 4)
        val encoded = json.encodeToString(ScanRequest.serializer(), req)
        assertTrue("\"library_id\":4" in encoded)
        assertTrue("path" !in encoded)
    }

    @Test
    fun `decodes scan response and cancel response`() {
        val scan = json.decodeFromString(
            ScanResponse.serializer(),
            """{"status":"scanning","mode":"incremental","library_id":4}""",
        )
        assertEquals("scanning", scan.status)
        assertEquals("incremental", scan.mode)
        assertEquals(4, scan.libraryId)

        val cancel = json.decodeFromString(
            ScanCancelResponse.serializer(),
            """{"cancelled":2,"library_id":4}""",
        )
        assertEquals(2, cancel.cancelled)
        assertEquals(4, cancel.libraryId)
    }
}
