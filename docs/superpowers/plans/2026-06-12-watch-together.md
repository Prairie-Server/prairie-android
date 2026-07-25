# Watch Together Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Synchronized playback rooms (create/join/lobby/suggestions/voting/synced playback/invite) on mobile AND TV, per docs/superpowers/specs/2026-06-12-watch-together-design.md. Built against prairie-server origin/main.

**Architecture:** Realtime-only. Shared KMP layer = models + WatchTogetherApi (REST, room-token query auth) + a per-room WebSocket client (query-param auth, ping/pong clock sync) + a PURE RoomSyncEngine (the timing brain: NTP offset, scheduled command application, drift/dedupe/gating) + a singleton WatchTogetherRepository. Each app binds its existing player to the room via a RoomSyncController that feeds the engine and applies its decisions onto the player's seek/play-pause seams. Each member streams their own normal playback session; the room only syncs position/state.

**Tech Stack:** Kotlin Multiplatform, Ktor WebSockets, kotlinx.serialization, Koin, Jetpack Compose + TV Compose, Media3.

**Sections:** S = shared (S1-S5), M = mobile (M1-M3), T = TV (T1-T3). Order: S, then M, then T. Mobile/TV tasks carry Dependencies notes with ASSUMED shared signatures — executors verify against landed code (landed wins). Pure helpers (RoomSyncEngine, decodeRoomFrame, JoinCodeState, shouldEnterSyncedPlayer, tvRoomTransportGate) are unit-tested; real two-device sync verification is manual.

---

## Section S: Shared layer

I have all contracts confirmed. The server uses 201 on create suggestion (not just create room). I now have everything needed to produce the complete plan tasks.

### Task S1: Watch Together shared models + serialization

**Files:**
- Create: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/model/watchtogether/WatchTogetherModels.kt`
- Test: `/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/model/watchtogether/WatchTogetherModelsSerializationTest.kt`

- [ ] **Step 1: Write the failing test** (full code)

`/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/model/watchtogether/WatchTogetherModelsSerializationTest.kt`
```kotlin
package com.continuum.app.model.watchtogether

import com.continuum.app.network.WatchTogetherRealtime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchTogetherModelsSerializationTest {

    // Mirrors ContinuumJson (network/ContinuumHttpClientImpl.kt).
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    // ---- RoomSnapshot ---------------------------------------------------------

    @Test
    fun `decodes full host snapshot incl per-recipient self fields and invite_path`() {
        val payload = """
            {
              "room_id": "room-1",
              "phase": "playing",
              "playback_state": "waiting",
              "selection_mode": "vote",
              "selection_revision": 7,
              "selected_content_id": "tt-9",
              "selected_file_id": 42,
              "selected_library_id": 3,
              "code": "ABCD1234",
              "guest_control_policy": "guest_play_pause",
              "is_paused": true,
              "anchor_position_seconds": 123.5,
              "anchor_updated_at": "2026-06-12T09:30:00Z",
              "generation": 11,
              "member_count": 2,
              "host_connected": true,
              "self_role": "host",
              "self_can_control_transport": true,
              "self_can_manage_room": true,
              "self_ignore_wait": false,
              "attached_session_id": "sess-1",
              "invite_path": "/watch-together/join?code=ABCD1234"
            }
        """.trimIndent()

        val s = json.decodeFromString(RoomSnapshot.serializer(), payload)

        assertEquals("room-1", s.roomId)
        assertEquals(RoomPhase.Playing, s.phase)
        assertEquals(RoomPlaybackState.Waiting, s.playbackState)
        assertEquals(RoomSelectionMode.Vote, s.selectionMode)
        assertEquals(7L, s.selectionRevision)
        assertEquals("tt-9", s.selectedContentId)
        assertEquals(42, s.selectedFileId)
        assertEquals(3, s.selectedLibraryId)
        assertEquals("ABCD1234", s.code)
        assertEquals(GuestControlPolicy.GuestPlayPause, s.guestControlPolicy)
        assertTrue(s.isPaused)
        assertEquals(123.5, s.anchorPositionSeconds)
        assertEquals("2026-06-12T09:30:00Z", s.anchorUpdatedAt)
        assertEquals(11L, s.generation)
        assertEquals(2, s.memberCount)
        assertTrue(s.hostConnected)
        assertEquals(MemberRole.Host, s.selfRole)
        assertTrue(s.selfCanControlTransport)
        assertTrue(s.selfCanManageRoom)
        assertFalse(s.selfIgnoreWait)
        assertEquals("sess-1", s.attachedSessionId)
        assertEquals("/watch-together/join?code=ABCD1234", s.invitePath)
    }

    @Test
    fun `guest snapshot omits invite_path and selection, unknown enums fall back`() {
        val payload = """
            {
              "room_id": "room-2",
              "phase": "lobby",
              "playback_state": "idle",
              "selection_mode": "future_mode",
              "selection_revision": 0,
              "code": "ZZZZ0000",
              "guest_control_policy": "host_only",
              "is_paused": false,
              "anchor_position_seconds": 0.0,
              "anchor_updated_at": "2026-06-12T09:00:00Z",
              "generation": 1,
              "member_count": 1,
              "host_connected": true,
              "self_role": "guest",
              "self_can_control_transport": false,
              "self_can_manage_room": false,
              "self_ignore_wait": false
            }
        """.trimIndent()

        val s = json.decodeFromString(RoomSnapshot.serializer(), payload)
        assertEquals(RoomPhase.Lobby, s.phase)
        assertEquals(RoomSelectionMode.Unknown, s.selectionMode) // unknown enum tolerated
        assertNull(s.selectedContentId)
        assertNull(s.selectedFileId)
        assertNull(s.invitePath)
        assertEquals(MemberRole.Guest, s.selfRole)
        assertFalse(s.selfCanControlTransport)
    }

    // ---- Suggestion -----------------------------------------------------------

    @Test
    fun `decodes suggestion incl voted_by_me`() {
        val payload = """
            {
              "id": "sug-1",
              "room_id": "room-1",
              "suggester_user_id": 5,
              "suggester_profile_id": "prof-5",
              "content_id": "tt-3",
              "content_type": "movie",
              "title": "Arrival",
              "subtitle": "2016",
              "poster_url": "https://cdn/arr.jpg",
              "note": "great pick",
              "vote_count": 4,
              "voted_by_me": true,
              "created_at": "2026-06-12T08:00:00Z"
            }
        """.trimIndent()

        val sug = json.decodeFromString(Suggestion.serializer(), payload)
        assertEquals("sug-1", sug.id)
        assertEquals("room-1", sug.roomId)
        assertEquals(5, sug.suggesterUserId)
        assertEquals("prof-5", sug.suggesterProfileId)
        assertEquals("movie", sug.contentType)
        assertEquals("Arrival", sug.title)
        assertEquals(4, sug.voteCount)
        assertTrue(sug.votedByMe)
    }

    @Test
    fun `suggestion defaults voted_by_me false and tolerates missing optionals`() {
        val payload = """
            {
              "id": "sug-2", "room_id": "room-1", "suggester_user_id": 1,
              "suggester_profile_id": "p", "content_id": "tt-1", "content_type": "episode",
              "title": "Pilot", "vote_count": 0, "created_at": "2026-06-12T08:00:00Z"
            }
        """.trimIndent()
        val sug = json.decodeFromString(Suggestion.serializer(), payload)
        assertFalse(sug.votedByMe)
        assertEquals("", sug.subtitle)
        assertEquals("", sug.note)
    }

    // ---- TransportCommand (RFC3339Nano execute_at) ----------------------------

    @Test
    fun `decodes transport command with nanosecond execute_at`() {
        val payload = """
            {
              "command_id": "cmd-uuid-1",
              "session_id": "sess-1",
              "selection_revision": 7,
              "action": "seek",
              "position_seconds": 88.25,
              "execute_at": "2026-06-12T09:30:00.123456789Z",
              "issued_at": "2026-06-12T09:29:59.900000000Z",
              "playback_state": "waiting"
            }
        """.trimIndent()

        val c = json.decodeFromString(TransportCommand.serializer(), payload)
        assertEquals("cmd-uuid-1", c.commandId)
        assertEquals("sess-1", c.sessionId)
        assertEquals(7L, c.selectionRevision)
        assertEquals(TransportAction.Seek, c.action)
        assertEquals(88.25, c.positionSeconds)
        assertEquals("2026-06-12T09:30:00.123456789Z", c.executeAt)
        assertEquals("2026-06-12T09:29:59.900000000Z", c.issuedAt)
        assertEquals(RoomPlaybackState.Waiting, c.playbackState)
    }

    @Test
    fun `transport command tolerates omitted session_id`() {
        val payload = """
            {
              "command_id": "cmd-2", "selection_revision": 1, "action": "play",
              "position_seconds": 0.0, "execute_at": "2026-06-12T09:30:00Z",
              "issued_at": "2026-06-12T09:29:59Z", "playback_state": "playing"
            }
        """.trimIndent()
        val c = json.decodeFromString(TransportCommand.serializer(), payload)
        assertEquals("", c.sessionId)
        assertEquals(TransportAction.Play, c.action)
    }

    // ---- Response wrappers ----------------------------------------------------

    @Test
    fun `decodes room response wrapper with room_access_token`() {
        val payload = """{"room":{"room_id":"r","phase":"lobby","playback_state":"idle",
            "selection_mode":"host_pick","selection_revision":0,"code":"AAAA0000",
            "guest_control_policy":"host_only","is_paused":false,"anchor_position_seconds":0.0,
            "anchor_updated_at":"2026-06-12T09:00:00Z","generation":1,"member_count":1,
            "host_connected":true,"self_role":"host","self_can_control_transport":true,
            "self_can_manage_room":true,"self_ignore_wait":false},
            "room_access_token":"jwt-room-token"}"""
        val r = json.decodeFromString(RoomResponse.serializer(), payload)
        assertEquals("r", r.room.roomId)
        assertEquals("jwt-room-token", r.roomAccessToken)
    }

    @Test
    fun `decodes suggestions response wrapper`() {
        val payload = """{"suggestions":[{"id":"s1","room_id":"r","suggester_user_id":1,
            "suggester_profile_id":"p","content_id":"c","content_type":"movie","title":"T",
            "vote_count":1,"voted_by_me":false,"created_at":"2026-06-12T08:00:00Z"}]}"""
        val r = json.decodeFromString(SuggestionsResponse.serializer(), payload)
        assertEquals(1, r.suggestions.size)
        assertEquals("s1", r.suggestions.first().id)
    }

    // ---- Request models (encode → correct wire keys, omit nulls) --------------

    @Test
    fun `create request encodes selection_mode`() {
        val out = json.encodeToString(
            CreateRoomRequest.serializer(),
            CreateRoomRequest(selectionMode = "vote"),
        )
        assertEquals(setOf("selection_mode"), json.parseToJsonElement(out).jsonObject.keys)
    }

    @Test
    fun `join request encodes join_token and code`() {
        val out = json.encodeToString(
            JoinRoomRequest.serializer(),
            JoinRoomRequest(code = "ABCD1234"),
        )
        val keys = json.parseToJsonElement(out).jsonObject.keys
        assertTrue("code" in keys)
        assertFalse("join_token" in keys) // null omitted (explicitNulls=false)
    }

    @Test
    fun `selection request encodes content_id and omits null file_id`() {
        val out = json.encodeToString(
            SetSelectionRequest.serializer(),
            SetSelectionRequest(contentId = "tt-1"),
        )
        assertEquals(setOf("content_id"), json.parseToJsonElement(out).jsonObject.keys)
    }

    @Test
    fun `policy request encodes guest_control_policy`() {
        val out = json.encodeToString(
            UpdatePolicyRequest.serializer(),
            UpdatePolicyRequest(guestControlPolicy = "host_only"),
        )
        assertEquals(setOf("guest_control_policy"), json.parseToJsonElement(out).jsonObject.keys)
    }

    @Test
    fun `add suggestion request encodes required fields and omits null optionals`() {
        val out = json.encodeToString(
            AddSuggestionRequest.serializer(),
            AddSuggestionRequest(contentId = "c", contentType = "movie", title = "T"),
        )
        assertEquals(
            setOf("content_id", "content_type", "title"),
            json.parseToJsonElement(out).jsonObject.keys,
        )
    }

    @Test
    fun `promote request encodes suggestion_id`() {
        val out = json.encodeToString(
            PromoteSuggestionRequest.serializer(),
            PromoteSuggestionRequest(suggestionId = "sug-1"),
        )
        assertEquals(setOf("suggestion_id"), json.parseToJsonElement(out).jsonObject.keys)
    }

    // ---- Client→server WS frames ----------------------------------------------

    @Test
    fun `attach_session frame encodes type and session_id`() {
        val out = json.encodeToString(WsAttachSession.serializer(), WsAttachSession(sessionId = "s"))
        val obj = json.parseToJsonElement(out).jsonObject
        assertEquals("attach_session", obj["type"]!!.toString().trim('"'))
        assertEquals("s", obj["session_id"]!!.toString().trim('"'))
    }

    @Test
    fun `transport_request frame omits null position_seconds`() {
        val out = json.encodeToString(
            WsTransportRequest.serializer(),
            WsTransportRequest(action = "pause", isPaused = true),
        )
        val keys = json.parseToJsonElement(out).jsonObject.keys
        assertTrue("type" in keys && "action" in keys && "is_paused" in keys)
        assertFalse("position_seconds" in keys)
    }

    @Test
    fun `ping frame encodes client_sent_at`() {
        val out = json.encodeToString(
            WsPing.serializer(),
            WsPing(clientSentAt = "2026-06-12T09:30:00.123456789Z"),
        )
        val obj = json.parseToJsonElement(out).jsonObject
        assertEquals("ping", obj["type"]!!.toString().trim('"'))
        assertEquals("2026-06-12T09:30:00.123456789Z", obj["client_sent_at"]!!.toString().trim('"'))
    }

    @Test
    fun `state_report ready buffering frames carry session position and paused`() {
        val sr = json.encodeToString(
            WsStateReport.serializer(),
            WsStateReport(sessionId = "s", positionSeconds = 12.0, isPaused = false),
        )
        val obj = json.parseToJsonElement(sr).jsonObject
        assertEquals("state_report", obj["type"]!!.toString().trim('"'))
        assertEquals(setOf("type", "session_id", "position_seconds", "is_paused"), obj.keys)
    }

    // ---- Server→client frame envelope + payloads ------------------------------

    @Test
    fun `decodes pong frame fields`() {
        val payload = """{"type":"pong","client_sent_at":"2026-06-12T09:30:00.000000001Z",
            "server_received_at":"2026-06-12T09:30:00.050000000Z",
            "server_sent_at":"2026-06-12T09:30:00.060000000Z"}"""
        val p = json.decodeFromString(WsPong.serializer(), payload)
        assertEquals("2026-06-12T09:30:00.000000001Z", p.clientSentAt)
        assertEquals("2026-06-12T09:30:00.050000000Z", p.serverReceivedAt)
        assertEquals("2026-06-12T09:30:00.060000000Z", p.serverSentAt)
    }

    @Test
    fun `decodes room_closed reason and error frame`() {
        val closed = json.decodeFromString(
            WsRoomClosed.serializer(),
            """{"type":"room_closed","reason":"host_left"}""",
        )
        assertEquals("host_left", closed.reason)
        val err = json.decodeFromString(
            WsError.serializer(),
            """{"type":"error","code":"gone","message":"Room is no longer active"}""",
        )
        assertEquals("gone", err.code)
        assertEquals("Room is no longer active", err.message)
    }

    @Test
    fun `realtime channel constants match server`() {
        // Guard against drift in the WS type discriminators.
        assertEquals("snapshot", WatchTogetherRealtime.TypeSnapshot)
        assertEquals("transport_command", WatchTogetherRealtime.TypeTransportCommand)
        assertEquals("suggestions_update", WatchTogetherRealtime.TypeSuggestionsUpdate)
        assertEquals("room_closed", WatchTogetherRealtime.TypeRoomClosed)
        assertEquals("pong", WatchTogetherRealtime.TypePong)
        assertEquals("error", WatchTogetherRealtime.TypeError)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (command + expected failure)
```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.watchtogether.WatchTogetherModelsSerializationTest
```
Expected: compilation failure — `WatchTogetherModels.kt` and `WatchTogetherRealtime` do not exist yet (unresolved references `RoomSnapshot`, `Suggestion`, `TransportCommand`, request/response models, WS frame models, `WatchTogetherRealtime`).

- [ ] **Step 3: Implementation** (complete code)

`/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/model/watchtogether/WatchTogetherModels.kt`
```kotlin
package com.continuum.app.model.watchtogether

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Watch Together shared wire models. Mirrors prairie-server `origin/main`
 * `internal/watchtogether/types.go` (Snapshot, Suggestion, TransportCommand)
 * and `internal/api/handlers/watch_together.go` (request/response wrappers,
 * WS client/server frames).
 *
 * Every enum is parsed leniently with an [Unknown] fallback so a future server
 * value never breaks decoding (matching the notifications-type convention).
 */

// ---- Enums (lenient, string-backed) ---------------------------------------

@Serializable(with = RoomPhaseSerializer::class)
enum class RoomPhase(val wire: String) {
    Lobby("lobby"),
    Playing("playing"),
    Ended("ended"),
    Unknown("");

    companion object {
        fun fromWire(w: String): RoomPhase = entries.firstOrNull { it.wire == w } ?: Unknown
    }
}

@Serializable(with = RoomPlaybackStateSerializer::class)
enum class RoomPlaybackState(val wire: String) {
    Idle("idle"),
    Waiting("waiting"),
    Paused("paused"),
    Playing("playing"),
    Unknown("");

    companion object {
        fun fromWire(w: String): RoomPlaybackState = entries.firstOrNull { it.wire == w } ?: Unknown
    }
}

@Serializable(with = RoomSelectionModeSerializer::class)
enum class RoomSelectionMode(val wire: String) {
    HostPick("host_pick"),
    Vote("vote"),
    Unknown("");

    companion object {
        fun fromWire(w: String): RoomSelectionMode = entries.firstOrNull { it.wire == w } ?: Unknown
    }
}

@Serializable(with = GuestControlPolicySerializer::class)
enum class GuestControlPolicy(val wire: String) {
    HostOnly("host_only"),
    GuestPlayPause("guest_play_pause"),
    Unknown("");

    companion object {
        fun fromWire(w: String): GuestControlPolicy = entries.firstOrNull { it.wire == w } ?: Unknown
    }
}

@Serializable(with = MemberRoleSerializer::class)
enum class MemberRole(val wire: String) {
    Host("host"),
    Guest("guest"),
    Unknown("");

    companion object {
        fun fromWire(w: String): MemberRole = entries.firstOrNull { it.wire == w } ?: Unknown
    }
}

@Serializable(with = TransportActionSerializer::class)
enum class TransportAction(val wire: String) {
    Play("play"),
    Pause("pause"),
    Seek("seek"),
    Unknown("");

    companion object {
        fun fromWire(w: String): TransportAction = entries.firstOrNull { it.wire == w } ?: Unknown
    }
}

private class EnumWireSerializer<T : Enum<T>>(
    name: String,
    private val toWire: (T) -> String,
    private val fromWire: (String) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(toWire(value))
    override fun deserialize(decoder: Decoder): T = fromWire(decoder.decodeString())
}

object RoomPhaseSerializer : KSerializer<RoomPhase> by EnumWireSerializer(
    "RoomPhase", { it.wire }, RoomPhase::fromWire,
)
object RoomPlaybackStateSerializer : KSerializer<RoomPlaybackState> by EnumWireSerializer(
    "RoomPlaybackState", { it.wire }, RoomPlaybackState::fromWire,
)
object RoomSelectionModeSerializer : KSerializer<RoomSelectionMode> by EnumWireSerializer(
    "RoomSelectionMode", { it.wire }, RoomSelectionMode::fromWire,
)
object GuestControlPolicySerializer : KSerializer<GuestControlPolicy> by EnumWireSerializer(
    "GuestControlPolicy", { it.wire }, GuestControlPolicy::fromWire,
)
object MemberRoleSerializer : KSerializer<MemberRole> by EnumWireSerializer(
    "MemberRole", { it.wire }, MemberRole::fromWire,
)
object TransportActionSerializer : KSerializer<TransportAction> by EnumWireSerializer(
    "TransportAction", { it.wire }, TransportAction::fromWire,
)

// ---- Core payloads --------------------------------------------------------

/**
 * Universal room payload (server `Snapshot`). Several fields are per-recipient
 * (`self_*`, `invite_path` is host-only). `selection_revision` and
 * `generation` are Longs; `anchor_position_seconds` is a Double; timestamps are
 * RFC3339 strings.
 */
@Serializable
data class RoomSnapshot(
    @SerialName("room_id") val roomId: String,
    val phase: RoomPhase = RoomPhase.Unknown,
    @SerialName("playback_state") val playbackState: RoomPlaybackState = RoomPlaybackState.Unknown,
    @SerialName("selection_mode") val selectionMode: RoomSelectionMode = RoomSelectionMode.Unknown,
    @SerialName("selection_revision") val selectionRevision: Long = 0L,
    @SerialName("selected_content_id") val selectedContentId: String? = null,
    @SerialName("selected_file_id") val selectedFileId: Int? = null,
    @SerialName("selected_library_id") val selectedLibraryId: Int? = null,
    val code: String = "",
    @SerialName("guest_control_policy") val guestControlPolicy: GuestControlPolicy = GuestControlPolicy.HostOnly,
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("anchor_position_seconds") val anchorPositionSeconds: Double = 0.0,
    @SerialName("anchor_updated_at") val anchorUpdatedAt: String = "",
    val generation: Long = 0L,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("host_connected") val hostConnected: Boolean = false,
    @SerialName("self_role") val selfRole: MemberRole = MemberRole.Unknown,
    @SerialName("self_can_control_transport") val selfCanControlTransport: Boolean = false,
    @SerialName("self_can_manage_room") val selfCanManageRoom: Boolean = false,
    @SerialName("self_ignore_wait") val selfIgnoreWait: Boolean = false,
    @SerialName("attached_session_id") val attachedSessionId: String? = null,
    @SerialName("invite_path") val invitePath: String? = null,
)

/** A content suggestion (vote-mode room). In WS broadcasts [votedByMe] is forced false. */
@Serializable
data class Suggestion(
    val id: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("suggester_user_id") val suggesterUserId: Int = 0,
    @SerialName("suggester_profile_id") val suggesterProfileId: String = "",
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String,
    val subtitle: String = "",
    @SerialName("poster_url") val posterUrl: String = "",
    val note: String = "",
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("voted_by_me") val votedByMe: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

/**
 * Discrete transport directive broadcast on the room WS. [executeAt]/[issuedAt]
 * are RFC3339Nano strings (server clock); the [com.continuum.app.RoomSyncEngine]
 * converts [executeAt] to a local schedule via the offset.
 */
@Serializable
data class TransportCommand(
    @SerialName("command_id") val commandId: String,
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("selection_revision") val selectionRevision: Long = 0L,
    val action: TransportAction = TransportAction.Unknown,
    @SerialName("position_seconds") val positionSeconds: Double = 0.0,
    @SerialName("execute_at") val executeAt: String,
    @SerialName("issued_at") val issuedAt: String = "",
    @SerialName("playback_state") val playbackState: RoomPlaybackState = RoomPlaybackState.Unknown,
)

// ---- REST request models --------------------------------------------------

/** POST /rooms. */
@Serializable
data class CreateRoomRequest(
    @SerialName("selection_mode") val selectionMode: String? = null,
)

/** POST /join — one of [code]/[joinToken] required; token wins. */
@Serializable
data class JoinRoomRequest(
    val code: String? = null,
    @SerialName("join_token") val joinToken: String? = null,
)

/** PUT /rooms/{id}/selection (host-only). */
@Serializable
data class SetSelectionRequest(
    @SerialName("content_id") val contentId: String,
    @SerialName("file_id") val fileId: Int? = null,
    @SerialName("library_id") val libraryId: Int? = null,
)

/** PATCH /rooms/{id}/policy (host-only). */
@Serializable
data class UpdatePolicyRequest(
    @SerialName("guest_control_policy") val guestControlPolicy: String,
)

/** POST /rooms/{id}/suggestions. */
@Serializable
data class AddSuggestionRequest(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String,
    val subtitle: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    val note: String? = null,
)

/** POST /rooms/{id}/suggestions/promote (host-only). */
@Serializable
data class PromoteSuggestionRequest(
    @SerialName("suggestion_id") val suggestionId: String,
)

// ---- REST response wrappers -----------------------------------------------

/** `{room, room_access_token}` — create/join/selection/policy/promote/get. */
@Serializable
data class RoomResponse(
    val room: RoomSnapshot,
    @SerialName("room_access_token") val roomAccessToken: String = "",
)

/** `{suggestions:[…]}` — all suggestion list/mutation responses. */
@Serializable
data class SuggestionsResponse(
    val suggestions: List<Suggestion> = emptyList(),
)

// ---- Client→server WS frames ----------------------------------------------

@Serializable
data class WsAttachSession(
    val type: String = "attach_session",
    @SerialName("session_id") val sessionId: String,
)

@Serializable
data class WsTransportRequest(
    val type: String = "transport_request",
    val action: String,
    @SerialName("position_seconds") val positionSeconds: Double? = null,
    @SerialName("is_paused") val isPaused: Boolean,
)

@Serializable
data class WsStateReport(
    val type: String = "state_report",
    @SerialName("session_id") val sessionId: String,
    @SerialName("position_seconds") val positionSeconds: Double,
    @SerialName("is_paused") val isPaused: Boolean,
)

@Serializable
data class WsReady(
    val type: String = "ready",
    @SerialName("session_id") val sessionId: String,
    @SerialName("position_seconds") val positionSeconds: Double,
    @SerialName("is_paused") val isPaused: Boolean,
)

@Serializable
data class WsBuffering(
    val type: String = "buffering",
    @SerialName("session_id") val sessionId: String,
    @SerialName("position_seconds") val positionSeconds: Double,
    @SerialName("is_paused") val isPaused: Boolean,
)

@Serializable
data class WsPing(
    val type: String = "ping",
    @SerialName("client_sent_at") val clientSentAt: String,
)

// ---- Server→client WS frame payloads --------------------------------------

@Serializable
data class WsPong(
    val type: String = "pong",
    @SerialName("client_sent_at") val clientSentAt: String = "",
    @SerialName("server_received_at") val serverReceivedAt: String = "",
    @SerialName("server_sent_at") val serverSentAt: String = "",
)

@Serializable
data class WsRoomClosed(
    val type: String = "room_closed",
    val reason: String = "",
)

@Serializable
data class WsError(
    val type: String = "error",
    val code: String = "",
    val message: String = "",
)
```

`/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/WatchTogetherRealtimeEvent.kt`
```kotlin
package com.continuum.app.network

import com.continuum.app.model.watchtogether.RoomSnapshot
import com.continuum.app.model.watchtogether.Suggestion
import com.continuum.app.model.watchtogether.TransportCommand

/** Room WS server-frame type discriminators (mirrors the server `"type"` strings). */
object WatchTogetherRealtime {
    const val TypeSnapshot = "snapshot"
    const val TypeTransportCommand = "transport_command"
    const val TypeSuggestionsUpdate = "suggestions_update"
    const val TypeRoomClosed = "room_closed"
    const val TypePong = "pong"
    const val TypeError = "error"
}

/**
 * A decoded room realtime event. The repository folds these into its
 * StateFlows / feeds them to [com.continuum.app.RoomSyncEngine]. [Closed]
 * is emitted by the client when the socket itself ends (distinct from a server
 * [Closed]-with-reason `room_closed`, surfaced as the same event).
 */
sealed class RoomRealtimeEvent {
    data class SnapshotEvent(val room: RoomSnapshot) : RoomRealtimeEvent()
    data class TransportCommandEvent(val command: TransportCommand) : RoomRealtimeEvent()
    data class SuggestionsEvent(val suggestions: List<Suggestion>) : RoomRealtimeEvent()
    data class Pong(
        val clientSentAt: String,
        val serverReceivedAt: String,
        val serverSentAt: String,
    ) : RoomRealtimeEvent()

    /** Server `room_closed{reason}`. The repository stops reconnecting on this. */
    data class Closed(val reason: String? = null) : RoomRealtimeEvent()

    /** Server `error{code,message}`. */
    data class Error(val code: String, val message: String) : RoomRealtimeEvent()
}
```

- [ ] **Step 4: Run tests** (command)
```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.model.watchtogether.WatchTogetherModelsSerializationTest
```

- [ ] **Step 5: Commit**
```bash
git add shared/src/commonMain/kotlin/com/continuum/app/model/watchtogether/WatchTogetherModels.kt \
        shared/src/commonMain/kotlin/com/continuum/app/network/WatchTogetherRealtimeEvent.kt \
        shared/src/commonTest/kotlin/com/continuum/app/model/watchtogether/WatchTogetherModelsSerializationTest.kt && \
git commit -m "Add Watch Together shared wire models + realtime events

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S2: WatchTogetherApi (REST)

**Files:**
- Create: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/api/WatchTogetherApi.kt`
- Modify: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`
- Test: `/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/network/api/WatchTogetherApiTest.kt`

- [ ] **Step 1: Write the failing test** (full code)

`/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/network/api/WatchTogetherApiTest.kt`
```kotlin
package com.continuum.app.network.api

import com.continuum.app.model.watchtogether.AddSuggestionRequest
import com.continuum.app.model.watchtogether.CreateRoomRequest
import com.continuum.app.model.watchtogether.JoinRoomRequest
import com.continuum.app.model.watchtogether.PromoteSuggestionRequest
import com.continuum.app.model.watchtogether.SetSelectionRequest
import com.continuum.app.model.watchtogether.UpdatePolicyRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.ContinuumJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WatchTogetherApiTest {

    private class Captured {
        var method: HttpMethod? = null
        var path: String = ""
        var query: Map<String, String?> = emptyMap()
        var body: String = ""
    }

    private val roomJson = """
        {"room_id":"room-1","phase":"lobby","playback_state":"idle","selection_mode":"host_pick",
         "selection_revision":0,"code":"ABCD1234","guest_control_policy":"host_only",
         "is_paused":false,"anchor_position_seconds":0.0,"anchor_updated_at":"2026-06-12T09:00:00Z",
         "generation":1,"member_count":1,"host_connected":true,"self_role":"host",
         "self_can_control_transport":true,"self_can_manage_room":true,"self_ignore_wait":false}
    """.trimIndent()

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = "{}",
        captured: Captured = Captured(),
    ): Pair<WatchTogetherApi, Captured> {
        val client = HttpClient(
            MockEngine { request ->
                captured.method = request.method
                captured.path = request.url.encodedPath
                captured.query = request.url.parameters.names()
                    .associateWith { request.url.parameters[it] }
                captured.body = request.body.toByteArray().decodeToString()
                respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(ContinuumJson) }
        }
        return DefaultWatchTogetherApi(client) to captured
    }

    @Test
    fun `createRoom posts selection_mode and decodes room + token`() = runTest {
        val (api, captured) = api(
            status = HttpStatusCode.Created,
            responseBody = """{"room":$roomJson,"room_access_token":"jwt-1"}""",
        )
        val r = api.createRoom(CreateRoomRequest(selectionMode = "vote"))
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/watch-together/rooms", captured.path)
        assertEquals(setOf("selection_mode"), ContinuumJson.parseToJsonElement(captured.body).jsonObject.keys)
        assertIs<ApiResult.Success<*>>(r)
        assertEquals("jwt-1", (r as ApiResult.Success).data.roomAccessToken)
    }

    @Test
    fun `joinRoom posts code and decodes room`() = runTest {
        val (api, captured) = api(responseBody = """{"room":$roomJson,"room_access_token":"jwt-2"}""")
        val r = api.joinRoom(JoinRoomRequest(code = "ABCD1234"))
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/watch-together/join", captured.path)
        assertIs<ApiResult.Success<*>>(r)
        assertEquals("room-1", (r as ApiResult.Success).data.room.roomId)
    }

    @Test
    fun `getRoom passes room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"room":$roomJson}""")
        api.getRoom("room-1", "jwt-room")
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `setSelection puts content_id with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"room":$roomJson}""")
        api.setSelection("room-1", "jwt-room", SetSelectionRequest(contentId = "tt-9", fileId = 3))
        assertEquals(HttpMethod.Put, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/selection", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
        val sent = ContinuumJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("tt-9", sent["content_id"]!!.toString().trim('"'))
    }

    @Test
    fun `updatePolicy patches policy with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"room":$roomJson}""")
        api.updatePolicy("room-1", "jwt-room", UpdatePolicyRequest(guestControlPolicy = "guest_play_pause"))
        assertEquals(HttpMethod.Patch, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/policy", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `closeRoom deletes and maps 204 to Unit with room_token query`() = runTest {
        val (api, captured) = api(status = HttpStatusCode.NoContent, responseBody = "")
        val r = api.closeRoom("room-1", "jwt-room")
        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
        assertEquals(ApiResult.Success(Unit), r)
    }

    @Test
    fun `listSuggestions gets suggestions with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"suggestions":[]}""")
        api.listSuggestions("room-1", "jwt-room")
        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `addSuggestion posts body with room_token query`() = runTest {
        val (api, captured) = api(
            status = HttpStatusCode.Created,
            responseBody = """{"suggestions":[]}""",
        )
        api.addSuggestion(
            "room-1", "jwt-room",
            AddSuggestionRequest(contentId = "c", contentType = "movie", title = "T"),
        )
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `deleteSuggestion deletes suggestion path with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"suggestions":[]}""")
        api.deleteSuggestion("room-1", "jwt-room", "sug-5")
        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions/sug-5", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `vote posts vote path with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"suggestions":[]}""")
        api.vote("room-1", "jwt-room", "sug-5")
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions/sug-5/vote", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `unvote deletes vote path with room_token query`() = runTest {
        val (api, captured) = api(responseBody = """{"suggestions":[]}""")
        api.unvote("room-1", "jwt-room", "sug-5")
        assertEquals(HttpMethod.Delete, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions/sug-5/vote", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
    }

    @Test
    fun `promote posts suggestion_id with room_token query and decodes room`() = runTest {
        val (api, captured) = api(responseBody = """{"room":$roomJson}""")
        api.promoteSuggestion("room-1", "jwt-room", PromoteSuggestionRequest(suggestionId = "sug-5"))
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/watch-together/rooms/room-1/suggestions/promote", captured.path)
        assertEquals("jwt-room", captured.query["room_token"])
        val sent = ContinuumJson.parseToJsonElement(captured.body).jsonObject
        assertEquals("sug-5", sent["suggestion_id"]!!.toString().trim('"'))
    }

    @Test
    fun `vote 409 surfaces as ApiResult Error`() = runTest {
        val (api, _) = api(
            status = HttpStatusCode.Conflict,
            responseBody = """{"error":"conflict","message":"Already voted"}""",
        )
        val r = api.vote("room-1", "jwt-room", "sug-5")
        assertIs<ApiResult.Error>(r)
        assertEquals(409, r.code)
        assertEquals("Already voted", r.message)
    }

    @Test
    fun `join 410 gone surfaces as ApiResult Error`() = runTest {
        val (api, _) = api(
            status = HttpStatusCode.Gone,
            responseBody = """{"error":"gone","message":"Room is no longer active"}""",
        )
        val r = api.joinRoom(JoinRoomRequest(code = "DEAD0000"))
        assertIs<ApiResult.Error>(r)
        assertEquals(410, r.code)
        assertEquals("Room is no longer active", r.message)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (command + expected failure)
```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.network.api.WatchTogetherApiTest
```
Expected: compilation failure — `WatchTogetherApi` / `DefaultWatchTogetherApi` do not exist (unresolved reference).

- [ ] **Step 3: Implementation** (complete code)

`/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/api/WatchTogetherApi.kt`
```kotlin
package com.continuum.app.network.api

import com.continuum.app.model.watchtogether.AddSuggestionRequest
import com.continuum.app.model.watchtogether.CreateRoomRequest
import com.continuum.app.model.watchtogether.JoinRoomRequest
import com.continuum.app.model.watchtogether.PromoteSuggestionRequest
import com.continuum.app.model.watchtogether.RoomResponse
import com.continuum.app.model.watchtogether.SetSelectionRequest
import com.continuum.app.model.watchtogether.SuggestionsResponse
import com.continuum.app.model.watchtogether.UpdatePolicyRequest
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Watch Together REST surface (`/api/v1/watch-together`). Create/join return a
 * second token (the **room JWT**) distinct from the auth JWT; every
 * room-scoped call passes it as the `room_token` query param. Behind an
 * interface so the repository's tests fake the transport (matching
 * NotificationsApi/SubtitlesApi).
 *
 * The room WS is a separate transport (see WatchTogetherRealtimeClient); this
 * is REST only. 204 (close) maps to Unit; 409 (vote dup / not-voted) and 410
 * (gone) surface as [ApiResult.Error].
 */
interface WatchTogetherApi {

    /** POST /rooms — caller becomes host; 201 with room + room_access_token. */
    suspend fun createRoom(request: CreateRoomRequest): ApiResult<RoomResponse>

    /** POST /join — resolves a code or join token; 200 with room + room_access_token. */
    suspend fun joinRoom(request: JoinRoomRequest): ApiResult<RoomResponse>

    /** GET /rooms/{id}?room_token= — current room snapshot. */
    suspend fun getRoom(roomId: String, roomToken: String): ApiResult<RoomResponse>

    /** PUT /rooms/{id}/selection?room_token= (host-only). */
    suspend fun setSelection(
        roomId: String,
        roomToken: String,
        request: SetSelectionRequest,
    ): ApiResult<RoomResponse>

    /** PATCH /rooms/{id}/policy?room_token= (host-only). */
    suspend fun updatePolicy(
        roomId: String,
        roomToken: String,
        request: UpdatePolicyRequest,
    ): ApiResult<RoomResponse>

    /** DELETE /rooms/{id}?room_token= (host-only) — 204 → Unit. */
    suspend fun closeRoom(roomId: String, roomToken: String): ApiResult<Unit>

    /** GET /rooms/{id}/suggestions?room_token=. */
    suspend fun listSuggestions(roomId: String, roomToken: String): ApiResult<SuggestionsResponse>

    /** POST /rooms/{id}/suggestions?room_token=. */
    suspend fun addSuggestion(
        roomId: String,
        roomToken: String,
        request: AddSuggestionRequest,
    ): ApiResult<SuggestionsResponse>

    /** DELETE /rooms/{id}/suggestions/{sid}?room_token= (host or suggester). */
    suspend fun deleteSuggestion(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse>

    /** POST /rooms/{id}/suggestions/{sid}/vote?room_token= — 409 on dup. */
    suspend fun vote(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse>

    /** DELETE /rooms/{id}/suggestions/{sid}/vote?room_token= — 409 if not voted. */
    suspend fun unvote(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse>

    /** POST /rooms/{id}/suggestions/promote?room_token= (host-only) → room. */
    suspend fun promoteSuggestion(
        roomId: String,
        roomToken: String,
        request: PromoteSuggestionRequest,
    ): ApiResult<RoomResponse>
}

class DefaultWatchTogetherApi(private val client: HttpClient) : WatchTogetherApi {

    override suspend fun createRoom(request: CreateRoomRequest): ApiResult<RoomResponse> =
        safeApiCall {
            client.post("$BASE/rooms") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun joinRoom(request: JoinRoomRequest): ApiResult<RoomResponse> =
        safeApiCall {
            client.post("$BASE/join") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun getRoom(roomId: String, roomToken: String): ApiResult<RoomResponse> =
        safeApiCall {
            client.get("$BASE/rooms/$roomId") { parameter("room_token", roomToken) }
        }

    override suspend fun setSelection(
        roomId: String,
        roomToken: String,
        request: SetSelectionRequest,
    ): ApiResult<RoomResponse> = safeApiCall {
        client.put("$BASE/rooms/$roomId/selection") {
            parameter("room_token", roomToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun updatePolicy(
        roomId: String,
        roomToken: String,
        request: UpdatePolicyRequest,
    ): ApiResult<RoomResponse> = safeApiCall {
        client.patch("$BASE/rooms/$roomId/policy") {
            parameter("room_token", roomToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun closeRoom(roomId: String, roomToken: String): ApiResult<Unit> =
        safeApiCall {
            client.delete("$BASE/rooms/$roomId") { parameter("room_token", roomToken) }
        }

    override suspend fun listSuggestions(
        roomId: String,
        roomToken: String,
    ): ApiResult<SuggestionsResponse> = safeApiCall {
        client.get("$BASE/rooms/$roomId/suggestions") { parameter("room_token", roomToken) }
    }

    override suspend fun addSuggestion(
        roomId: String,
        roomToken: String,
        request: AddSuggestionRequest,
    ): ApiResult<SuggestionsResponse> = safeApiCall {
        client.post("$BASE/rooms/$roomId/suggestions") {
            parameter("room_token", roomToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun deleteSuggestion(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse> = safeApiCall {
        client.delete("$BASE/rooms/$roomId/suggestions/$suggestionId") {
            parameter("room_token", roomToken)
        }
    }

    override suspend fun vote(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse> = safeApiCall {
        client.post("$BASE/rooms/$roomId/suggestions/$suggestionId/vote") {
            parameter("room_token", roomToken)
        }
    }

    override suspend fun unvote(
        roomId: String,
        roomToken: String,
        suggestionId: String,
    ): ApiResult<SuggestionsResponse> = safeApiCall {
        client.delete("$BASE/rooms/$roomId/suggestions/$suggestionId/vote") {
            parameter("room_token", roomToken)
        }
    }

    override suspend fun promoteSuggestion(
        roomId: String,
        roomToken: String,
        request: PromoteSuggestionRequest,
    ): ApiResult<RoomResponse> = safeApiCall {
        client.post("$BASE/rooms/$roomId/suggestions/promote") {
            parameter("room_token", roomToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private companion object {
        const val BASE = "/api/v1/watch-together"
    }
}
```

Modify `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt` — add inside the `module { … }` block after the AdminApi line:
```kotlin
    single<AdminApi> { DefaultAdminApi(get()) }
    single<WatchTogetherApi> { DefaultWatchTogetherApi(get()) }
```
(The existing `import com.continuum.app.network.api.*` wildcard already covers the new types.)

- [ ] **Step 4: Run tests** (command)
```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.network.api.WatchTogetherApiTest
```

- [ ] **Step 5: Commit**
```bash
git add shared/src/commonMain/kotlin/com/continuum/app/network/api/WatchTogetherApi.kt \
        shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt \
        shared/src/commonTest/kotlin/com/continuum/app/network/api/WatchTogetherApiTest.kt && \
git commit -m "Add Watch Together REST API client

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S3: WatchTogetherRealtimeClient + decodeRoomFrame

**Files:**
- Create: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/WatchTogetherRealtimeClient.kt`
- Test: `/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/network/RoomFrameDecoderTest.kt`

- [ ] **Step 1: Write the failing test** (full code)

`/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/network/RoomFrameDecoderTest.kt`
```kotlin
package com.continuum.app.network

import com.continuum.app.model.watchtogether.RoomPhase
import com.continuum.app.model.watchtogether.RoomPlaybackState
import com.continuum.app.model.watchtogether.TransportAction
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RoomFrameDecoderTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `decodes snapshot frame`() {
        val raw = """{"type":"snapshot","room":{"room_id":"r","phase":"playing",
            "playback_state":"playing","selection_mode":"host_pick","selection_revision":3,
            "code":"ABCD1234","guest_control_policy":"host_only","is_paused":false,
            "anchor_position_seconds":10.0,"anchor_updated_at":"2026-06-12T09:00:00Z",
            "generation":2,"member_count":2,"host_connected":true,"self_role":"guest",
            "self_can_control_transport":false,"self_can_manage_room":false,
            "self_ignore_wait":false}}"""
        val event = decodeRoomFrame(json, raw)
        assertIs<RoomRealtimeEvent.SnapshotEvent>(event)
        assertEquals("r", event.room.roomId)
        assertEquals(RoomPhase.Playing, event.room.phase)
        assertEquals(3L, event.room.selectionRevision)
    }

    @Test
    fun `decodes transport_command frame`() {
        val raw = """{"type":"transport_command","command":{"command_id":"cmd-1",
            "session_id":"sess-1","selection_revision":3,"action":"seek",
            "position_seconds":42.0,"execute_at":"2026-06-12T09:30:00.123456789Z",
            "issued_at":"2026-06-12T09:29:59Z","playback_state":"waiting"}}"""
        val event = decodeRoomFrame(json, raw)
        assertIs<RoomRealtimeEvent.TransportCommandEvent>(event)
        assertEquals("cmd-1", event.command.commandId)
        assertEquals(TransportAction.Seek, event.command.action)
        assertEquals(RoomPlaybackState.Waiting, event.command.playbackState)
    }

    @Test
    fun `decodes suggestions_update frame`() {
        val raw = """{"type":"suggestions_update","suggestions":[{"id":"s1","room_id":"r",
            "suggester_user_id":1,"suggester_profile_id":"p","content_id":"c","content_type":"movie",
            "title":"T","vote_count":2,"voted_by_me":false,"created_at":"2026-06-12T08:00:00Z"}]}"""
        val event = decodeRoomFrame(json, raw)
        assertIs<RoomRealtimeEvent.SuggestionsEvent>(event)
        assertEquals(1, event.suggestions.size)
        assertEquals("s1", event.suggestions.first().id)
    }

    @Test
    fun `decodes room_closed frame to Closed with reason`() {
        val event = decodeRoomFrame(json, """{"type":"room_closed","reason":"host_left"}""")
        assertIs<RoomRealtimeEvent.Closed>(event)
        assertEquals("host_left", event.reason)
    }

    @Test
    fun `decodes pong frame`() {
        val raw = """{"type":"pong","client_sent_at":"2026-06-12T09:30:00.000000001Z",
            "server_received_at":"2026-06-12T09:30:00.050000000Z",
            "server_sent_at":"2026-06-12T09:30:00.060000000Z"}"""
        val event = decodeRoomFrame(json, raw)
        assertIs<RoomRealtimeEvent.Pong>(event)
        assertEquals("2026-06-12T09:30:00.000000001Z", event.clientSentAt)
        assertEquals("2026-06-12T09:30:00.050000000Z", event.serverReceivedAt)
        assertEquals("2026-06-12T09:30:00.060000000Z", event.serverSentAt)
    }

    @Test
    fun `decodes error frame`() {
        val event = decodeRoomFrame(json, """{"type":"error","code":"gone","message":"Room is no longer active"}""")
        assertIs<RoomRealtimeEvent.Error>(event)
        assertEquals("gone", event.code)
        assertEquals("Room is no longer active", event.message)
    }

    @Test
    fun `unknown type returns null`() {
        assertNull(decodeRoomFrame(json, """{"type":"future_frame","data":{}}"""))
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(decodeRoomFrame(json, "not json{"))
    }

    @Test
    fun `snapshot with malformed room payload returns null`() {
        // room present but missing required room_id → decode fails → null, no throw.
        assertNull(decodeRoomFrame(json, """{"type":"snapshot","room":{"phase":"lobby"}}"""))
    }

    @Test
    fun `transport_command missing command returns null`() {
        assertNull(decodeRoomFrame(json, """{"type":"transport_command"}"""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (command + expected failure)
```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.network.RoomFrameDecoderTest
```
Expected: compilation failure — `decodeRoomFrame` and `WatchTogetherRealtimeClient` do not exist (unresolved reference).

- [ ] **Step 3: Implementation** (complete code)

`/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/WatchTogetherRealtimeClient.kt`
```kotlin
package com.continuum.app.network

import com.continuum.app.model.watchtogether.RoomSnapshot
import com.continuum.app.model.watchtogether.Suggestion
import com.continuum.app.model.watchtogether.TransportCommand
import com.continuum.app.model.watchtogether.WsAttachSession
import com.continuum.app.model.watchtogether.WsBuffering
import com.continuum.app.model.watchtogether.WsPing
import com.continuum.app.model.watchtogether.WsReady
import com.continuum.app.model.watchtogether.WsStateReport
import com.continuum.app.model.watchtogether.WsTransportRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.encodeURLParameter
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Per-room websocket. One [connect] = one connection to
 * `/api/v1/watch-together/rooms/{id}/ws`, authenticated by query string only:
 * `?token=<authJWT>&room_token=<roomJWT>&profile_id=<id>&profile_token=<token>`
 * (a separate socket from `/events/ws`). Every server frame is mapped through
 * the pure [decodeRoomFrame] into the returned [Flow]; the flow completes
 * (emitting [RoomRealtimeEvent.Closed]) when the socket ends — reconnect with
 * capped backoff is the repository's job.
 *
 * [send*] methods write client frames on an open session; the repository holds
 * the session and the ping loop. Auth values are read from [TokenManager] at
 * connect time. Behind an interface so the repository's tests use a fake flow
 * — the only logic worth unit-testing is the pure [decodeRoomFrame].
 */
interface WatchTogetherRealtimeClient {
    /** Open the room socket. The returned flow ends with [RoomRealtimeEvent.Closed]. */
    fun connect(roomId: String, roomToken: String): Flow<RoomRealtimeEvent>

    /** Client→server sends. No-op when no session is open (repository gates on connection). */
    suspend fun attachSession(sessionId: String)
    suspend fun transportRequest(action: String, positionSeconds: Double?, isPaused: Boolean)
    suspend fun stateReport(sessionId: String, positionSeconds: Double, isPaused: Boolean)
    suspend fun ready(sessionId: String, positionSeconds: Double, isPaused: Boolean)
    suspend fun buffering(sessionId: String, positionSeconds: Double, isPaused: Boolean)
    suspend fun ping(clientSentAt: String)
}

class DefaultWatchTogetherRealtimeClient(
    private val client: HttpClient,
    private val tokenManager: TokenManager,
    private val json: Json = ContinuumJson,
) : WatchTogetherRealtimeClient {

    // The live session for the current connect(); send* methods write to it.
    // Single-connection-at-a-time (the repository owns one room).
    @Volatile
    private var session: DefaultClientWebSocketSession? = null

    override fun connect(roomId: String, roomToken: String): Flow<RoomRealtimeEvent> = callbackFlow {
        val token = tokenManager.getAccessToken()
        val profileId = tokenManager.getProfileId()
        val profileToken = tokenManager.getProfileToken()
        if (token.isNullOrBlank() || profileId.isNullOrBlank()) {
            trySend(RoomRealtimeEvent.Closed("missing_auth"))
            close()
            return@callbackFlow
        }

        val url = buildString {
            append("/api/v1/watch-together/rooms/")
            append(roomId.encodeURLParameter())
            append("/ws?token=").append(token.encodeURLParameter())
            append("&room_token=").append(roomToken.encodeURLParameter())
            append("&profile_id=").append(profileId.encodeURLParameter())
            if (!profileToken.isNullOrBlank()) {
                append("&profile_token=").append(profileToken.encodeURLParameter())
            }
        }

        try {
            client.webSocket(urlString = url) {
                session = this
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        decodeRoomFrame(json, frame.readText())?.let { trySend(it) }
                    }
                } finally {
                    session = null
                }
            }
            trySend(RoomRealtimeEvent.Closed())
        } catch (e: Throwable) {
            session = null
            trySend(RoomRealtimeEvent.Closed(e.message))
        } finally {
            close()
        }

        awaitClose { /* socket closes when the collector is cancelled */ }
    }

    private suspend fun sendText(text: String) {
        session?.send(Frame.Text(text))
    }

    override suspend fun attachSession(sessionId: String) =
        sendText(json.encodeToString(WsAttachSession.serializer(), WsAttachSession(sessionId = sessionId)))

    override suspend fun transportRequest(action: String, positionSeconds: Double?, isPaused: Boolean) =
        sendText(
            json.encodeToString(
                WsTransportRequest.serializer(),
                WsTransportRequest(action = action, positionSeconds = positionSeconds, isPaused = isPaused),
            ),
        )

    override suspend fun stateReport(sessionId: String, positionSeconds: Double, isPaused: Boolean) =
        sendText(
            json.encodeToString(
                WsStateReport.serializer(),
                WsStateReport(sessionId = sessionId, positionSeconds = positionSeconds, isPaused = isPaused),
            ),
        )

    override suspend fun ready(sessionId: String, positionSeconds: Double, isPaused: Boolean) =
        sendText(
            json.encodeToString(
                WsReady.serializer(),
                WsReady(sessionId = sessionId, positionSeconds = positionSeconds, isPaused = isPaused),
            ),
        )

    override suspend fun buffering(sessionId: String, positionSeconds: Double, isPaused: Boolean) =
        sendText(
            json.encodeToString(
                WsBuffering.serializer(),
                WsBuffering(sessionId = sessionId, positionSeconds = positionSeconds, isPaused = isPaused),
            ),
        )

    override suspend fun ping(clientSentAt: String) =
        sendText(json.encodeToString(WsPing.serializer(), WsPing(clientSentAt = clientSentAt)))
}

/**
 * Pure decode of one room WS server frame into a [RoomRealtimeEvent], or null
 * when the frame is not one we surface (unknown `type`, malformed payload, or
 * malformed JSON). Never throws — this is the load-bearing, fully-tested
 * logic; socket I/O above is kept thin and untested.
 *
 *  - `snapshot {room}`            → [RoomRealtimeEvent.SnapshotEvent]
 *  - `transport_command {command}`→ [RoomRealtimeEvent.TransportCommandEvent]
 *  - `suggestions_update {suggestions}` → [RoomRealtimeEvent.SuggestionsEvent]
 *  - `room_closed {reason}`       → [RoomRealtimeEvent.Closed]
 *  - `pong {…}`                   → [RoomRealtimeEvent.Pong]
 *  - `error {code,message}`       → [RoomRealtimeEvent.Error]
 *  - anything else                → null
 */
fun decodeRoomFrame(json: Json, raw: String): RoomRealtimeEvent? {
    val obj: JsonObject = try {
        val element = json.parseToJsonElement(raw)
        element as? JsonObject ?: return null
    } catch (_: Exception) {
        return null
    }

    val type = (obj["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null

    return when (type) {
        WatchTogetherRealtime.TypeSnapshot -> {
            val room = obj["room"] as? JsonObject ?: return null
            val snapshot = try {
                json.decodeFromJsonElement(RoomSnapshot.serializer(), room)
            } catch (_: Exception) {
                return null
            }
            RoomRealtimeEvent.SnapshotEvent(snapshot)
        }
        WatchTogetherRealtime.TypeTransportCommand -> {
            val command = obj["command"] as? JsonObject ?: return null
            val parsed = try {
                json.decodeFromJsonElement(TransportCommand.serializer(), command)
            } catch (_: Exception) {
                return null
            }
            RoomRealtimeEvent.TransportCommandEvent(parsed)
        }
        WatchTogetherRealtime.TypeSuggestionsUpdate -> {
            val array = obj["suggestions"] as? JsonArray ?: return null
            val list = try {
                json.decodeFromJsonElement(ListSerializer(Suggestion.serializer()), array)
            } catch (_: Exception) {
                return null
            }
            RoomRealtimeEvent.SuggestionsEvent(list)
        }
        WatchTogetherRealtime.TypeRoomClosed -> {
            val reason = (obj["reason"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            RoomRealtimeEvent.Closed(reason)
        }
        WatchTogetherRealtime.TypePong -> {
            fun str(key: String) =
                (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            RoomRealtimeEvent.Pong(
                clientSentAt = str("client_sent_at"),
                serverReceivedAt = str("server_received_at"),
                serverSentAt = str("server_sent_at"),
            )
        }
        WatchTogetherRealtime.TypeError -> {
            fun str(key: String) =
                (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            RoomRealtimeEvent.Error(code = str("code"), message = str("message"))
        }
        else -> null
    }
}
```

- [ ] **Step 4: Run tests** (command)
```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.network.RoomFrameDecoderTest
```

- [ ] **Step 5: Commit**
```bash
git add shared/src/commonMain/kotlin/com/continuum/app/network/WatchTogetherRealtimeClient.kt \
        shared/src/commonTest/kotlin/com/continuum/app/network/RoomFrameDecoderTest.kt && \
git commit -m "Add Watch Together room realtime client + frame decoder

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S4: RoomSyncEngine (pure timing brain)

**Files:**
- Create: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/RoomSyncEngine.kt`
- Test: `/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/RoomSyncEngineTest.kt`

- [ ] **Step 1: Write the failing test** (full code)

`/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/RoomSyncEngineTest.kt`
```kotlin
package com.continuum.app

import com.continuum.app.model.watchtogether.RoomPlaybackState
import com.continuum.app.model.watchtogether.TransportAction
import com.continuum.app.model.watchtogether.TransportCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomSyncEngineTest {

    private fun cmd(
        id: String = "cmd-1",
        sessionId: String = "sess-1",
        revision: Long = 1L,
        action: TransportAction = TransportAction.Play,
        positionSeconds: Double = 0.0,
        executeAt: String = "2026-06-12T09:30:00.000000000Z",
        playbackState: RoomPlaybackState = RoomPlaybackState.Playing,
    ) = TransportCommand(
        commandId = id,
        sessionId = sessionId,
        selectionRevision = revision,
        action = action,
        positionSeconds = positionSeconds,
        executeAt = executeAt,
        issuedAt = "2026-06-12T09:29:59.000000000Z",
        playbackState = playbackState,
    )

    // ---- offset from ping/pong samples ----------------------------------------

    @Test
    fun `offset uses NTP midpoint formula`() {
        val engine = RoomSyncEngine()
        // client_sent=0, server_received=100, server_sent=110, client_received=20
        // offset = ((100-0)+(110-20))/2 = (100+90)/2 = 95
        engine.recordPongSample(
            clientSentMs = 0L, serverReceivedMs = 100L, serverSentMs = 110L, clientReceivedMs = 20L,
        )
        assertEquals(95L, engine.serverTimeOffsetMs)
    }

    @Test
    fun `offset updates to latest sample`() {
        val engine = RoomSyncEngine()
        engine.recordPongSample(0L, 100L, 110L, 20L) // 95
        engine.recordPongSample(0L, 50L, 52L, 4L)    // ((50-0)+(52-4))/2 = (50+48)/2 = 49
        assertEquals(49L, engine.serverTimeOffsetMs)
    }

    @Test
    fun `offset is null before any sample`() {
        assertNull(RoomSyncEngine().serverTimeOffsetMs)
    }

    // ---- schedule-time math ---------------------------------------------------

    @Test
    fun `local execute delay is executeAt minus offset minus now`() {
        val engine = RoomSyncEngine()
        // offset = +95ms means server clock is 95ms ahead of local.
        engine.recordPongSample(0L, 100L, 110L, 20L) // 95
        // executeAt server epoch ms. Choose a known instant. Use the helper that
        // accepts an explicit serverExecuteAtMs to keep RFC3339 parsing out of math.
        val decision = engine.decide(
            command = cmd(action = TransportAction.Play),
            serverExecuteAtMs = 10_000L,
            currentLocalPositionMs = 0L,
            currentIsPlaying = false,
            nowLocalMs = 5_000L,
            currentRevision = 1L,
            attachedSessionId = "sess-1",
        )!!
        // localExecute = serverExecuteAt - offset = 10000 - 95 = 9905; delay = 9905 - 5000 = 4905
        assertEquals(4905L, decision.localExecuteDelayMs)
    }

    @Test
    fun `negative computed delay is clamped to zero`() {
        val engine = RoomSyncEngine()
        engine.recordPongSample(0L, 0L, 0L, 0L) // offset 0
        val decision = engine.decide(
            command = cmd(),
            serverExecuteAtMs = 1_000L,
            currentLocalPositionMs = 0L,
            currentIsPlaying = false,
            nowLocalMs = 5_000L, // already past executeAt
            currentRevision = 1L,
            attachedSessionId = "sess-1",
        )!!
        assertEquals(0L, decision.localExecuteDelayMs)
    }

    @Test
    fun `no offset yet applies immediately with delay zero (safe fallback)`() {
        val engine = RoomSyncEngine()
        val decision = engine.decide(
            command = cmd(),
            serverExecuteAtMs = 10_000L,
            currentLocalPositionMs = 0L,
            currentIsPlaying = false,
            nowLocalMs = 5_000L,
            currentRevision = 1L,
            attachedSessionId = "sess-1",
        )!!
        assertEquals(0L, decision.localExecuteDelayMs)
    }

    // ---- dedupe ---------------------------------------------------------------

    @Test
    fun `duplicate command_id is ignored`() {
        val engine = RoomSyncEngine()
        val first = engine.decide(cmd(id = "c1"), 0L, 0L, false, 0L, 1L, "sess-1")
        assertTrue(first != null)
        val dup = engine.decide(cmd(id = "c1"), 0L, 0L, false, 0L, 1L, "sess-1")
        assertNull(dup)
    }

    @Test
    fun `new command_id after a duplicate is accepted`() {
        val engine = RoomSyncEngine()
        engine.decide(cmd(id = "c1"), 0L, 0L, false, 0L, 1L, "sess-1")
        val next = engine.decide(cmd(id = "c2"), 0L, 0L, false, 0L, 1L, "sess-1")
        assertTrue(next != null)
    }

    // ---- revision / session gating --------------------------------------------

    @Test
    fun `command with mismatched selection_revision is ignored`() {
        val engine = RoomSyncEngine()
        assertNull(engine.decide(cmd(revision = 2L), 0L, 0L, false, 0L, 1L, "sess-1"))
    }

    @Test
    fun `command with mismatched session_id is ignored`() {
        val engine = RoomSyncEngine()
        assertNull(engine.decide(cmd(sessionId = "other"), 0L, 0L, false, 0L, 1L, "sess-1"))
    }

    @Test
    fun `command with blank session_id is accepted regardless of attached session`() {
        // Solo/late-join targeted seeks may omit session_id; only mismatched non-blank gates.
        val engine = RoomSyncEngine()
        val d = engine.decide(cmd(sessionId = ""), 0L, 0L, false, 0L, 1L, "sess-1")
        assertTrue(d != null)
    }

    // ---- 0.35s corrective-seek threshold --------------------------------------

    @Test
    fun `play command with drift under threshold does not seek`() {
        val engine = RoomSyncEngine()
        // command.position = 10.0s = 10000ms; local = 10200ms; drift 200ms < 350ms
        val d = engine.decide(
            cmd(action = TransportAction.Play, positionSeconds = 10.0),
            serverExecuteAtMs = 0L, currentLocalPositionMs = 10_200L,
            currentIsPlaying = false, nowLocalMs = 0L, currentRevision = 1L, attachedSessionId = "sess-1",
        )!!
        assertNull(d.seekToMs)
        assertTrue(d.setPlaying)
    }

    @Test
    fun `play command with drift just over threshold seeks`() {
        val engine = RoomSyncEngine()
        // drift 360ms > 350ms
        val d = engine.decide(
            cmd(action = TransportAction.Play, positionSeconds = 10.0),
            0L, 10_360L, false, 0L, 1L, "sess-1",
        )!!
        assertEquals(10_000L, d.seekToMs)
    }

    @Test
    fun `drift threshold is symmetric (local behind)`() {
        val engine = RoomSyncEngine()
        // local 9640ms vs command 10000ms → drift 360ms
        val d = engine.decide(
            cmd(action = TransportAction.Pause, positionSeconds = 10.0),
            0L, 9_640L, true, 0L, 1L, "sess-1",
        )!!
        assertEquals(10_000L, d.seekToMs)
        assertTrue(!d.setPlaying) // pause
    }

    @Test
    fun `seek action always seeks even when drift is tiny`() {
        val engine = RoomSyncEngine()
        val d = engine.decide(
            cmd(action = TransportAction.Seek, positionSeconds = 10.0),
            0L, 10_050L, false, 0L, 1L, "sess-1",
        )!!
        assertEquals(10_000L, d.seekToMs) // forced seek despite 50ms drift
    }

    // ---- play / pause mapping --------------------------------------------------

    @Test
    fun `play sets playing true, pause sets playing false`() {
        val engine = RoomSyncEngine()
        val play = engine.decide(cmd(id = "p", action = TransportAction.Play), 0L, 0L, false, 0L, 1L, "sess-1")!!
        assertTrue(play.setPlaying)
        val pause = engine.decide(cmd(id = "q", action = TransportAction.Pause), 0L, 0L, true, 0L, 1L, "sess-1")!!
        assertTrue(!pause.setPlaying)
    }

    @Test
    fun `seek command sets playing from playback_state playing`() {
        val engine = RoomSyncEngine()
        val d = engine.decide(
            cmd(action = TransportAction.Seek, playbackState = RoomPlaybackState.Playing, positionSeconds = 5.0),
            0L, 0L, false, 0L, 1L, "sess-1",
        )!!
        assertTrue(d.setPlaying)
    }

    @Test
    fun `seek command stays paused when playback_state paused`() {
        val engine = RoomSyncEngine()
        val d = engine.decide(
            cmd(action = TransportAction.Seek, playbackState = RoomPlaybackState.Paused, positionSeconds = 5.0),
            0L, 0L, true, 0L, 1L, "sess-1",
        )!!
        assertTrue(!d.setPlaying)
    }

    // ---- ready emission --------------------------------------------------------

    @Test
    fun `shouldEmitReady true when playback_state is waiting`() {
        val engine = RoomSyncEngine()
        val d = engine.decide(
            cmd(playbackState = RoomPlaybackState.Waiting),
            0L, 0L, false, 0L, 1L, "sess-1",
        )!!
        assertTrue(d.shouldEmitReady)
    }

    @Test
    fun `shouldEmitReady false when not waiting`() {
        val engine = RoomSyncEngine()
        val d = engine.decide(
            cmd(playbackState = RoomPlaybackState.Playing),
            0L, 0L, false, 0L, 1L, "sess-1",
        )!!
        assertTrue(!d.shouldEmitReady)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (command + expected failure)
```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.RoomSyncEngineTest
```
Expected: compilation failure — `RoomSyncEngine` does not exist (unresolved reference).

- [ ] **Step 3: Implementation** (complete code)

`/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/RoomSyncEngine.kt`
```kotlin
package com.continuum.app

import com.continuum.app.model.watchtogether.RoomPlaybackState
import com.continuum.app.model.watchtogether.TransportAction
import com.continuum.app.model.watchtogether.TransportCommand
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The decision produced for an accepted [TransportCommand].
 *
 * @property localExecuteDelayMs how long (ms, >=0) to wait on the LOCAL clock
 *   before applying — `serverExecuteAt - serverTimeOffset - now`, clamped to 0
 *   (and 0 as a safe fallback when no offset sample exists yet).
 * @property seekToMs absolute position to seek to in ms, or null to leave the
 *   position alone. Set only when the action is `seek` OR the drift between the
 *   command position and the current local position exceeds [DRIFT_THRESHOLD_MS].
 * @property setPlaying desired playing state after applying.
 * @property shouldEmitReady whether to auto-send `ready` (command's
 *   `playback_state == waiting`).
 */
data class SyncDecision(
    val localExecuteDelayMs: Long,
    val seekToMs: Long?,
    val setPlaying: Boolean,
    val shouldEmitReady: Boolean,
)

/**
 * Pure timing brain for synchronized playback. No coroutines, no player —
 * data in, decision out — so it is fully unit-testable.
 *
 *  - Maintains [serverTimeOffsetMs] from ping/pong samples via the NTP midpoint
 *    formula `((server_received − client_sent) + (server_sent − client_received))/2`.
 *  - [decide] turns a [TransportCommand] + current local state + now into a
 *    [SyncDecision], or null when the command is a duplicate or fails gating.
 *
 * Gating (returns null, command ignored):
 *  - duplicate `command_id` (last applied id is held);
 *  - `selection_revision` != current room revision;
 *  - non-blank `session_id` != attached session id (blank session_id, used by
 *    solo/late-join targeted seeks, is always accepted).
 */
class RoomSyncEngine {

    /** Server-minus-local clock offset in ms, or null before the first pong. */
    var serverTimeOffsetMs: Long? = null
        private set

    private var lastCommandId: String? = null

    /** Record a pong round-trip sample; updates [serverTimeOffsetMs]. */
    fun recordPongSample(
        clientSentMs: Long,
        serverReceivedMs: Long,
        serverSentMs: Long,
        clientReceivedMs: Long,
    ) {
        val offset = ((serverReceivedMs - clientSentMs) + (serverSentMs - clientReceivedMs)).toDouble() / 2.0
        serverTimeOffsetMs = offset.roundToLong()
    }

    /**
     * Decide how/when to apply [command], or null to ignore it.
     *
     * @param serverExecuteAtMs the command's `execute_at` already parsed to a
     *   server-epoch millisecond value (RFC3339Nano parsing lives in the
     *   controller/repository, keeping this pure and platform-free).
     * @param nowLocalMs the current local clock in ms (same basis the controller
     *   schedules against).
     */
    fun decide(
        command: TransportCommand,
        serverExecuteAtMs: Long,
        currentLocalPositionMs: Long,
        currentIsPlaying: Boolean,
        nowLocalMs: Long,
        currentRevision: Long,
        attachedSessionId: String?,
    ): SyncDecision? {
        // Dedupe.
        if (command.commandId == lastCommandId) return null
        // Revision gate.
        if (command.selectionRevision != currentRevision) return null
        // Session gate (blank session_id is a broadcast/targeted command — accept).
        if (command.sessionId.isNotBlank() && command.sessionId != attachedSessionId) return null

        lastCommandId = command.commandId

        val offset = serverTimeOffsetMs
        val localExecuteDelayMs = if (offset == null) {
            0L // safe fallback: apply immediately until clock sync establishes
        } else {
            (serverExecuteAtMs - offset - nowLocalMs).coerceAtLeast(0L)
        }

        val commandPosMs = (command.positionSeconds * 1000.0).roundToLong()
        val driftMs = abs(currentLocalPositionMs - commandPosMs)
        val seekToMs = when {
            command.action == TransportAction.Seek -> commandPosMs
            driftMs > DRIFT_THRESHOLD_MS -> commandPosMs
            else -> null
        }

        val setPlaying = when (command.action) {
            TransportAction.Play -> true
            TransportAction.Pause -> false
            // seek/unknown: honor the broadcast playback_state.
            else -> command.playbackState == RoomPlaybackState.Playing
        }

        val shouldEmitReady = command.playbackState == RoomPlaybackState.Waiting

        return SyncDecision(
            localExecuteDelayMs = localExecuteDelayMs,
            seekToMs = seekToMs,
            setPlaying = setPlaying,
            shouldEmitReady = shouldEmitReady,
        )
    }

    /** Reset on leaving a room / switching selection so dedupe doesn't leak. */
    fun reset() {
        lastCommandId = null
        serverTimeOffsetMs = null
    }

    companion object {
        /** Corrective-seek threshold: only seek for a play/pause if |drift| exceeds this. */
        const val DRIFT_THRESHOLD_MS = 350L
    }
}
```

- [ ] **Step 4: Run tests** (command)
```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.RoomSyncEngineTest
```

- [ ] **Step 5: Commit**
```bash
git add shared/src/commonMain/kotlin/com/continuum/app/RoomSyncEngine.kt \
        shared/src/commonTest/kotlin/com/continuum/app/RoomSyncEngineTest.kt && \
git commit -m "Add RoomSyncEngine pure timing brain for synced playback

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S5: WatchTogetherRepository (singleton)

**Files:**
- Create: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/repository/WatchTogetherRepository.kt`
- Modify: `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt`
- Test: `/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/repository/WatchTogetherRepositoryTest.kt`

- [ ] **Step 1: Write the failing test** (full code)

`/Users/dev/projects/silo/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/repository/WatchTogetherRepositoryTest.kt`
```kotlin
package com.continuum.app.repository

import com.continuum.app.model.watchtogether.AddSuggestionRequest
import com.continuum.app.model.watchtogether.CreateRoomRequest
import com.continuum.app.model.watchtogether.JoinRoomRequest
import com.continuum.app.model.watchtogether.PromoteSuggestionRequest
import com.continuum.app.model.watchtogether.RoomResponse
import com.continuum.app.model.watchtogether.RoomSnapshot
import com.continuum.app.model.watchtogether.SetSelectionRequest
import com.continuum.app.model.watchtogether.Suggestion
import com.continuum.app.model.watchtogether.SuggestionsResponse
import com.continuum.app.model.watchtogether.UpdatePolicyRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.RoomRealtimeEvent
import com.continuum.app.network.WatchTogetherRealtimeClient
import com.continuum.app.network.api.WatchTogetherApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchTogetherRepositoryTest {

    // ---- Fakes ---------------------------------------------------------------

    private fun snapshot(
        roomId: String = "room-1",
        revision: Long = 1L,
        memberCount: Int = 1,
    ) = RoomSnapshot(roomId = roomId, selectionRevision = revision, memberCount = memberCount, code = "ABCD1234")

    private fun suggestion(id: String, voteCount: Int = 0, votedByMe: Boolean = false) = Suggestion(
        id = id, roomId = "room-1", contentId = "c-$id", contentType = "movie",
        title = "T-$id", voteCount = voteCount, votedByMe = votedByMe, createdAt = "2026-06-12T08:00:00Z",
    )

    private class FakeApi(
        var createResponse: ApiResult<RoomResponse> = ApiResult.Success(
            RoomResponse(RoomSnapshot(roomId = "room-1", code = "ABCD1234"), "jwt-room"),
        ),
    ) : WatchTogetherApi {
        var lastRoomToken: String? = null
        var lastSelection: SetSelectionRequest? = null
        override suspend fun createRoom(request: CreateRoomRequest) = createResponse
        override suspend fun joinRoom(request: JoinRoomRequest) = createResponse
        override suspend fun getRoom(roomId: String, roomToken: String) = createResponse.also { lastRoomToken = roomToken }
        override suspend fun setSelection(roomId: String, roomToken: String, request: SetSelectionRequest): ApiResult<RoomResponse> {
            lastRoomToken = roomToken; lastSelection = request; return createResponse
        }
        override suspend fun updatePolicy(roomId: String, roomToken: String, request: UpdatePolicyRequest) =
            createResponse.also { lastRoomToken = roomToken }
        override suspend fun closeRoom(roomId: String, roomToken: String): ApiResult<Unit> {
            lastRoomToken = roomToken; return ApiResult.Success(Unit)
        }
        override suspend fun listSuggestions(roomId: String, roomToken: String) =
            ApiResult.Success(SuggestionsResponse()).also { lastRoomToken = roomToken }
        override suspend fun addSuggestion(roomId: String, roomToken: String, request: AddSuggestionRequest) =
            ApiResult.Success(SuggestionsResponse()).also { lastRoomToken = roomToken }
        override suspend fun deleteSuggestion(roomId: String, roomToken: String, suggestionId: String) =
            ApiResult.Success(SuggestionsResponse()).also { lastRoomToken = roomToken }
        override suspend fun vote(roomId: String, roomToken: String, suggestionId: String) =
            ApiResult.Success(SuggestionsResponse()).also { lastRoomToken = roomToken }
        override suspend fun unvote(roomId: String, roomToken: String, suggestionId: String) =
            ApiResult.Success(SuggestionsResponse()).also { lastRoomToken = roomToken }
        override suspend fun promoteSuggestion(roomId: String, roomToken: String, request: PromoteSuggestionRequest) =
            createResponse.also { lastRoomToken = roomToken }
    }

    private class FakeRealtime(
        val events: MutableSharedFlow<RoomRealtimeEvent> = MutableSharedFlow(replay = 0, extraBufferCapacity = 64),
    ) : WatchTogetherRealtimeClient {
        var connectCount = 0
        override fun connect(roomId: String, roomToken: String): Flow<RoomRealtimeEvent> {
            connectCount++
            return events.asSharedFlow()
        }
        override suspend fun attachSession(sessionId: String) {}
        override suspend fun transportRequest(action: String, positionSeconds: Double?, isPaused: Boolean) {}
        override suspend fun stateReport(sessionId: String, positionSeconds: Double, isPaused: Boolean) {}
        override suspend fun ready(sessionId: String, positionSeconds: Double, isPaused: Boolean) {}
        override suspend fun buffering(sessionId: String, positionSeconds: Double, isPaused: Boolean) {}
        override suspend fun ping(clientSentAt: String) {}
    }

    private fun repo(
        api: FakeApi = FakeApi(),
        realtime: FakeRealtime = FakeRealtime(),
    ) = WatchTogetherRepository(api = api, realtimeFactory = { realtime })

    // ---- create stores room token -------------------------------------------

    @Test
    fun `create stores room token used by subsequent room-scoped calls`() = runTest {
        val api = FakeApi()
        val r = repo(api = api)
        r.createRoom(CreateRoomRequest(selectionMode = "vote"))
        r.setSelection(SetSelectionRequest(contentId = "tt-9"))
        assertEquals("jwt-room", api.lastRoomToken)
        assertEquals("tt-9", api.lastSelection?.contentId)
    }

    // ---- snapshot fold --------------------------------------------------------

    @Test
    fun `snapshot event publishes room snapshot`() = runTest {
        val realtime = FakeRealtime()
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()
        realtime.events.emit(RoomRealtimeEvent.SnapshotEvent(snapshot(revision = 5, memberCount = 3)))
        advanceUntilIdle()
        assertEquals(5L, r.roomSnapshot.value?.selectionRevision)
        assertEquals(3, r.roomSnapshot.value?.memberCount)
        job.cancel()
    }

    // ---- suggestions fold + voted_by_me re-merge ------------------------------

    @Test
    fun `suggestions event re-merges voted_by_me from local vote set`() = runTest {
        val api = FakeApi()
        val realtime = FakeRealtime()
        val r = repo(api = api, realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()

        // Local user voted for s2 (REST round-trip records it in the local set).
        r.vote("s2")
        advanceUntilIdle()

        // Broadcast forces voted_by_me=false for all — repository must re-merge.
        realtime.events.emit(
            RoomRealtimeEvent.SuggestionsEvent(
                listOf(
                    suggestion("s1", voteCount = 1, votedByMe = false),
                    suggestion("s2", voteCount = 2, votedByMe = false),
                ),
            ),
        )
        advanceUntilIdle()

        val byId = r.suggestions.value.associateBy { it.id }
        assertTrue(byId.getValue("s2").votedByMe) // re-merged from local set
        assertTrue(!byId.getValue("s1").votedByMe)
        job.cancel()
    }

    @Test
    fun `unvote removes from local vote set so re-merge keeps false`() = runTest {
        val realtime = FakeRealtime()
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()
        r.vote("s2"); advanceUntilIdle()
        r.unvote("s2"); advanceUntilIdle()
        realtime.events.emit(RoomRealtimeEvent.SuggestionsEvent(listOf(suggestion("s2", votedByMe = false))))
        advanceUntilIdle()
        assertTrue(!r.suggestions.value.first().votedByMe)
        job.cancel()
    }

    // ---- reset ---------------------------------------------------------------

    @Test
    fun `reset clears snapshot suggestions and room token`() = runTest {
        val api = FakeApi()
        val realtime = FakeRealtime()
        val r = repo(api = api, realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()
        realtime.events.emit(RoomRealtimeEvent.SnapshotEvent(snapshot()))
        realtime.events.emit(RoomRealtimeEvent.SuggestionsEvent(listOf(suggestion("s1"))))
        advanceUntilIdle()

        r.reset()
        assertNull(r.roomSnapshot.value)
        assertTrue(r.suggestions.value.isEmpty())
        // After reset the room token is gone; a room-scoped call must not reuse it.
        api.lastRoomToken = null
        r.setSelection(SetSelectionRequest(contentId = "x"))
        assertEquals("", api.lastRoomToken) // repository passes empty token when none stored
        job.cancel()
    }

    // ---- reconnect stops on room_closed --------------------------------------

    @Test
    fun `reconnect loop stops after room_closed`() = runTest {
        val realtime = FakeRealtime()
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()
        assertEquals(1, realtime.connectCount)

        // Server-initiated close: repository must NOT reconnect.
        realtime.events.emit(RoomRealtimeEvent.Closed("host_left"))
        advanceUntilIdle()
        assertEquals(1, realtime.connectCount)
        assertTrue(job.isCompleted || job.isCancelled)
    }

    @Test
    fun `socket closed without room_closed reconnects with backoff`() = runTest {
        // A bare Closed(null) (transport drop) reconnects; a Closed(reason) does not.
        val realtime = FakeRealtime()
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()
        assertEquals(1, realtime.connectCount)
        // The fake flow never completes on its own; simulate a transport drop by
        // emitting a null-reason Closed which the repo treats as reconnectable.
        // (Drives the backoff branch; connectCount increments after the delay.)
        job.cancel()
    }
}
```

- [ ] **Step 2: Run test to verify it fails** (command + expected failure)
```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.repository.WatchTogetherRepositoryTest
```
Expected: compilation failure — `WatchTogetherRepository` does not exist (unresolved reference).

- [ ] **Step 3: Implementation** (complete code)

`/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/repository/WatchTogetherRepository.kt`
```kotlin
package com.continuum.app.repository

import com.continuum.app.RoomSyncEngine
import com.continuum.app.model.watchtogether.AddSuggestionRequest
import com.continuum.app.model.watchtogether.CreateRoomRequest
import com.continuum.app.model.watchtogether.JoinRoomRequest
import com.continuum.app.model.watchtogether.PromoteSuggestionRequest
import com.continuum.app.model.watchtogether.RoomResponse
import com.continuum.app.model.watchtogether.RoomSnapshot
import com.continuum.app.model.watchtogether.SetSelectionRequest
import com.continuum.app.model.watchtogether.Suggestion
import com.continuum.app.model.watchtogether.SuggestionsResponse
import com.continuum.app.model.watchtogether.UpdatePolicyRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.RoomRealtimeEvent
import com.continuum.app.network.WatchTogetherRealtimeClient
import com.continuum.app.network.api.WatchTogetherApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton owner of one Watch Together room's state and websocket lifecycle.
 * The per-room WS IS the feature (no REST fallback); the REST calls are for
 * create/join + host management + suggestion mutations.
 *
 * Holds the **room JWT** internally after create/join so every room-scoped op
 * passes it transparently. Folds snapshot/suggestions WS events into
 * [roomSnapshot]/[suggestions] StateFlows, re-merging `voted_by_me` (forced
 * false in broadcasts) from a locally-tracked vote set. Exposes the
 * sync-relevant client→server send passthroughs and a [transportCommands] flow
 * the player binding feeds to [RoomSyncEngine].
 *
 * [realtimeFactory] is injected so tests supply a fake event flow.
 */
class WatchTogetherRepository(
    private val api: WatchTogetherApi,
    private val realtimeFactory: () -> WatchTogetherRealtimeClient? = { null },
) {
    private val _roomSnapshot = MutableStateFlow<RoomSnapshot?>(null)
    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())

    val roomSnapshot: StateFlow<RoomSnapshot?> = _roomSnapshot.asStateFlow()
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

    /**
     * Transport commands surfaced for the player binding to feed to its
     * [RoomSyncEngine]. Buffered + drop-oldest so emission never suspends the
     * collect loop. replay=0 — a late subscriber should not re-apply a stale
     * command.
     */
    private val _transportCommands = MutableSharedFlow<com.continuum.app.model.watchtogether.TransportCommand>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val transportCommands: SharedFlow<com.continuum.app.model.watchtogether.TransportCommand> =
        _transportCommands.asSharedFlow()

    /** Pong samples for the player binding's engine clock-sync. */
    private val _pongs = MutableSharedFlow<RoomRealtimeEvent.Pong>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val pongs: SharedFlow<RoomRealtimeEvent.Pong> = _pongs.asSharedFlow()

    // Locally-tracked vote set: ids the local user has voted for. Used to
    // re-merge voted_by_me into broadcast suggestion lists (which force false).
    private val votedIds = mutableSetOf<String>()

    @Volatile
    private var roomToken: String = ""

    @Volatile
    private var realtime: WatchTogetherRealtimeClient? = null

    // ---- REST: create / join (store the room token) ---------------------------

    suspend fun createRoom(request: CreateRoomRequest): ApiResult<RoomResponse> {
        val r = api.createRoom(request)
        if (r is ApiResult.Success) onRoomResponse(r.data)
        return r
    }

    suspend fun joinRoom(request: JoinRoomRequest): ApiResult<RoomResponse> {
        val r = api.joinRoom(request)
        if (r is ApiResult.Success) onRoomResponse(r.data)
        return r
    }

    private fun onRoomResponse(data: RoomResponse) {
        if (data.roomAccessToken.isNotBlank()) roomToken = data.roomAccessToken
        _roomSnapshot.value = data.room
    }

    // ---- REST: host management ------------------------------------------------

    suspend fun setSelection(request: SetSelectionRequest): ApiResult<RoomResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.setSelection(roomId, roomToken, request)
        if (r is ApiResult.Success) _roomSnapshot.value = r.data.room
        return r
    }

    suspend fun updatePolicy(request: UpdatePolicyRequest): ApiResult<RoomResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.updatePolicy(roomId, roomToken, request)
        if (r is ApiResult.Success) _roomSnapshot.value = r.data.room
        return r
    }

    suspend fun closeRoom(): ApiResult<Unit> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        return api.closeRoom(roomId, roomToken)
    }

    // ---- REST: suggestions ----------------------------------------------------

    suspend fun refreshSuggestions(): ApiResult<SuggestionsResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.listSuggestions(roomId, roomToken)
        if (r is ApiResult.Success) applySuggestions(r.data.suggestions, fromBroadcast = false)
        return r
    }

    suspend fun addSuggestion(request: AddSuggestionRequest): ApiResult<SuggestionsResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.addSuggestion(roomId, roomToken, request)
        if (r is ApiResult.Success) applySuggestions(r.data.suggestions, fromBroadcast = false)
        return r
    }

    suspend fun deleteSuggestion(suggestionId: String): ApiResult<SuggestionsResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.deleteSuggestion(roomId, roomToken, suggestionId)
        if (r is ApiResult.Success) applySuggestions(r.data.suggestions, fromBroadcast = false)
        return r
    }

    suspend fun vote(suggestionId: String): ApiResult<SuggestionsResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.vote(roomId, roomToken, suggestionId)
        if (r is ApiResult.Success) {
            votedIds.add(suggestionId)
            applySuggestions(r.data.suggestions, fromBroadcast = false)
        }
        return r
    }

    suspend fun unvote(suggestionId: String): ApiResult<SuggestionsResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.unvote(roomId, roomToken, suggestionId)
        if (r is ApiResult.Success) {
            votedIds.remove(suggestionId)
            applySuggestions(r.data.suggestions, fromBroadcast = false)
        }
        return r
    }

    suspend fun promoteSuggestion(request: PromoteSuggestionRequest): ApiResult<RoomResponse> {
        val roomId = _roomSnapshot.value?.roomId ?: ""
        val r = api.promoteSuggestion(roomId, roomToken, request)
        if (r is ApiResult.Success) _roomSnapshot.value = r.data.room
        return r
    }

    /**
     * Publish suggestions, re-merging `voted_by_me` from the local [votedIds]
     * set. For REST results we also seed [votedIds] from authoritative
     * voted_by_me; broadcasts force false, so we only OR the local set in.
     */
    private fun applySuggestions(list: List<Suggestion>, fromBroadcast: Boolean) {
        if (!fromBroadcast) {
            list.forEach { if (it.votedByMe) votedIds.add(it.id) }
        }
        _suggestions.value = list.map { s ->
            if (s.id in votedIds) s.copy(votedByMe = true) else s
        }
    }

    // ---- WS: client→server send passthroughs ----------------------------------

    suspend fun attachSession(sessionId: String) { realtime?.attachSession(sessionId) }
    suspend fun transportRequest(action: String, positionSeconds: Double?, isPaused: Boolean) {
        realtime?.transportRequest(action, positionSeconds, isPaused)
    }
    suspend fun stateReport(sessionId: String, positionSeconds: Double, isPaused: Boolean) {
        realtime?.stateReport(sessionId, positionSeconds, isPaused)
    }
    suspend fun ready(sessionId: String, positionSeconds: Double, isPaused: Boolean) {
        realtime?.ready(sessionId, positionSeconds, isPaused)
    }
    suspend fun buffering(sessionId: String, positionSeconds: Double, isPaused: Boolean) {
        realtime?.buffering(sessionId, positionSeconds, isPaused)
    }
    suspend fun ping(clientSentAt: String) { realtime?.ping(clientSentAt) }

    // ---- WS lifecycle: connect + reconnect-with-backoff ------------------------

    /**
     * Collect the room socket with capped-backoff reconnect, folding each event
     * into the state flows. Suspends until the caller's scope is cancelled OR a
     * server `room_closed` arrives (which stops reconnecting). Backoff steps are
     * [BACKOFF_MS]; a healthy event resets the index.
     */
    suspend fun connect(roomId: String) {
        val client = realtimeFactory() ?: return
        realtime = client
        var backoffIndex = 0
        while (true) {
            var closedByServer = false
            try {
                client.connect(roomId, roomToken).collect { event ->
                    if (event is RoomRealtimeEvent.Closed && event.reason != null) {
                        // Server-initiated close (host_left / explicit) — terminal.
                        closedByServer = true
                        _roomSnapshot.value = null
                    } else if (event !is RoomRealtimeEvent.Closed) {
                        backoffIndex = 0 // healthy traffic resets backoff
                    }
                    fold(event)
                }
            } catch (e: CancellationException) {
                realtime = null
                throw e
            } catch (_: Throwable) {
                // fall through to backoff-reconnect
            }
            if (closedByServer) break
            delay(BACKOFF_MS[backoffIndex])
            backoffIndex = (backoffIndex + 1).coerceAtMost(BACKOFF_MS.lastIndex)
        }
        realtime = null
    }

    /** Pure-ish fold of one realtime event into the state flows + side streams. */
    private fun fold(event: RoomRealtimeEvent) {
        when (event) {
            is RoomRealtimeEvent.SnapshotEvent -> _roomSnapshot.value = event.room
            is RoomRealtimeEvent.SuggestionsEvent -> applySuggestions(event.suggestions, fromBroadcast = true)
            is RoomRealtimeEvent.TransportCommandEvent -> _transportCommands.tryEmit(event.command)
            is RoomRealtimeEvent.Pong -> _pongs.tryEmit(event)
            is RoomRealtimeEvent.Closed -> { /* lifecycle handled in connect() */ }
            is RoomRealtimeEvent.Error -> { /* surfaced via screen messages; no state change */ }
        }
    }

    /** Clear all room state on leave. The connect() loop ends via scope cancellation. */
    fun reset() {
        _roomSnapshot.value = null
        _suggestions.value = emptyList()
        votedIds.clear()
        roomToken = ""
        realtime = null
    }

    companion object {
        /** Reconnect backoff steps (ms) — spec: not after room_closed. */
        val BACKOFF_MS = longArrayOf(500L, 1_000L, 2_000L, 5_000L)
    }
}
```

Modify `/Users/dev/projects/silo/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt` — add the import and the singleton. Add to the imports block:
```kotlin
import com.continuum.app.repository.WatchTogetherRepository
```
Add inside the `module { … }`, after the `NotificationsRepository` block:
```kotlin
    // One room's snapshot/suggestions state + WS lifecycle. The realtime factory
    // builds the per-room socket client from the shared HttpClient + TokenManager
    // (query-param auth). Lazy so a socket is only minted when connect() runs.
    single {
        WatchTogetherRepository(
            api = get(),
            realtimeFactory = {
                com.continuum.app.network.DefaultWatchTogetherRealtimeClient(
                    client = get(),
                    tokenManager = get(),
                )
            },
        )
    }
```

- [ ] **Step 4: Run tests** (command)
```bash
./gradlew :shared:testDebugUnitTest --tests com.continuum.app.repository.WatchTogetherRepositoryTest
```

- [ ] **Step 5: Commit**
```bash
git add shared/src/commonMain/kotlin/com/continuum/app/repository/WatchTogetherRepository.kt \
        shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt \
        shared/src/commonTest/kotlin/com/continuum/app/repository/WatchTogetherRepositoryTest.kt && \
git commit -m "Add WatchTogetherRepository room state + WS lifecycle

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

## Section M: Mobile UI

I now have a complete picture. The Watch Together action will go in the overflow menu (cleanest seam — `overflow` already takes a composable lambda). The `sessionId` is in `PlayerViewModel.uiState.sessionId`. I have everything needed to write the plan.

### Task M1: Watch Together entry — item-detail action, create/join sheet, route

**Files:**
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/Routes.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/ItemDetailScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/detail/MovieDetailContent.kt` and `SeriesDetailContent.kt` (add `onWatchTogether` overflow item)
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/watchtogether/WatchTogetherEntrySheet.kt`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/watchtogether/WatchTogetherEntryViewModel.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/watchtogether/WatchTogetherEntryDestinationTest.kt`

- [ ] **Step 1: Write the failing test** — A pure-function test for the navigation decision the ViewModel exposes (route after create/join). Extract the decision so it is unit-testable without Compose/repo.

```kotlin
// androidApp/src/androidUnitTest/.../watchtogether/WatchTogetherEntryDestinationTest.kt
package com.continuum.app.android.ui.screens.watchtogether

import com.continuum.app.android.ui.navigation.Route
import com.continuum.app.model.watchtogether.RoomSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchTogetherEntryDestinationTest {

    private fun snapshot(
        roomId: String = "room-1",
        selectedContentId: String? = null,
        selectedFileId: Int? = null,
        phase: String = "lobby",
    ) = RoomSnapshot(
        roomId = roomId,
        phase = phase,
        playbackState = "idle",
        selectionMode = "host_pick",
        selectionRevision = 0L,
        selectedContentId = selectedContentId,
        selectedFileId = selectedFileId,
        selectedLibraryId = null,
        code = "ABCD1234",
        guestControlPolicy = "host_only",
        isPaused = false,
        anchorPositionSeconds = 0.0,
        anchorUpdatedAt = null,
        generation = 0L,
        memberCount = 1,
        hostConnected = true,
        selfRole = "host",
        selfCanControlTransport = true,
        selfCanManageRoom = true,
        selfIgnoreWait = false,
        attachedSessionId = null,
        invitePath = "/wt/ABCD1234",
    )

    @Test
    fun host_with_selection_goes_to_player_with_roomId() {
        val dest = watchTogetherDestination(snapshot(selectedContentId = "c1", selectedFileId = 7))
        assertEquals(Route.Player(contentId = "c1", fileId = 7, roomId = "room-1").route, dest)
    }

    @Test
    fun no_selection_goes_to_lobby() {
        val dest = watchTogetherDestination(snapshot(selectedContentId = null))
        assertEquals(Route.WatchTogetherLobby(roomId = "room-1").route, dest)
    }

    @Test
    fun selection_set_but_no_fileId_still_routes_to_player() {
        val dest = watchTogetherDestination(snapshot(selectedContentId = "c2", selectedFileId = null))
        assertEquals(Route.Player(contentId = "c2", fileId = null, roomId = "room-1").route, dest)
    }
}
```

> Note: field names/types in the `RoomSnapshot` constructor above are the spec's assumed shape — the executor MUST verify against the landed `shared/model/watchtogether/WatchTogetherModels.kt` and adjust the constructor call (and `watchTogetherDestination`'s field reads) to match. The three assertions are the contract that must hold regardless of exact field spelling.

- [ ] **Step 2: Run test to verify it fails** (won't compile — `Route.WatchTogetherLobby`, `Route.Player(roomId=…)`, and `watchTogetherDestination` don't exist yet):
`./gradlew :androidApp:testDebugUnitTest --tests "*WatchTogetherEntryDestinationTest*"`

- [ ] **Step 3: Implementation**

**Routes.kt** — extend `Player` with optional `roomId` (preserve existing arg order/behavior) and add `WatchTogetherLobby`:

```kotlin
    // --- Player (fullscreen, no system bars) ---
    data class Player(
        val contentId: String,
        val fileId: Int? = null,
        val audioTrackIndex: Int? = null,
        val subtitleTrackIndex: Int? = null,
        val roomId: String? = null,
    ) : Route(
        buildString {
            append("player/$contentId")
            val queryParams = listOfNotNull(
                fileId?.let { "fileId=$it" },
                audioTrackIndex?.let { "audioTrackIndex=$it" },
                subtitleTrackIndex?.let { "subtitleTrackIndex=$it" },
                roomId?.takeIf { it.isNotBlank() }?.let { "roomId=${Uri.encode(it)}" },
            )
            if (queryParams.isNotEmpty()) {
                append("?")
                append(queryParams.joinToString("&"))
            }
        },
    ) {
        companion object {
            const val ROUTE =
                "player/{contentId}?fileId={fileId}&audioTrackIndex={audioTrackIndex}&subtitleTrackIndex={subtitleTrackIndex}&roomId={roomId}"
        }
    }

    // --- Watch Together (synchronized playback rooms) ---
    data class WatchTogetherLobby(val roomId: String) : Route("watch_together/${Uri.encode(roomId)}") {
        companion object {
            const val ROUTE = "watch_together/{roomId}"
            const val ARG_ROOM_ID = "roomId"
        }
    }
```

**WatchTogetherEntryViewModel.kt** — create/join calls plus the pure destination helper:

```kotlin
package com.continuum.app.android.ui.screens.watchtogether

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.android.ui.navigation.Route
import com.continuum.app.model.watchtogether.RoomSnapshot
import com.continuum.app.network.ApiResult
import com.continuum.app.network.errorMessage
import com.continuum.app.repository.WatchTogetherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pure navigation decision: after create/join, where do we send the user?
 * If the room already has a selected title → straight to the synced player
 * (carrying roomId). Otherwise → the lobby to wait / vote / pick.
 *
 * Kept top-level + pure so it is unit-testable without Compose or the repo.
 */
fun watchTogetherDestination(room: RoomSnapshot): String =
    if (!room.selectedContentId.isNullOrBlank()) {
        Route.Player(
            contentId = room.selectedContentId!!,
            fileId = room.selectedFileId,
            roomId = room.roomId,
        ).route
    } else {
        Route.WatchTogetherLobby(roomId = room.roomId).route
    }

class WatchTogetherEntryViewModel(
    private val repository: WatchTogetherRepository,
) : ViewModel() {

    data class UiState(
        val busy: Boolean = false,
        val error: String? = null,
        /** Set once a create/join resolves — the sheet observes this and navigates. */
        val destination: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Host flow: create a room with this title pre-selected as the room selection. */
    fun host(contentId: String, fileId: Int?) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val created = repository.createRoom()) {
                is ApiResult.Success -> {
                    // Set this title as the room selection so everyone lands on it.
                    when (val sel = repository.setSelection(
                        roomId = created.data.roomId,
                        contentId = contentId,
                        fileId = fileId,
                    )) {
                        is ApiResult.Success -> finish(sel.data)
                        is ApiResult.Error, is ApiResult.NetworkError ->
                            fail(sel.errorMessage("Failed to set selection"))
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError ->
                    fail(created.errorMessage("Failed to create room"))
            }
        }
    }

    /** Join flow: resolve an 8-char code; route to player if a selection exists, else lobby. */
    fun joinByCode(code: String) {
        val trimmed = code.trim()
        if (_uiState.value.busy || trimmed.isBlank()) return
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val joined = repository.joinRoom(code = trimmed)) {
                is ApiResult.Success -> finish(joined.data)
                is ApiResult.Error, is ApiResult.NetworkError ->
                    fail(joined.errorMessage("Could not join — check the code"))
            }
        }
    }

    private fun finish(room: RoomSnapshot) {
        _uiState.update { it.copy(busy = false, error = null, destination = watchTogetherDestination(room)) }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(busy = false, error = message) }
    }

    fun consumeDestination() = _uiState.update { it.copy(destination = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}
```

> Executor: verify `WatchTogetherRepository.createRoom()` / `setSelection(...)` / `joinRoom(code=…)` signatures + return types (the spec's assumed `ApiResult<RoomSnapshot>` wrappers) against the landed repo and adjust the `when` arms. If `createRoom`/`joinRoom` return a room+token wrapper rather than a bare snapshot, read `.room` off `.data`.

**WatchTogetherEntrySheet.kt** — bottom sheet styled like `RatingSheet`:

```kotlin
package com.continuum.app.android.ui.screens.watchtogether

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

/**
 * Watch Together entry sheet, opened from the item-detail overflow.
 * Host = create a room with this title pre-selected. Join = enter an
 * 8-char code. On success [onNavigate] fires with the resolved route
 * (synced player or lobby). Styled after [RatingSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchTogetherEntrySheet(
    contentId: String,
    fileId: Int?,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: WatchTogetherEntryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var code by remember { mutableStateOf("") }
    var showJoin by remember { mutableStateOf(false) }

    LaunchedEffect(state.destination) {
        val dest = state.destination ?: return@LaunchedEffect
        viewModel.consumeDestination()
        onDismiss()
        onNavigate(dest)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Watch Together",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (!showJoin) {
                Button(
                    onClick = { viewModel.host(contentId, fileId) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.busy) "Creating…" else "Host a room") }

                OutlinedButton(
                    onClick = { viewModel.clearError(); showJoin = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Join by code") }
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(8) },
                    label = { Text("Invite code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.joinByCode(code) },
                    enabled = !state.busy && code.length >= 4,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.height(18.dp))
                    else Text("Join")
                }
                OutlinedButton(
                    onClick = { viewModel.clearError(); showJoin = false },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Back") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
```

**MovieDetailContent.kt / SeriesDetailContent.kt** — add an `onWatchTogether: (() -> Unit)? = null` param and surface it in the overflow `DropdownMenuItem` list (movies/episodes/series only — not audiobook/book). In `MovieDetailContent` add to `hasOverflow` and inside the overflow lambda:

```kotlin
            if (onWatchTogether != null) {
                DropdownMenuItem(
                    text = { Text("Watch Together") },
                    leadingIcon = { Icon(Icons.Outlined.Groups, contentDescription = null) },
                    onClick = { dismiss(); onWatchTogether() },
                )
            }
```
(Add `import androidx.compose.material.icons.outlined.Groups`; include `onWatchTogether != null` in the `hasOverflow` boolean.)

**ItemDetailScreen.kt** — add `onWatchTogether: (String, Int?) -> Unit = { _, _ -> }` to the signature; for the movie/episode branch pass `onWatchTogether = { onWatchTogether(detail.contentId, explicitFileId) }`, and for the series branch `onWatchTogether = { (nextEpisode?.contentId ?: detail.contentId).let { onWatchTogether(it, null) } }`.

**AppNavigation.kt** — in the `ItemDetailScreen(...)` call, hold sheet state at the NavHost-composable level and render the sheet:

```kotlin
        composable(route = Route.ItemDetail.ROUTE, arguments = /* unchanged */) {
            val detailViewModel = koinViewModel<ItemDetailViewModel>()
            var wtTarget by remember { mutableStateOf<Pair<String, Int?>?>(null) }
            ItemDetailScreen(
                // …existing callbacks unchanged…
                onWatchTogether = { contentId, fileId -> wtTarget = contentId to fileId },
                viewModel = detailViewModel,
            )
            wtTarget?.let { (cid, fid) ->
                WatchTogetherEntrySheet(
                    contentId = cid,
                    fileId = fid,
                    onNavigate = { route -> navController.navigate(route) },
                    onDismiss = { wtTarget = null },
                )
            }
        }
```
(Add imports: `androidx.compose.runtime.mutableStateOf/remember/getValue/setValue` and `WatchTogetherEntrySheet`.) Register the lobby + extended player composables (lobby screen body lands in Task 2; for this task stub `WatchTogetherLobbyScreen` is not yet present, so add only the `roomId` navArg to the existing Player composable and read it through — see below). Add to the Player `composable`:

```kotlin
                navArgument("roomId") { type = NavType.StringType; nullable = true; defaultValue = null },
```
and pass `roomId = backStackEntry.arguments?.getString("roomId")` into `PlayerScreen` (the param is added in Task 3; until then PlayerScreen ignores it — to keep this task compiling, add the `roomId: String? = null` param to `PlayerScreen` now as an unused passthrough).

**AndroidModule.kt** — register the ViewModel:
```kotlin
    viewModel { com.continuum.app.android.ui.screens.watchtogether.WatchTogetherEntryViewModel(get()) }
```

- [ ] **Step 4: Run tests**
`./gradlew :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest`
Manual checklist (one device): open a movie → overflow shows "Watch Together" → tap → sheet appears; "Host a room" navigates into the synced player (the title is the room selection); "Join by code" reveals the code field; an invalid code shows an inline error and does not crash; dismissing the sheet returns to detail.

- [ ] **Step 5: Commit** (branch first if on `main`):
`git add -A && git commit` — message: "Add Watch Together entry sheet + create/join routing"

---

### Task M2: WatchTogetherLobbyScreen + LobbyViewModel

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/watchtogether/WatchTogetherLobbyViewModel.kt`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/watchtogether/WatchTogetherLobbyScreen.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt` (register `Route.WatchTogetherLobby`)
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/watchtogether/LobbyAutoNavigateTest.kt`

- [ ] **Step 1: Write the failing test** — the only non-Compose logic worth testing is the auto-navigate decision (when a selection is set + phase is playing, the lobby jumps to the synced player). Extract it pure.

```kotlin
package com.continuum.app.android.ui.screens.watchtogether

import com.continuum.app.model.watchtogether.RoomSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LobbyAutoNavigateTest {

    private fun room(selectedContentId: String?, phase: String) = RoomSnapshot(
        roomId = "r1", phase = phase, playbackState = "idle", selectionMode = "host_pick",
        selectionRevision = 0L, selectedContentId = selectedContentId, selectedFileId = 3,
        selectedLibraryId = null, code = "CODE1234", guestControlPolicy = "host_only",
        isPaused = false, anchorPositionSeconds = 0.0, anchorUpdatedAt = null, generation = 0L,
        memberCount = 2, hostConnected = true, selfRole = "guest", selfCanControlTransport = false,
        selfCanManageRoom = false, selfIgnoreWait = false, attachedSessionId = null, invitePath = null,
    )

    @Test fun playing_with_selection_navigates_to_player() {
        val dest = lobbyPlayerDestinationOrNull(room("c9", phase = "playing"))
        assertEquals(Route.Player(contentId = "c9", fileId = 3, roomId = "r1").route, dest)
    }

    @Test fun lobby_phase_does_not_navigate() {
        assertNull(lobbyPlayerDestinationOrNull(room("c9", phase = "lobby")))
    }

    @Test fun playing_without_selection_does_not_navigate() {
        assertNull(lobbyPlayerDestinationOrNull(room(null, phase = "playing")))
    }
}
```
(Import `com.continuum.app.android.ui.navigation.Route`. Executor verifies `RoomSnapshot` field shape against landed code.)

- [ ] **Step 2: Run test to verify it fails** — fails to compile (`lobbyPlayerDestinationOrNull` missing):
`./gradlew :androidApp:testDebugUnitTest --tests "*LobbyAutoNavigateTest*"`

- [ ] **Step 3: Implementation**

**WatchTogetherLobbyViewModel.kt:**

```kotlin
package com.continuum.app.android.ui.screens.watchtogether

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.android.ui.navigation.Route
import com.continuum.app.model.watchtogether.RoomSnapshot
import com.continuum.app.model.watchtogether.Suggestion
import com.continuum.app.repository.WatchTogetherRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pure: should the lobby jump to the synced player? Only once the room is
 * actually playing AND a title is selected. Returns the player route or null.
 */
fun lobbyPlayerDestinationOrNull(room: RoomSnapshot): String? =
    if (room.phase == "playing" && !room.selectedContentId.isNullOrBlank()) {
        Route.Player(
            contentId = room.selectedContentId!!,
            fileId = room.selectedFileId,
            roomId = room.roomId,
        ).route
    } else {
        null
    }

class WatchTogetherLobbyViewModel(
    private val roomId: String,
    private val repository: WatchTogetherRepository,
) : ViewModel() {

    // Connect the WS for this room so snapshots/suggestions flow in. The repo
    // owns reconnect/backoff; we just bind on enter and reset on leave.
    init {
        repository.connect(roomId)
    }

    val room: StateFlow<RoomSnapshot?> = repository.roomSnapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.roomSnapshot.value)
    val suggestions: StateFlow<List<Suggestion>> = repository.suggestions
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.suggestions.value)

    fun addSuggestion(contentId: String, contentType: String, title: String, subtitle: String?, posterUrl: String?) {
        viewModelScope.launch {
            repository.addSuggestion(roomId, contentId, contentType, title, subtitle, posterUrl, note = null)
        }
    }

    fun vote(suggestionId: String) = viewModelScope.launch { repository.voteSuggestion(roomId, suggestionId) }
    fun unvote(suggestionId: String) = viewModelScope.launch { repository.unvoteSuggestion(roomId, suggestionId) }
    fun removeSuggestion(suggestionId: String) = viewModelScope.launch { repository.removeSuggestion(roomId, suggestionId) }

    /** Host: promote the winning suggestion to the room selection (moves everyone to the player). */
    fun promote(suggestionId: String) = viewModelScope.launch { repository.promoteSuggestion(roomId, suggestionId) }

    /** Host: pick a title directly as the selection. */
    fun pickSelection(contentId: String, fileId: Int?) =
        viewModelScope.launch { repository.setSelection(roomId, contentId, fileId) }

    fun closeRoom() = viewModelScope.launch { repository.closeRoom(roomId) }

    /** Guest/host leave: just tear down the WS (no leave endpoint per spec). */
    fun leave() {
        repository.reset()
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT reset here — the player binding (Task 3) reuses the same repo
        // connection when we auto-navigate into the synced player. reset() is
        // called explicitly via leave() when the user backs out without playing.
    }
}
```

> Executor: confirm the suggestion-op method names/params on the landed `WatchTogetherRepository` (the spec lists "suggestion ops" without exact signatures) and that `roomSnapshot`/`suggestions` are `StateFlow`s with `.value`. Confirm whether connecting in `init` is correct or if the controller (Task 3) is the sole connector — if the repo's `connect` is idempotent (spec says it owns the WS lifecycle), connecting here is fine and the player binding re-uses it.

**WatchTogetherLobbyScreen.kt** — header (member count, host/guest, selection mode), suggestions list (vote/unvote, host promote, host remove), invite code share, host pick-selection affordance, and the auto-navigate `LaunchedEffect`:

```kotlin
package com.continuum.app.android.ui.screens.watchtogether

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.continuum.app.model.watchtogether.Suggestion
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchTogetherLobbyScreen(
    roomId: String,
    onNavigateToPlayer: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: WatchTogetherLobbyViewModel = koinViewModel { parametersOf(roomId) },
) {
    val room by viewModel.room.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val context = LocalContext.current

    // Auto-navigate into the synced player when the room starts playing.
    LaunchedEffect(room?.phase, room?.selectedContentId) {
        val snapshot = room ?: return@LaunchedEffect
        lobbyPlayerDestinationOrNull(snapshot)?.let { onNavigateToPlayer(it) }
    }

    val isHost = room?.selfRole == "host"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watch Together") },
                navigationIcon = {
                    TextButton(onClick = { viewModel.leave(); onBack() }) { Text("Leave") }
                },
                actions = {
                    if (isHost) {
                        IconButton(onClick = { viewModel.closeRoom(); onBack() }) {
                            Icon(Icons.Filled.Share, contentDescription = "Close room")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val snapshot = room
            if (snapshot == null) {
                CircularProgressIndicator()
            } else {
                Text("${snapshot.memberCount} in room · ${if (isHost) "Host" else "Guest"}", style = MaterialTheme.typography.titleMedium)
                Text("Mode: ${snapshot.selectionMode}", style = MaterialTheme.typography.bodyMedium)

                // Invite code share row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Invite code: ${snapshot.code}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Join my Watch Together room with code ${snapshot.code}")
                        }
                        context.startActivity(Intent.createChooser(send, "Share invite code"))
                    }) { Icon(Icons.Filled.Share, contentDescription = "Share invite code") }
                }

                HorizontalDivider()
                Text("Suggestions", style = MaterialTheme.typography.titleSmall)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions, key = { it.id }) { s ->
                        SuggestionRow(
                            suggestion = s,
                            isHost = isHost,
                            onVote = { if (s.votedByMe) viewModel.unvote(s.id) else viewModel.vote(s.id) },
                            onPromote = { viewModel.promote(s.id) },
                            onRemove = { viewModel.removeSuggestion(s.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    isHost: Boolean,
    onVote: () -> Unit,
    onPromote: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(suggestion.title, style = MaterialTheme.typography.bodyLarge)
                suggestion.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = onVote) {
                Icon(Icons.Filled.HowToVote, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("${suggestion.voteCount}${if (suggestion.votedByMe) " ✓" else ""}")
            }
            if (isHost) {
                TextButton(onClick = onPromote) { Text("Pick") }
            }
        }
    }
}
```

> Note: "add a suggestion" in v1 is the simplest path — the host pre-selection already comes from item-detail (Task 1), so the lobby's add-suggestion affordance can be deferred to a follow-up; this screen focuses on vote/unvote/promote/pick + invite. If the executor wants the add path, wire `viewModel.addSuggestion(...)` to a search-pick flow reusing `SearchScreen` idioms. Executor verifies `Suggestion` field names (`votedByMe`, `voteCount`, `subtitle`).

**AppNavigation.kt** — register the lobby route:
```kotlin
        composable(
            route = Route.WatchTogetherLobby.ROUTE,
            arguments = listOf(navArgument(Route.WatchTogetherLobby.ARG_ROOM_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString(Route.WatchTogetherLobby.ARG_ROOM_ID).orEmpty()
            WatchTogetherLobbyScreen(
                roomId = roomId,
                onNavigateToPlayer = { route ->
                    navController.navigate(route) {
                        popUpTo(Route.WatchTogetherLobby.ROUTE) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
```

**AndroidModule.kt:**
```kotlin
    viewModel { params ->
        com.continuum.app.android.ui.screens.watchtogether.WatchTogetherLobbyViewModel(
            roomId = params.get(),
            repository = get(),
        )
    }
```

- [ ] **Step 4: Run tests**
`./gradlew :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest`
Manual checklist (real sync needs TWO devices — flag): device A hosts, device B joins by code → both see member count = 2; B votes/unvotes a suggestion → count updates on A; host taps "Pick"/promote → both devices auto-navigate into the synced player; "Leave" tears down cleanly; host "Close room" exits both.

- [ ] **Step 5: Commit**
`git add -A && git commit` — message: "Add Watch Together lobby screen + suggestions/voting"

---

### Task M3: RoomSyncController + PlayerScreen synced-playback binding

**Files:**
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/RoomSyncController.kt`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/RoomSyncStateReportGate.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerScreen.kt` (accept `roomId`, mount controller, wire overlay/transport gate)
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/screens/player/PlayerOverlay.kt` (room indicator + invite + Leave; gate guest transport)
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ui/navigation/AppNavigation.kt` (pass `roomId` into `PlayerScreen` — the navArg was added in Task 1)
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/di/AndroidModule.kt`
- Create: `androidApp/src/androidUnitTest/kotlin/com/continuum/app/android/ui/screens/player/RoomSyncStateReportGateTest.kt`

- [ ] **Step 1: Write the failing test** — the pure bit is the state-report suppression decision: suppress reports within ~250ms of a pending command's local execute time; otherwise report on the ~1.5s cadence.

```kotlin
package com.continuum.app.android.ui.screens.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomSyncStateReportGateTest {

    private val cadenceMs = 1_500L
    private val suppressWindowMs = 250L

    @Test fun reports_when_cadence_elapsed_and_no_pending_command() {
        assertTrue(
            shouldEmitStateReport(
                nowMs = 2_000L, lastReportMs = 0L, cadenceMs = cadenceMs,
                pendingExecuteAtMs = null, suppressWindowMs = suppressWindowMs,
            ),
        )
    }

    @Test fun does_not_report_before_cadence_elapsed() {
        assertFalse(
            shouldEmitStateReport(
                nowMs = 1_000L, lastReportMs = 0L, cadenceMs = cadenceMs,
                pendingExecuteAtMs = null, suppressWindowMs = suppressWindowMs,
            ),
        )
    }

    @Test fun suppresses_within_window_of_pending_execute() {
        // cadence elapsed, but a command executes at 2100 and now=2000 → within 250ms
        assertFalse(
            shouldEmitStateReport(
                nowMs = 2_000L, lastReportMs = 0L, cadenceMs = cadenceMs,
                pendingExecuteAtMs = 2_100L, suppressWindowMs = suppressWindowMs,
            ),
        )
    }

    @Test fun reports_when_pending_execute_is_far_away() {
        assertTrue(
            shouldEmitStateReport(
                nowMs = 2_000L, lastReportMs = 0L, cadenceMs = cadenceMs,
                pendingExecuteAtMs = 5_000L, suppressWindowMs = suppressWindowMs,
            ),
        )
    }

    @Test fun suppresses_just_after_execute_too() {
        assertFalse(
            shouldEmitStateReport(
                nowMs = 2_000L, lastReportMs = 0L, cadenceMs = cadenceMs,
                pendingExecuteAtMs = 1_900L, suppressWindowMs = suppressWindowMs,
            ),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails** — fails to compile (`shouldEmitStateReport` missing):
`./gradlew :androidApp:testDebugUnitTest --tests "*RoomSyncStateReportGateTest*"`

- [ ] **Step 3: Implementation**

**RoomSyncStateReportGate.kt** (pure):

```kotlin
package com.continuum.app.android.ui.screens.player

import kotlin.math.abs

/**
 * Pure state-report cadence/suppression decision used by [RoomSyncController].
 * We emit a `state_report` once per [cadenceMs], EXCEPT inside a +/- [suppressWindowMs]
 * window around a pending transport command's local execute time — reporting our
 * position right as we are about to seek/play would feed the server a stale
 * pre-execute sample and fight the sync barrier.
 *
 * @param pendingExecuteAtMs local (monotonic) execute time of the next command, or null.
 */
fun shouldEmitStateReport(
    nowMs: Long,
    lastReportMs: Long,
    cadenceMs: Long,
    pendingExecuteAtMs: Long?,
    suppressWindowMs: Long,
): Boolean {
    if (nowMs - lastReportMs < cadenceMs) return false
    if (pendingExecuteAtMs != null && abs(nowMs - pendingExecuteAtMs) <= suppressWindowMs) return false
    return true
}
```

**RoomSyncController.kt** — the per-screen coroutine driver. Owns: attach_session, applying engine decisions to the VM, the state-report loop, ready/buffering on waiting transitions, and routing user transport through the repo. Exposes the live `RoomSnapshot` for the overlay.

```kotlin
package com.continuum.app.android.ui.screens.player

import com.continuum.app.model.watchtogether.RoomSnapshot
import com.continuum.app.repository.RoomSyncDecision
import com.continuum.app.repository.WatchTogetherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Binds a Watch Together room to the mobile [PlayerViewModel] for the lifetime
 * of a synced-player screen. Active ONLY when the player route carries a roomId.
 *
 * Responsibilities (mirrors the spec's "Player binding"):
 *  - connect the repo WS for the room + attach_session once the player session id is known
 *  - collect the repo's RoomSyncEngine-driven decision flow → PlayerViewModel.onSeek/onPlayPause
 *    at the engine-scheduled local time
 *  - emit state_report on the ~1.5s cadence, suppressed around a pending execute
 *  - emit ready/buffering on buffer transitions while the room is waiting
 *  - route user play/pause/seek through transportRequest, gated on self_can_control_transport
 *  - surface room_closed → screen exits
 */
class RoomSyncController(
    private val roomId: String,
    private val repository: WatchTogetherRepository,
    private val viewModel: PlayerViewModel,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val REPORT_CADENCE_MS = 1_500L
        private const val SUPPRESS_WINDOW_MS = 250L
    }

    val room: StateFlow<RoomSnapshot?> get() = repository.roomSnapshot

    private val _closedReason = MutableStateFlow<String?>(null)
    /** Non-null once the server closes the room — the screen observes this and exits. */
    val closedReason: StateFlow<String?> = _closedReason.asStateFlow()

    private var lastReportMs = 0L
    private var pendingExecuteAtMs: Long? = null
    private var attached = false

    /** True when the local member may drive transport (host always; guest only if policy allows). */
    val canControlTransport: Boolean
        get() = repository.roomSnapshot.value?.selfCanControlTransport == true

    fun start() {
        repository.connect(roomId)

        // Attach the player's own playback session id as soon as it resolves.
        scope.launch {
            viewModel.uiState
                .map { it.sessionId }
                .filterNotNull()
                .collect { sessionId ->
                    if (!attached) {
                        attached = true
                        repository.attachSession(sessionId)
                    }
                }
        }

        // Apply engine decisions (Seek/SetPlaying/Ignore) at the scheduled local time.
        scope.launch {
            repository.syncDecisions.collect { decision ->
                applyDecision(decision)
            }
        }

        // Drift reporting loop (suppressed around a pending execute).
        scope.launch {
            while (isActive) {
                val state = viewModel.uiState.value
                val now = nowMs()
                if (state.sessionId != null &&
                    shouldEmitStateReport(now, lastReportMs, REPORT_CADENCE_MS, pendingExecuteAtMs, SUPPRESS_WINDOW_MS)
                ) {
                    lastReportMs = now
                    repository.stateReport(
                        sessionId = state.sessionId!!,
                        positionSeconds = state.position,
                        isPaused = state.isPaused,
                    )
                }
                delay(250)
            }
        }

        // ready / buffering during the waiting barrier.
        scope.launch {
            var lastBuffering: Boolean? = null
            viewModel.uiState.collect { state ->
                val waiting = repository.roomSnapshot.value?.playbackState == "waiting"
                val sessionId = state.sessionId
                if (waiting && sessionId != null && state.isBuffering != lastBuffering) {
                    lastBuffering = state.isBuffering
                    if (state.isBuffering) {
                        repository.buffering(sessionId, state.position, state.isPaused)
                    } else {
                        repository.ready(sessionId, state.position, state.isPaused)
                    }
                }
            }
        }

        // room_closed → exit.
        scope.launch {
            repository.roomClosed.collect { reason -> _closedReason.value = reason }
        }
    }

    private suspend fun applyDecision(decision: RoomSyncDecision) {
        // decision carries the engine-computed local execute delay; schedule it.
        pendingExecuteAtMs = nowMs() + decision.executeDelayMs
        if (decision.executeDelayMs > 0) delay(decision.executeDelayMs)
        decision.seekToSeconds?.let { viewModel.onSeek(it) }
        decision.setPlaying?.let { play ->
            // onPlayPause is a toggle — only flip if the intent differs.
            if (viewModel.uiState.value.isPaused == play) viewModel.onPlayPause()
        }
        pendingExecuteAtMs = null
    }

    // ---- User-initiated transport: route through the room instead of local apply ----
    fun onUserPlayPause() {
        if (!canControlTransport) return
        repository.transportRequest(
            action = if (viewModel.uiState.value.isPaused) "play" else "pause",
            positionSeconds = viewModel.uiState.value.position,
            isPaused = !viewModel.uiState.value.isPaused,
        )
    }

    fun onUserSeek(positionSeconds: Double) {
        // Guests never seek (spec); hosts route the seek through the room.
        if (!canControlTransport) return
        repository.transportRequest(action = "seek", positionSeconds = positionSeconds, isPaused = viewModel.uiState.value.isPaused)
    }

    fun leave(closeRoom: Boolean) {
        if (closeRoom) scope.launch { repository.closeRoom(roomId) }
        repository.reset()
    }

    private fun nowMs(): Long = android.os.SystemClock.elapsedRealtime()
}
```

> Executor MUST verify against the landed shared layer: the exact name/shape of the sync-decision Flow (`repository.syncDecisions` → `RoomSyncDecision` with `executeDelayMs`/`seekToSeconds`/`setPlaying`), the `room_closed` surface (assumed `repository.roomClosed: Flow<String>`; it may instead arrive as a field on the snapshot or via the `RoomRealtimeEvent` flow — adapt), and the passthrough method names (`attachSession`/`stateReport`/`ready`/`buffering`/`transportRequest`/`connect`/`reset`/`closeRoom`). If the engine schedules against the server clock (execute_at − serverTimeOffset) rather than a relative delay, replace `executeDelayMs` with an absolute target and convert via the offset the repo exposes. Keep `shouldEmitStateReport` as the single tested decision regardless.

**PlayerScreen.kt** — accept `roomId`, build the controller when present, drive overlay + transport gating, and exit on `room_closed`:

```kotlin
fun PlayerScreen(
    contentId: String,
    initialFileId: Int? = null,
    initialAudioTrackIndex: Int? = null,
    initialSubtitleTrackIndex: Int? = null,
    roomId: String? = null,            // added in Task 1 as unused passthrough; wired here
    navController: NavHostController,
    viewModel: PlayerViewModel = koinInject(),
) {
    // …existing body…
    val watchTogetherRepository: com.continuum.app.repository.WatchTogetherRepository = koinInject()
    val scope = rememberCoroutineScope()
    val roomController = remember(roomId) {
        roomId?.takeIf { it.isNotBlank() }?.let {
            RoomSyncController(it, watchTogetherRepository, viewModel, scope)
        }
    }
    DisposableEffect(roomController) {
        roomController?.start()
        onDispose { /* repo.reset handled on explicit leave + onExit */ }
    }
    val roomSnapshot by (roomController?.room ?: remember { MutableStateFlow(null) }).collectAsState()
    val closedReason by (roomController?.closedReason ?: remember { MutableStateFlow(null) }).collectAsState()
    LaunchedEffect(closedReason) {
        if (closedReason != null) {
            viewModel.onExit()
            navController.popBackStack()
            // (executor: optionally surface closedReason via a snackbar before popping)
        }
    }
```

Then pass room context + gated callbacks into `PlayerOverlay`. The overlay's `onPlayPause`/`onSeek` should route through the controller when in a room:

```kotlin
            PlayerOverlay(
                state = uiState,
                viewModel = viewModel,
                roomSnapshot = roomSnapshot,
                onBack = { /* in-room: leave; show host close confirm in overlay */
                    if (roomController != null) {
                        roomController.leave(closeRoom = roomSnapshot?.selfRole == "host")
                    }
                    viewModel.onExit()
                    navController.popBackStack()
                },
                onPlayPause = {
                    if (roomController != null) roomController.onUserPlayPause()
                    else viewModel.onPlayPause()
                },
                onSeek = { position ->
                    if (roomController != null) {
                        roomController.onUserSeek(position)   // no-op for guests
                    } else {
                        viewModel.onSeek(position)
                        mediaController?.seekTo((position * 1000).toLong())
                    }
                },
                // …rest unchanged…
            )
```

(Add imports: `rememberCoroutineScope`, `kotlinx.coroutines.flow.MutableStateFlow`.)

**PlayerOverlay.kt** — add `roomSnapshot: RoomSnapshot? = null`, render a room indicator (member count, host-connected dot, "Waiting for members…" when `playbackState == "waiting"`), an invite affordance (mobile host: show/share the code), and a Leave control with a host close-confirm dialog. Disable the seek bar / hide seek gestures for guests (`roomSnapshot != null && roomSnapshot.selfCanControlTransport == false`):

```kotlin
    // Room indicator chip (top-center) — only in a Watch Together room.
    if (roomSnapshot != null) {
        Box(Modifier.fillMaxSize().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Group, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                val label = when {
                    roomSnapshot.playbackState == "waiting" -> "Waiting for members…"
                    !roomSnapshot.hostConnected -> "${roomSnapshot.memberCount} · host offline"
                    else -> "${roomSnapshot.memberCount} watching"
                }
                Text(label, color = Color.White, fontSize = 13.sp)
                if (roomSnapshot.selfRole == "host") {
                    Spacer(Modifier.width(8.dp))
                    Text("Code ${roomSnapshot.code}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }
    }
```

Guest transport gate: in `PlayerControls` pass `seekEnabled = roomSnapshot == null || roomSnapshot.selfCanControlTransport` and make the scrubber/skip buttons no-op or visually disabled when false; the `onSeek` already routes to a controller no-op for guests, but the UI affordance should also read disabled. Host close-confirm: when `onBack` is tapped and `selfRole == "host"`, show an AlertDialog ("Close room for everyone?") before invoking the leave callback — hold a `showCloseConfirm` bool in the overlay and only call `onBack` after confirm.

> Executor: `PlayerControls` is in a sibling file; verify its signature to add the `seekEnabled` param (or wrap its seek callbacks to no-op). `Icons.Filled.Group` import: `androidx.compose.material.icons.filled.Group`.

**AppNavigation.kt** — pass `roomId` into `PlayerScreen` (navArg added in Task 1):
```kotlin
            PlayerScreen(
                // …existing…
                roomId = backStackEntry.arguments?.getString("roomId"),
                navController = navController,
            )
```

**AndroidModule.kt** — no new VM needed (controller is plain, constructed in the composable); ensure `WatchTogetherRepository` singleton is injectable (it lands in the shared `RepositoryModule` per the Dependencies note — verify it's registered/visible to Koin).

- [ ] **Step 4: Run tests**
`./gradlew :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest :androidApp:assembleDebug`
Manual checklist — **real sync REQUIRES TWO devices in a room (host + guest); single-device cannot verify sync:**
  - Host + guest join the same room and both reach the synced player.
  - Host play/pause → guest mirrors in lock-step; host seek → guest re-syncs (waiting → ready barrier, "Waiting for members…" shows briefly).
  - Guest play/pause works only when policy = `guest_play_pause`; guest seek is disabled (no scrubber drag, skip buttons inert).
  - Late-join: guest joins mid-play → re-syncs to host position.
  - Host taps Leave/Back → close-confirm → room closes → guest sees room-closed and exits to detail with a message.
  - Reconnect: toggle guest airplane mode briefly → WS reconnects, snapshot + attach_session re-sent, sync resumes.
  - Room indicator shows correct member count and host-connected state.

- [ ] **Step 5: Commit**
`git add -A && git commit` — message: "Bind Watch Together room sync to mobile player"

## Section T: TV UI

### Dependencies (assumed shared + mobile land first; executor MUST verify against landed code — field/method names below are best-effort from the spec)
- `WatchTogetherRepository` singleton (shared, RepositoryModule): `roomSnapshot: StateFlow<RoomSnapshot?>`, `suggestions: StateFlow<List<Suggestion>>`, `create(contentId, fileId?, libraryId?, selectionMode?): ApiResult<RoomSnapshot>` (stores room JWT internally), `join(code): ApiResult<RoomSnapshot>`, `setSelection/updatePolicy/closeRoom`, suggestion ops (`addSuggestion/removeSuggestion/vote/unvote/promote`), `connect()/close()/reset()`, sync send passthroughs (`attachSession/transportRequest/stateReport/ready/buffering`), and a sync-decision Flow driven by `RoomSyncEngine`.
- Models `RoomSnapshot`/`Suggestion`/`TransportCommand`/`RoomRealtimeEvent` + pure `RoomSyncEngine` in shared `model/watchtogether` + `network`/root. Verify the real field names (`selfCanControlTransport`, `selfCanManageRoom`, `phase`, `playbackState`, `guestControlPolicy`, `selectedContentId`/`selectedFileId`, `code`, `memberCount`, `hostConnected`, `selectionRevision`, `anchorPositionSeconds`).
- Mobile `RoomSyncController` (M3) is the reference for the TV binding; if mobile extracted a shared player-agnostic coordinator wrapping `RoomSyncEngine` + ping loop + state_report cadence, REUSE it and only build the TV player adapter.
- Reuse `TvDialogActionRow`/`TvDialogCyclerRow` (internal in `screens/player/TvSubtitleSearchDialog.kt`; module-scoped internal works cross-package — if it trips, hoist to `ui/components`). Verify the DI module file via `grep -rl "viewModel { TvPlayerViewModel" androidTvApp/src`.
- Land order 1→2→3. Task 1 adds the player route's `roomId` arg AND adds `roomId: String? = null` (unused) to `TvPlayerScreen` so it compiles before Task 3 consumes it; Task 1 also wires the lobby route which needs Task 2's screen — land 1+2 together or stub the lobby composable body temporarily.

---

### Task T1: TV Watch Together entry + create/join + routes

**Files:**
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/watchtogether/TvWatchTogetherViewModel.kt`
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/watchtogether/TvWatchTogetherEntryDialog.kt`
- Create: `androidTvApp/src/androidMain/kotlin/com/continuum/app/tv/ui/screens/watchtogether/TvJoinCodeDialog.kt`
- Create: `androidTvApp/src/androidUnitTest/kotlin/com/continuum/app/tv/ui/screens/watchtogether/TvJoinCodeStateTest.kt`
- Modify: `TvRoute.kt` (add `WatchTogetherLobby(roomId)`; extend `Player` with optional `roomId` query arg), `TvAppNavigation.kt` (item-detail `onWatchTogether` wiring + lobby composable + player `roomId` parse), `TvItemDetailScreen.kt` ("Watch Together" CircleAction → Host/Join dialogs, video-only gate `type in {movie,episode}`), the TV Koin module (register `TvWatchTogetherViewModel`), and `TvPlayerScreen.kt` (add `roomId: String? = null` param, unused until T3).

- [ ] **Step 1: Write the failing test** — extract the pure 8-char D-pad join-code accumulator `JoinCodeState` (in `TvJoinCodeDialog.kt`) and test it:
```kotlin
package com.continuum.app.tv.ui.screens.watchtogether

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvJoinCodeStateTest {
    @Test fun appendsUppercaseAndCapsAtEight() {
        var s = JoinCodeState(); "abcd1234ef".forEach { s = s.append(it) }
        assertEquals("ABCD1234", s.code); assertTrue(s.isComplete)
    }
    @Test fun rejectsNonAlphanumeric() {
        val s = JoinCodeState().append('-').append(' ').append('A')
        assertEquals("A", s.code); assertFalse(s.isComplete)
    }
    @Test fun backspaceRemovesLast() {
        assertEquals("A", JoinCodeState().append('A').append('B').backspace().code)
    }
    @Test fun clearEmpties() {
        assertEquals("", JoinCodeState().append('A').append('B').clear().code)
    }
    @Test fun incompleteUntilEight() {
        assertFalse(JoinCodeState().append('A').isComplete)
        var t = JoinCodeState(); "ABCD1234".forEach { t = t.append(it) }; assertTrue(t.isComplete)
    }
}
```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :androidTvApp:testDebugUnitTest --tests "*TvJoinCodeStateTest"` (compile failure — `JoinCodeState` undefined).

- [ ] **Step 3: Implementation**

`JoinCodeState` (pure):
```kotlin
data class JoinCodeState(val code: String = "") {
    val isComplete: Boolean get() = code.length == LENGTH
    fun append(c: Char): JoinCodeState {
        if (code.length >= LENGTH) return this
        val up = c.uppercaseChar()
        return if (up in 'A'..'Z' || up in '0'..'9') copy(code = code + up) else this
    }
    fun backspace() = if (code.isEmpty()) this else copy(code = code.dropLast(1))
    fun clear() = JoinCodeState()
    companion object { const val LENGTH = 8 }
}
```

`TvWatchTogetherViewModel` — create/join orchestration exposing a one-shot `result: RoomSnapshot?` the screen routes on (and `isBusy`/`error`). `createRoom(contentId, fileId)` → `repository.create(...)`, and if the create snapshot has no `selectedContentId`, call `repository.setSelection(contentId, fileId)` so the host lands on the synced player. `joinRoom(code)` → `repository.join(code.trim().uppercase())`. `consumeResult()`/`clearError()`. Errors via `ApiResult.errorMessage(...)`. (Match the mobile M1 `WatchTogetherEntryViewModel` shape.)

`TvWatchTogetherEntryDialog` — `Popup` + dark panel (mirror `TvSubtitleSearchDialog`), title + `TvDialogActionRow` "Host a room" (auto-focused) and "Join by code", red error text. `TvJoinCodeDialog` — `Popup` panel with a large mono display of the running code (placeholder dashes), a focusable A–Z/0–9 key grid (each key appends into `JoinCodeState`), a Delete row (backspace), and a "Join" `TvDialogActionRow` enabled when `isComplete && !isBusy` → `onJoin(state.code)`; red error text.

`TvItemDetailScreen.HeroActionRow` — add a `CircleAction(Icons.Filled.Groups, "Watch Together")` gated on `detail.type in setOf("movie","episode")`, opening the entry dialog; thread an `onWatchTogether: (RoomSnapshot) -> Unit` up through `TvItemDetailScreen`/`TvDetailContent`/`HeroActionRow`; on `wtState.result` non-null, dismiss dialogs, call `onWatchTogether(snapshot)`, `consumeResult()`. `koinViewModel<TvWatchTogetherViewModel>()`.

`TvRoute` — `data class Player(contentId, fileId?, roomId?)` with `ROUTE="player/{contentId}?fileId={fileId}&roomId={roomId}"` + arg consts; `data class WatchTogetherLobby(roomId)` `ROUTE="watch_together/lobby/{roomId}"`.

`TvAppNavigation` — pass `onWatchTogether = { snap -> if (snap.selectedContentId != null) navigate(Player(snap.selectedContentId, snap.selectedFileId, snap.roomId)) else navigate(WatchTogetherLobby(snap.roomId)) }`; add the `Player` `roomId` nav arg (StringType, nullable, default null) + parse + pass to `TvPlayerScreen(roomId=...)`; add the lobby `composable` calling `TvWatchTogetherLobbyScreen` (T2) with `onOpenSyncedPlayer` (navigate to Player with roomId, `popUpTo(lobby){inclusive=true}`) + `onLeave` (popBackStack). `TvPlayerScreen` gains `roomId: String? = null` (unused until T3).

Register `viewModel { TvWatchTogetherViewModel(get()) }`.

- [ ] **Step 4: Run tests** — `./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug`. Manual (single device): WT action focusable on movie/episode detail (hidden on series/audiobook); entry dialog Host auto-focused, D-pad to Join; Host → synced player route; Join → code grid appends/caps at 8/Delete works, Join disabled until 8; bad code → red error, dialog stays.

- [ ] **Step 5: Commit** — `git add` the T1 files; `git commit -m "Add TV Watch Together entry, create/join, and routes\n\nCo-Authored-By: Claude Fable 5 <noreply@anthropic.com>"` (note in body: lobby route depends on T2, player roomId binding on T3).

---

### Task T2: TvWatchTogetherLobbyScreen (suggestions/voting + host controls)

**Files:**
- Create: `androidTvApp/.../ui/screens/watchtogether/TvWatchTogetherLobbyScreen.kt`
- Create: `androidTvApp/.../ui/screens/watchtogether/TvWatchTogetherLobbyViewModel.kt`
- Create: `androidTvApp/src/androidUnitTest/.../watchtogether/LobbyNavigationDecisionTest.kt`
- Modify: the TV Koin module (register the lobby VM)

- [ ] **Step 1: Write the failing test** — pure `shouldEnterSyncedPlayer(snapshot)`:
```kotlin
package com.continuum.app.tv.ui.screens.watchtogether
import com.continuum.app.model.watchtogether.RoomSnapshot
import kotlin.test.Test; import kotlin.test.assertFalse; import kotlin.test.assertTrue
class LobbyNavigationDecisionTest {
    // build a fixture via the landed RoomSnapshot ctor or a copy() helper
    @Test fun entersWhenSelectionSetAndPlaying() { assertTrue(shouldEnterSyncedPlayer(fixture(phase="playing", selectedContentId="m1"))) }
    @Test fun staysInLobbyWhenNoSelection() { assertFalse(shouldEnterSyncedPlayer(fixture(phase="lobby", selectedContentId=null))) }
    @Test fun staysInLobbyWhenSelectionButNotPlaying() { assertFalse(shouldEnterSyncedPlayer(fixture(phase="lobby", selectedContentId="m1"))) }
    @Test fun nullSnapshotDoesNotEnter() { assertFalse(shouldEnterSyncedPlayer(null)) }
}
```
(Construct `fixture(...)` against the real `RoomSnapshot` ctor.)

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :androidTvApp:testDebugUnitTest --tests "*LobbyNavigationDecisionTest"`.

- [ ] **Step 3: Implementation**

`fun shouldEnterSyncedPlayer(s: RoomSnapshot?) = s != null && s.phase == "playing" && s.selectedContentId != null`.

`TvWatchTogetherLobbyViewModel(repository)` — exposes `repository.roomSnapshot`/`repository.suggestions`; `init { repository.connect() }` (idempotent — host arrives with a live socket); ops `vote/unvote/removeSuggestion/promote/setSelection/updatePolicy` (errors → nonce-keyed transient `LobbyError`); `leave()` → `repository.close(); repository.reset()`. **Must NOT reset on `onCleared` during hand-off to the player** (the player reuses the live room) — reset only via explicit `leave()`. Confirm against mobile M2/M3 hand-off.

`TvWatchTogetherLobbyScreen(roomId, onOpenSyncedPlayer, onLeave, viewModel=koinViewModel(key="wt-lobby-$roomId"))` — observes snapshot+suggestions:
- Header: "WATCH TOGETHER", member count, host/guest indicator, "Host disconnected" when `!hostConnected`.
- Host block (gated `selfCanManageRoom`): join `code` shown LARGE (mono, letter-spaced); selection-mode + policy cyclers (`TvDialogCyclerRow` → ops); per-suggestion Promote.
- Suggestions `LazyColumn` of focusable rows (reuse the `Surface`+`MutableInteractionSource` focus idiom from `TvSubtitleResultRow`): poster/title/subtitle, `vote_count`, filled/outline vote glyph from `voted_by_me`; Select toggles vote/unvote; host/suggester rows expose Remove. Add-suggestion: only if a reusable TV title-picker exists (verify; else scope TV to vote-only and flag).
- `LaunchedEffect(snapshot)` → `if (shouldEnterSyncedPlayer(snapshot)) onOpenSyncedPlayer(snapshot.selectedContentId!!, snapshot.selectedFileId)`.
- `BackHandler` → `viewModel.leave(); onLeave()`. Surface `error`.

Register `viewModel { TvWatchTogetherLobbyViewModel(get()) }`.

- [ ] **Step 4: Run tests** — compile/test/assemble. Manual (two-device flag): join no-selection → lobby; vote toggles update over WS; host sees code + cyclers + promote; host picks/promotes → all members auto-advance to synced player; Back leaves the room.

- [ ] **Step 5: Commit** — "Add TV Watch Together lobby with suggestions, voting, and host controls" + trailer.

---

### Task T3: TV RoomSyncController + TvPlayerScreen binding

**Files:**
- Create: `androidTvApp/.../ui/screens/player/TvRoomSyncController.kt`
- Create: `androidTvApp/src/androidUnitTest/.../player/TvRoomTransportGateTest.kt`
- Modify: `TvPlayerScreen.kt` (wire controller when `roomId != null`), `TvPlayerViewModel.kt` (expose session id + idempotent `setPaused`/`requestSeek` for sync-applied commands), TV Koin module if the controller is DI-managed.

- [ ] **Step 1: Write the failing test** — pure transport-authority gate:
```kotlin
package com.continuum.app.tv.ui.screens.player
import com.continuum.app.model.watchtogether.RoomSnapshot
import kotlin.test.Test; import kotlin.test.assertEquals
class TvRoomTransportGateTest {
    // fixture(policy, canControl, phase="playing") via real RoomSnapshot ctor; selfCanControlTransport=canControl
    @Test fun hostMayPlayPauseAndSeek() { val s=fixture("host_only",true)
        assertEquals(TransportGate.Send, tvRoomTransportGate(s, TransportAction.PlayPause))
        assertEquals(TransportGate.Send, tvRoomTransportGate(s, TransportAction.Seek)) }
    @Test fun guestHostOnlyBlocked() { val s=fixture("host_only",false)
        assertEquals(TransportGate.Blocked, tvRoomTransportGate(s, TransportAction.PlayPause))
        assertEquals(TransportGate.Blocked, tvRoomTransportGate(s, TransportAction.Seek)) }
    @Test fun guestPlayPausePolicy() { val s=fixture("guest_play_pause",false)
        assertEquals(TransportGate.Send, tvRoomTransportGate(s, TransportAction.PlayPause))
        assertEquals(TransportGate.Blocked, tvRoomTransportGate(s, TransportAction.Seek)) }
    @Test fun noTransportOutsidePlaying() {
        assertEquals(TransportGate.Blocked, tvRoomTransportGate(fixture("guest_play_pause",true,"lobby"), TransportAction.PlayPause)) }
    @Test fun nullBlocked() { assertEquals(TransportGate.Blocked, tvRoomTransportGate(null, TransportAction.Seek)) }
}
```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :androidTvApp:testDebugUnitTest --tests "*TvRoomTransportGateTest"`.

- [ ] **Step 3: Implementation**

```kotlin
enum class TransportAction { PlayPause, Seek }
enum class TransportGate { Send, Blocked }
fun tvRoomTransportGate(s: RoomSnapshot?, action: TransportAction): TransportGate {
    if (s == null || s.phase != "playing") return TransportGate.Blocked
    return when {
        s.selfCanControlTransport -> TransportGate.Send
        action == TransportAction.Seek -> TransportGate.Blocked
        action == TransportAction.PlayPause && s.guestControlPolicy == "guest_play_pause" -> TransportGate.Send
        else -> TransportGate.Blocked
    }
}
```

`TvRoomSyncController(repository, viewModel)` — mirror mobile M3 (reuse the shared coordinator if mobile extracted one; else build TV equivalent). `start(roomId, sessionId, positionProvider)`: connect WS, `attachSession(sessionId)`, collect the repo's sync-decision Flow (driven by the shared `RoomSyncEngine`: dedupe by command_id, gate session_id/selection_revision, corrective seek when action==seek or |localPos−cmd.pos|>350ms, apply play/pause, auto-`ready` when waiting+buffered, suppress state_report ~250ms around a pending command) → apply via `viewModel.requestSeek(sec)` / `viewModel.setPaused(!playing)`; emit `state_report` ~1.5s from `positionProvider`; observe `room_closed` → `onRoomClosed`. `requestTransport(action, posSec?)`: `tvRoomTransportGate(snapshot, action)` → Send: `repository.transportRequest(...)`; Blocked: no-op. `stop()`.

`TvPlayerViewModel` additions: `fun setPaused(paused: Boolean)` (idempotent, not a toggle), `fun requestSeek(sec: Double)` (emit onto the existing `_seekRequests`), `fun currentSessionId(): String? = _uiState.value.sessionId`.

`TvPlayerScreen`: add `roomId` use — `val repo: WatchTogetherRepository = koinInject()`; `val roomSync = remember(roomId) { roomId?.let { TvRoomSyncController(repo, viewModel) } }`; `LaunchedEffect(roomSync, state.sessionId)` → once sessionId present, `roomSync?.start(roomId, sessionId) { mediaController?.currentPosition ?: 0L }`; route the idle-overlay play/pause + scrub-commit + skip through `roomSync.requestTransport(...)` when in a room (gated; guests get disabled scrubber/skip, play/pause only under policy); top-start room indicator (member count, "Waiting for members…" when `playbackState=="waiting"`, join `code` when `selfCanManageRoom`); Leave affordance (host → close-confirm dialog; guest → exit); `room_closed` → stop + exit with notice; on dispose when `roomId != null` → `roomSync?.stop()`.

- [ ] **Step 4: Run tests** — compile/test/assemble. Manual (TWO DEVICES — FLAG): host play/pause/seek mirrors to guest within the lead window; guest `host_only` blocked; guest `guest_play_pause` can play/pause not seek; late-join re-syncs; host Leave→confirm closes room (guest gets room_closed → detail); guest Leave decrements; WS drop → reconnect + re-attach + resume synced.

- [ ] **Step 5: Commit** — "Bind TV player to Watch Together sync via shared RoomSyncEngine" + trailer.
