# Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In-app notification inbox, unread badge, per-profile preferences, and live foreground updates on mobile + TV, per docs/superpowers/specs/2026-06-12-notifications-design.md. Built against prairie-server open PR #136 (head 5d7ef15f).

**Architecture:** REST is the source of truth; the events websocket is a foreground accelerator only. Shared KMP layer (models verified against the PR's Go source, NotificationsApi, a singleton NotificationsRepository with REST + a lifecycle-gated realtime client, pure applyEvent fold) feeds a mobile bell+inbox+settings surface and a TV badge+inbox+settings surface. No FCM/push (server v2; no device-token endpoint exists).

**Tech Stack:** Kotlin Multiplatform, Ktor (adds WebSockets plugin), kotlinx.serialization, Koin, androidx.lifecycle-process, Jetpack Compose + TV Compose, Media3-adjacent.

**Sections:** S = shared (S1-S5), M = mobile (M1-M4), T = TV (T1-T3). Order: S, then M, then T. Mobile/TV tasks carry Dependencies notes with ASSUMED shared signatures — executors verify against landed code (landed wins).

**Cross-section note (realtime starter):** androidApp and androidTvApp are separate application modules. The foreground-lifecycle starter is wired per-app in each Application class. If the shared layer exposes a reusable starter `single`, both apps invoke it; otherwise each app holds its own small copy (the TV plan calls this out). Reconcile at execution time.

---

## Section S: Shared layer

I have all the conventions and wire shapes pinned. Note one wire detail: `PayloadForRow` omits `PosterURL` in the conversion (only PosterPath set), but the struct has the json tag `poster_url`. The image resolver (`SetImageResolver`) populates poster_url for the API path. I'll include both fields. Now producing the plan.

### Task S1: Add ktor-client-websockets dependency + install WebSockets plugin

**Files:**
- Modify: `/Users/dev/projects/prairie/prairie-android/gradle/libs.versions.toml`
- Modify: `/Users/dev/projects/prairie/prairie-android/shared/build.gradle.kts`
- Modify: `/Users/dev/projects/prairie/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/ContinuumHttpClientImpl.kt`

This is an enabling task. It has no dedicated unit test; verification is a successful shared compile, which proves the new plugin install does not break the existing client config (ContentNegotiation, Auth, Logging, Timeout, defaultRequest).

- [ ] **Step 1: Write the failing test** — N/A (enabling task). The compile in Step 4 is the gate. Do NOT add a test that boots the client over a real socket.

- [ ] **Step 2: Run to verify current state** (baseline still green before edits)
```
./gradlew :shared:compileKotlinMetadata
```
Expected: BUILD SUCCESSFUL (baseline). We are confirming the starting point compiles so a Step-4 failure is attributable to our change.

- [ ] **Step 3: Implementation**

In `gradle/libs.versions.toml`, add under the `# Ktor` block (after `ktor-client-auth`, keeping `ktor` version ref `3.1.2`):
```toml
ktor-client-websockets = { module = "io.ktor:ktor-client-websockets", version.ref = "ktor" }
```

In `shared/build.gradle.kts`, add to `commonMain.dependencies` (after `implementation(libs.ktor.client.auth)`):
```kotlin
            implementation(libs.ktor.client.websockets)
```

In `shared/src/commonMain/kotlin/com/continuum/app/network/ContinuumHttpClientImpl.kt`, add the WebSockets import and install. Replace the import block top:
```kotlin
package com.continuum.app.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
```
Then add the `WebSockets` install inside the `platformClient.config { ... }` block, immediately after the `install(ContentNegotiation) { json(ContinuumJson) }` block and before `install(Logging)`:
```kotlin
        // Foreground notifications accelerator (events websocket). REST stays
        // the source of truth; the socket only makes the unread badge instant.
        install(WebSockets)
```

- [ ] **Step 4: Run tests** (the real gate)
```
./gradlew :shared:compileKotlinMetadata && ./gradlew :shared:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL. The WebSockets plugin installs alongside the existing plugins without conflict.

- [ ] **Step 5: Commit**
```
git add gradle/libs.versions.toml shared/build.gradle.kts shared/src/commonMain/kotlin/com/continuum/app/network/ContinuumHttpClientImpl.kt && git commit -m "Add ktor WebSockets plugin to shared HttpClient

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S2: NotificationModels.kt (REST + preference + capability + realtime frame models)

**Files:**
- Create: `/Users/dev/projects/prairie/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/model/notifications/NotificationModels.kt`
- Test: `/Users/dev/projects/prairie/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/model/notifications/NotificationModelsSerializationTest.kt`

Evidence — pinned Go wire shapes (prairie-server `origin/feat/notifications-v1`):

`internal/notifications/dispatcher.go` — `DeliveryRowPayload` (shared by inbox list, ws snapshot, `notification.created`):
```go
type DeliveryRowPayload struct {
	ID              string          `json:"id"`
	Type            string          `json:"type"`
	ProfileID       string          `json:"profile_id"`
	LibraryID       *int            `json:"library_id,omitempty"`
	SeriesID        *string         `json:"series_id,omitempty"`
	EpisodeID       *string         `json:"episode_id,omitempty"`
	SeriesTitle     string          `json:"series_title,omitempty"`
	EpisodeTitle    string          `json:"episode_title,omitempty"`
	SeasonNumber    *int            `json:"season_number,omitempty"`
	EpisodeNumber   *int            `json:"episode_number,omitempty"`
	PosterPath      string          `json:"poster_path,omitempty"`
	PosterURL       string          `json:"poster_url,omitempty"`
	PosterThumbhash string          `json:"poster_thumbhash,omitempty"`
	ReasonFlags     json.RawMessage `json:"reason_flags"`   // defaults to "{}"
	CreatedAt       time.Time       `json:"created_at"`
	ReadAt          *time.Time      `json:"read_at"`        // NOT omitempty → always present, null when unread
}
```
`event names`: `EventNotificationCreated = "notification.created"`, `EventNotificationRead = "notification.read"`.

`internal/notifications/release_types.go` — `reason_flags` typed shape + preference defaults:
```go
type ReasonFlags struct {
	Favorite         bool `json:"favorite"`
	Watchlist        bool `json:"watchlist"`
	ContinueWatching bool `json:"continue_watching"`
	NextUp           bool `json:"next_up"`
}
// DefaultPreferences: Enabled/NotifyFavorites/NotifyWatchlist/NotifyContinueWatching/NotifyNextUp all true
type Preferences struct {
	ProfileID              string `json:"profile_id"`
	Enabled                bool   `json:"enabled"`
	NotifyFavorites        bool   `json:"notify_favorites"`
	NotifyWatchlist        bool   `json:"notify_watchlist"`
	NotifyContinueWatching bool   `json:"notify_continue_watching"`
	NotifyNextUp           bool   `json:"notify_next_up"`
}
const DeliveryTypeEpisodeAvailable = "episode.available"
```
Decision: `reason_flags` is modeled as `kotlinx.serialization.json.JsonObject reasonFlags = JsonObject(emptyMap())` (default-empty, matching server `"{}"`) so unknown future flag keys never break decode, PLUS a typed convenience view `NotificationReasonFlags` decoded leniently from it. The JsonObject is the source of truth on the wire model.

`internal/api/handlers/notifications.go` — REST envelopes + capability:
```go
type notificationListResponse struct {
	Notifications []DeliveryRowPayload `json:"notifications"`
	NextCursor    string               `json:"next_cursor,omitempty"`
}
type notificationSyncResponse struct {
	Notifications []DeliveryRowPayload `json:"notifications"`
	NextCursor    string               `json:"next_cursor,omitempty"`
	UnreadCount   int                  `json:"unread_count"`
}
type unreadCountResponse struct { Count int `json:"count"` }
type wsTicketResponse struct {
	Ticket    string `json:"ticket"`
	ExpiresIn int    `json:"expires_in"`
}
type capabilityResponse struct {
	InApp       capabilityInApp          `json:"in_app"`       // {enabled bool}
	ApplePush   capabilityPush           `json:"apple_push"`   // {available, provider, supported_modes}
	AndroidPush capabilityPush           `json:"android_push"` // available:false, provider:"off", supported_modes:["in_app_only"]
	WebPush     capabilityWebPush        `json:"web_push"`     // {available, public_key?}
	Webhooks    capabilityWebhooks       `json:"webhooks"`     // {available, max_per_profile, supported_types}
	Email       capabilityAccountChannel `json:"email"`        // {available, modes, digest_hour}
	Discord     capabilityAccountChannel `json:"discord"`
}
```

`internal/api/handlers/events_ws.go` + `internal/events/types.go` — frame JSON:
```go
EventsHelloMessage{ type:"hello", schema_version:int, connection_id:string, available_channels:[]string, required_action:"subscribe" }
EventsSubscribeMessage{ type:"subscribe", request_id?:string, channels:["notifications"] }   // client→server
EventsSubscribedMessage{ type:"subscribed", request_id?:string, channels:[]string, rejected?:[{channel,code,message}] }
EventsSnapshotMessage{ type:"snapshot", channel:"notifications", timestamp:string, data:[DeliveryRowPayload...] }
EventsEventMessage{ type:"event", channel:"notifications", event:"notification.created"|"notification.read", event_id:string, timestamp:string, data:<DeliveryRowPayload | {profile_id,id} | {profile_id,all:true}> }
EventsErrorMessage{ type:"error", code:string, message:string }
```
`ws-ticket`: `POST /api/v1/events/ws-ticket` → `{ticket, expires_in}`. Socket: `GET /api/v1/events/ws?ticket=<ticket>`.

- [ ] **Step 1: Write the failing test**

`shared/src/commonTest/kotlin/com/continuum/app/model/notifications/NotificationModelsSerializationTest.kt`:
```kotlin
package com.continuum.app.model.notifications

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationModelsSerializationTest {

    // Mirrors ContinuumJson (network/ContinuumHttpClientImpl.kt).
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `decodes episode_available row with reason flags and poster`() {
        val payload = """
            {
              "id": "dlv-1",
              "type": "episode.available",
              "profile_id": "prof-7",
              "library_id": 3,
              "series_id": "series-9",
              "episode_id": "ep-101",
              "series_title": "Severance",
              "episode_title": "Cold Harbor",
              "season_number": 2,
              "episode_number": 10,
              "poster_path": "/p/sev.jpg",
              "poster_url": "https://cdn/sev.jpg",
              "poster_thumbhash": "1QcSHQRnh493V4dIh4eXh1h4kJUI",
              "reason_flags": {"favorite": true, "next_up": true},
              "created_at": "2026-06-12T09:30:00Z",
              "read_at": null
            }
        """.trimIndent()

        val row = json.decodeFromString(NotificationRow.serializer(), payload)

        assertEquals("dlv-1", row.id)
        assertEquals(NotificationType.EpisodeAvailable, row.type)
        assertEquals("prof-7", row.profileId)
        assertEquals(3, row.libraryId)
        assertEquals("series-9", row.seriesId)
        assertEquals("ep-101", row.episodeId)
        assertEquals("Severance", row.seriesTitle)
        assertEquals(2, row.seasonNumber)
        assertEquals(10, row.episodeNumber)
        assertEquals("https://cdn/sev.jpg", row.posterUrl)
        assertEquals("1QcSHQRnh493V4dIh4eXh1h4kJUI", row.posterThumbhash)
        assertNull(row.readAt)
        assertFalse(row.isRead)
        // typed flags view
        assertTrue(row.reasonFlagsTyped.favorite)
        assertTrue(row.reasonFlagsTyped.nextUp)
        assertFalse(row.reasonFlagsTyped.watchlist)
        assertFalse(row.reasonFlagsTyped.continueWatching)
        // raw JsonObject preserved
        assertEquals(true, (row.reasonFlags["favorite"] as JsonPrimitive).boolean)
    }

    @Test
    fun `unknown type decodes to a renderable row, never throws`() {
        val payload = """
            {"id":"dlv-2","type":"webhook.auto_disabled","profile_id":"prof-7",
             "reason_flags":{},"created_at":"2026-06-12T09:31:00Z","read_at":null}
        """.trimIndent()

        val row = json.decodeFromString(NotificationRow.serializer(), payload)

        assertEquals("dlv-2", row.id)
        assertEquals(NotificationType.Unknown, row.type)
        assertEquals("webhook.auto_disabled", row.rawType)
        // absent optionals default cleanly
        assertNull(row.libraryId)
        assertNull(row.seriesId)
        assertEquals("", row.seriesTitle)
        assertTrue(row.reasonFlags.isEmpty())
    }

    @Test
    fun `read row exposes read_at and isRead`() {
        val payload = """
            {"id":"dlv-3","type":"episode.available","profile_id":"p",
             "reason_flags":{},"created_at":"2026-06-12T09:00:00Z",
             "read_at":"2026-06-12T10:00:00Z"}
        """.trimIndent()
        val row = json.decodeFromString(NotificationRow.serializer(), payload)
        assertEquals("2026-06-12T10:00:00Z", row.readAt)
        assertTrue(row.isRead)
    }

    @Test
    fun `decodes list response with next_cursor`() {
        val payload = """
            {"notifications":[{"id":"a","type":"episode.available","profile_id":"p",
              "reason_flags":{},"created_at":"2026-06-12T09:00:00Z","read_at":null}],
             "next_cursor":"Y3Vyc29y"}
        """.trimIndent()
        val resp = json.decodeFromString(NotificationListResponse.serializer(), payload)
        assertEquals(1, resp.notifications.size)
        assertEquals("Y3Vyc29y", resp.nextCursor)
    }

    @Test
    fun `list response without next_cursor leaves it null`() {
        val resp = json.decodeFromString(
            NotificationListResponse.serializer(),
            """{"notifications":[]}""",
        )
        assertTrue(resp.notifications.isEmpty())
        assertNull(resp.nextCursor)
    }

    @Test
    fun `decodes sync response with unread_count`() {
        val resp = json.decodeFromString(
            NotificationSyncResponse.serializer(),
            """{"notifications":[],"next_cursor":"c2luY2U","unread_count":4}""",
        )
        assertEquals(4, resp.unreadCount)
        assertEquals("c2luY2U", resp.nextCursor)
    }

    @Test
    fun `decodes unread count and ws ticket envelopes`() {
        assertEquals(
            7,
            json.decodeFromString(UnreadCountResponse.serializer(), """{"count":7}""").count,
        )
        val ticket = json.decodeFromString(
            WsTicketResponse.serializer(),
            """{"ticket":"tkt-abc","expires_in":30}""",
        )
        assertEquals("tkt-abc", ticket.ticket)
        assertEquals(30, ticket.expiresIn)
    }

    @Test
    fun `preferences default all-enabled and round-trip`() {
        val prefs = json.decodeFromString(
            NotificationPreferences.serializer(),
            """{"profile_id":"p","enabled":true,"notify_favorites":true,
                "notify_watchlist":false,"notify_continue_watching":true,"notify_next_up":true}""",
        )
        assertTrue(prefs.enabled)
        assertFalse(prefs.notifyWatchlist)
        // partial PUT body omits nulls (encodeDefaults+explicitNulls=false → only set fields)
        val body = json.encodeToString(
            NotificationPreferencesUpdate.serializer(),
            NotificationPreferencesUpdate(notifyWatchlist = true),
        )
        assertTrue("notify_watchlist" in body)
        assertFalse("enabled" in body)
    }

    @Test
    fun `capability with android_push unavailable`() {
        val payload = """
            {"in_app":{"enabled":true},
             "apple_push":{"available":false,"provider":"off","supported_modes":["in_app_only"]},
             "android_push":{"available":false,"provider":"off","supported_modes":["in_app_only"]},
             "web_push":{"available":false},
             "webhooks":{"available":false,"max_per_profile":0,"supported_types":[]},
             "email":{"available":false,"modes":[],"digest_hour":0},
             "discord":{"available":false,"modes":[],"digest_hour":0}}
        """.trimIndent()
        val cap = json.decodeFromString(NotificationCapability.serializer(), payload)
        assertTrue(cap.inApp.enabled)
        assertFalse(cap.androidPush.available)
        assertEquals("off", cap.androidPush.provider)
        assertFalse(cap.webPush.available)
    }

    @Test
    fun `decodes hello and subscribed frames`() {
        val hello = json.decodeFromString(
            WsHello.serializer(),
            """{"type":"hello","schema_version":1,"connection_id":"c1",
                "available_channels":["catalog","notifications"],"required_action":"subscribe"}""",
        )
        assertEquals("hello", hello.type)
        assertTrue("notifications" in hello.availableChannels)

        val subscribed = json.decodeFromString(
            WsSubscribed.serializer(),
            """{"type":"subscribed","channels":["notifications"]}""",
        )
        assertEquals(listOf("notifications"), subscribed.channels)
        assertTrue(subscribed.rejected.isEmpty())
    }

    @Test
    fun `subscribe frame encodes channels`() {
        val body = json.encodeToString(
            WsSubscribe.serializer(),
            WsSubscribe(channels = listOf("notifications")),
        )
        assertTrue("\"type\":\"subscribe\"" in body)
        assertTrue("notifications" in body)
    }

    @Test
    fun `decodes snapshot frame as a raw envelope with rows`() {
        val frame = """
            {"type":"snapshot","channel":"notifications","timestamp":"2026-06-12T09:00:00Z",
             "data":[{"id":"s1","type":"episode.available","profile_id":"p",
               "reason_flags":{},"created_at":"2026-06-12T09:00:00Z","read_at":null}]}
        """.trimIndent()
        val env = json.decodeFromString(WsFrameEnvelope.serializer(), frame)
        assertEquals("snapshot", env.type)
        assertEquals("notifications", env.channel)
        assertNull(env.event)
        // data is a JsonElement (array here) parsed by the realtime decoder, not the model
        assertIs<JsonObject?>(null) // marker; data shape exercised in the decoder test (Task 4)
    }

    @Test
    fun `decodes event frame envelope preserving event name and data`() {
        val created = """
            {"type":"event","channel":"notifications","event":"notification.created",
             "event_id":"evt-1","timestamp":"2026-06-12T09:05:00Z",
             "data":{"id":"e1","type":"episode.available","profile_id":"p",
               "reason_flags":{},"created_at":"2026-06-12T09:05:00Z","read_at":null}}
        """.trimIndent()
        val env = json.decodeFromString(WsFrameEnvelope.serializer(), created)
        assertEquals("event", env.type)
        assertEquals("notification.created", env.event)

        val read = """
            {"type":"event","channel":"notifications","event":"notification.read",
             "event_id":"evt-2","timestamp":"2026-06-12T09:06:00Z",
             "data":{"profile_id":"p","id":"e1"}}
        """.trimIndent()
        val readEnv = json.decodeFromString(WsFrameEnvelope.serializer(), read)
        assertEquals("notification.read", readEnv.event)

        val readAll = """
            {"type":"event","channel":"notifications","event":"notification.read",
             "event_id":"evt-3","timestamp":"2026-06-12T09:07:00Z",
             "data":{"profile_id":"p","all":true}}
        """.trimIndent()
        val readAllEnv = json.decodeFromString(WsFrameEnvelope.serializer(), readAll)
        assertEquals("notification.read", readAllEnv.event)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
```
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.notifications.NotificationModelsSerializationTest"
```
Expected failure: compilation error / unresolved references (`NotificationRow`, `NotificationType`, `NotificationListResponse`, `WsFrameEnvelope`, etc.) — the model file does not exist yet.

- [ ] **Step 3: Implementation**

`shared/src/commonMain/kotlin/com/continuum/app/model/notifications/NotificationModels.kt`:
```kotlin
// shared/src/commonMain/kotlin/com/continuum/app/model/notifications/NotificationModels.kt
package com.continuum.app.model.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Delivery `type` registry. The server contract is explicitly extensible
 * (dispatcher.go / release_types.go) — clients MUST render unrecognized types
 * with a generic fallback, so [NotificationRow.type] is parsed leniently and
 * the raw string is always retained in [NotificationRow.rawType].
 */
enum class NotificationType {
    EpisodeAvailable,
    RequestFulfilled,
    WebhookAutoDisabled,
    Unknown,
    ;

    companion object {
        const val EpisodeAvailableWire = "episode.available"
        const val RequestFulfilledWire = "request.fulfilled"
        const val WebhookAutoDisabledWire = "webhook.auto_disabled"

        fun fromWire(wire: String): NotificationType = when (wire) {
            EpisodeAvailableWire -> EpisodeAvailable
            RequestFulfilledWire -> RequestFulfilled
            WebhookAutoDisabledWire -> WebhookAutoDisabled
            else -> Unknown
        }
    }
}

/** Realtime channel + event-name constants mirrored from the server. */
object NotificationRealtime {
    const val Channel = "notifications"
    const val EventCreated = "notification.created"
    const val EventRead = "notification.read"
    const val ActionSubscribe = "subscribe"
}

/**
 * Typed convenience view over [NotificationRow.reasonFlags] (release_types.go
 * `ReasonFlags`). Unknown keys in the wire object are ignored here; the raw
 * [JsonObject] on the row is the source of truth.
 */
@Serializable
data class NotificationReasonFlags(
    val favorite: Boolean = false,
    val watchlist: Boolean = false,
    @SerialName("continue_watching") val continueWatching: Boolean = false,
    @SerialName("next_up") val nextUp: Boolean = false,
) {
    val any: Boolean get() = favorite || watchlist || continueWatching || nextUp

    companion object {
        /** Lenient read straight off a wire [JsonObject] (never throws). */
        fun from(flags: JsonObject): NotificationReasonFlags = NotificationReasonFlags(
            favorite = flags.bool("favorite"),
            watchlist = flags.bool("watchlist"),
            continueWatching = flags.bool("continue_watching"),
            nextUp = flags.bool("next_up"),
        )

        private fun JsonObject.bool(key: String): Boolean =
            (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
    }
}

/**
 * One inbox delivery row — the shape shared by the inbox list/sync/get APIs,
 * the websocket snapshot, and `notification.created` events
 * (dispatcher.go `DeliveryRowPayload`).
 *
 * [type] is the parsed enum; [rawType] preserves the original wire string so a
 * generic fallback card can label unknown types. [reasonFlags] is kept as a
 * raw [JsonObject] (defaults to empty `{}`, matching the server) so unknown
 * future flag keys never break decoding; [reasonFlagsTyped] is the convenience
 * view.
 */
@Serializable
data class NotificationRow(
    val id: String,
    @SerialName("type") val rawType: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("library_id") val libraryId: Int? = null,
    @SerialName("series_id") val seriesId: String? = null,
    @SerialName("episode_id") val episodeId: String? = null,
    @SerialName("series_title") val seriesTitle: String = "",
    @SerialName("episode_title") val episodeTitle: String = "",
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("poster_path") val posterPath: String = "",
    @SerialName("poster_url") val posterUrl: String = "",
    @SerialName("poster_thumbhash") val posterThumbhash: String = "",
    @SerialName("reason_flags") val reasonFlags: JsonObject = JsonObject(emptyMap()),
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
) {
    val type: NotificationType get() = NotificationType.fromWire(rawType)

    val reasonFlagsTyped: NotificationReasonFlags get() = NotificationReasonFlags.from(reasonFlags)

    val isRead: Boolean get() = !readAt.isNullOrBlank()
}

/** GET /api/v1/notifications — newest-first page. */
@Serializable
data class NotificationListResponse(
    val notifications: List<NotificationRow> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

/** GET /api/v1/notifications/sync — ascending catch-up; adds unread_count. */
@Serializable
data class NotificationSyncResponse(
    val notifications: List<NotificationRow> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
)

/** GET /api/v1/notifications/unread-count. */
@Serializable
data class UnreadCountResponse(val count: Int = 0)

/** POST /api/v1/events/ws-ticket. */
@Serializable
data class WsTicketResponse(
    val ticket: String,
    @SerialName("expires_in") val expiresIn: Int = 0,
)

/** GET/PUT /api/v1/notifications/preferences (full row). */
@Serializable
data class NotificationPreferences(
    @SerialName("profile_id") val profileId: String = "",
    val enabled: Boolean = true,
    @SerialName("notify_favorites") val notifyFavorites: Boolean = true,
    @SerialName("notify_watchlist") val notifyWatchlist: Boolean = true,
    @SerialName("notify_continue_watching") val notifyContinueWatching: Boolean = true,
    @SerialName("notify_next_up") val notifyNextUp: Boolean = true,
)

/**
 * Partial PUT body. Only set (non-null) fields are sent — with
 * `explicitNulls = false` the nulls are omitted, matching the server's
 * pointer-field "omitted keeps current value" semantics.
 */
@Serializable
data class NotificationPreferencesUpdate(
    val enabled: Boolean? = null,
    @SerialName("notify_favorites") val notifyFavorites: Boolean? = null,
    @SerialName("notify_watchlist") val notifyWatchlist: Boolean? = null,
    @SerialName("notify_continue_watching") val notifyContinueWatching: Boolean? = null,
    @SerialName("notify_next_up") val notifyNextUp: Boolean? = null,
)

/** GET /api/v1/notifications/capability — drives the settings UI. */
@Serializable
data class NotificationCapability(
    @SerialName("in_app") val inApp: CapabilityInApp = CapabilityInApp(),
    @SerialName("apple_push") val applePush: CapabilityPush = CapabilityPush(),
    @SerialName("android_push") val androidPush: CapabilityPush = CapabilityPush(),
    @SerialName("web_push") val webPush: CapabilityWebPush = CapabilityWebPush(),
    val webhooks: CapabilityWebhooks = CapabilityWebhooks(),
    val email: CapabilityAccountChannel = CapabilityAccountChannel(),
    val discord: CapabilityAccountChannel = CapabilityAccountChannel(),
)

@Serializable
data class CapabilityInApp(val enabled: Boolean = false)

@Serializable
data class CapabilityPush(
    val available: Boolean = false,
    val provider: String = "off",
    @SerialName("supported_modes") val supportedModes: List<String> = emptyList(),
)

@Serializable
data class CapabilityWebPush(
    val available: Boolean = false,
    @SerialName("public_key") val publicKey: String = "",
)

@Serializable
data class CapabilityWebhooks(
    val available: Boolean = false,
    @SerialName("max_per_profile") val maxPerProfile: Int = 0,
    @SerialName("supported_types") val supportedTypes: List<String> = emptyList(),
)

@Serializable
data class CapabilityAccountChannel(
    val available: Boolean = false,
    val modes: List<String> = emptyList(),
    @SerialName("digest_hour") val digestHour: Int = 0,
)

// ---- Realtime frames (events websocket) -----------------------------------

/** Server `hello` frame (events_ws.go / events/types.go EventsHelloMessage). */
@Serializable
data class WsHello(
    val type: String = "hello",
    @SerialName("schema_version") val schemaVersion: Int = 0,
    @SerialName("connection_id") val connectionId: String = "",
    @SerialName("available_channels") val availableChannels: List<String> = emptyList(),
    @SerialName("required_action") val requiredAction: String = "",
)

/** Client `subscribe` frame (sent after hello, within 5s). */
@Serializable
data class WsSubscribe(
    val type: String = "subscribe",
    @SerialName("request_id") val requestId: String? = null,
    val channels: List<String> = emptyList(),
)

@Serializable
data class WsRejectedChannel(
    val channel: String = "",
    val code: String = "",
    val message: String = "",
)

/** Server `subscribed` ack frame. */
@Serializable
data class WsSubscribed(
    val type: String = "subscribed",
    @SerialName("request_id") val requestId: String? = null,
    val channels: List<String> = emptyList(),
    val rejected: List<WsRejectedChannel> = emptyList(),
)

/**
 * Generic server frame envelope. The realtime decoder (Task 4) dispatches on
 * [type]/[event] and parses [data] (a raw [JsonElement]: a DeliveryRowPayload
 * array for snapshots, a single row for `notification.created`, or
 * `{profile_id,id}` / `{profile_id,all:true}` for `notification.read`).
 */
@Serializable
data class WsFrameEnvelope(
    val type: String = "",
    val channel: String? = null,
    val event: String? = null,
    @SerialName("event_id") val eventId: String? = null,
    val timestamp: String? = null,
    val data: JsonElement? = null,
)

/** `notification.read` data payload: a single id, or all=true. */
@Serializable
data class NotificationReadPayload(
    @SerialName("profile_id") val profileId: String = "",
    val id: String? = null,
    val all: Boolean = false,
)
```

- [ ] **Step 4: Run tests**
```
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.model.notifications.NotificationModelsSerializationTest"
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**
```
git add shared/src/commonMain/kotlin/com/continuum/app/model/notifications/NotificationModels.kt shared/src/commonTest/kotlin/com/continuum/app/model/notifications/NotificationModelsSerializationTest.kt && git commit -m "Add notification wire models + serialization tests

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S3: NotificationsApi.kt (REST + ws-ticket) + NetworkModule registration

**Files:**
- Create: `/Users/dev/projects/prairie/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/api/NotificationsApi.kt`
- Modify: `/Users/dev/projects/prairie/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`
- Test: `/Users/dev/projects/prairie/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/network/api/NotificationsApiTest.kt`

Evidence — routes (router.go, all under `/api/v1`, `RequireProfile`):
`GET /notifications` (`status=unread`, `limit`, `before`), `GET /notifications/sync` (`since`, `limit`), `GET /notifications/{id}`, `GET /notifications/unread-count`, `GET /notifications/capability`, `GET /notifications/preferences`, `PUT /notifications/preferences`, `POST /notifications/{id}/read` (204), `POST /notifications/read-all` (204), `POST /events/ws-ticket`.

- [ ] **Step 1: Write the failing test**

`shared/src/commonTest/kotlin/com/continuum/app/network/api/NotificationsApiTest.kt`:
```kotlin
package com.continuum.app.network.api

import com.continuum.app.model.notifications.NotificationPreferencesUpdate
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotificationsApiTest {

    private class Captured {
        var method: HttpMethod? = null
        var path: String = ""
        var query: Map<String, String?> = emptyMap()
        var body: String = ""
    }

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = "{}",
        captured: Captured = Captured(),
    ): Pair<NotificationsApi, Captured> {
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
        return DefaultNotificationsApi(client) to captured
    }

    @Test
    fun `list passes status and limit, omits null before, decodes rows + cursor`() = runTest {
        val (api, captured) = api(
            responseBody = """
                {"notifications":[{"id":"a","type":"episode.available","profile_id":"p",
                  "reason_flags":{},"created_at":"2026-06-12T09:00:00Z","read_at":null}],
                 "next_cursor":"Y3Vy"}
            """.trimIndent(),
        )

        val result = api.list(limit = 25, unreadOnly = true, before = null)

        assertEquals(HttpMethod.Get, captured.method)
        assertEquals("/api/v1/notifications", captured.path)
        assertEquals("unread", captured.query["status"])
        assertEquals("25", captured.query["limit"])
        assertFalse("before" in captured.query.keys) // null omitted
        assertIs<ApiResult.Success<*>>(result)
        assertEquals("Y3Vy", (result as ApiResult.Success).data.nextCursor)
    }

    @Test
    fun `list without unread filter omits status and passes before cursor`() = runTest {
        val (api, captured) = api(responseBody = """{"notifications":[]}""")
        api.list(limit = 50, unreadOnly = false, before = "cur-1")
        assertFalse("status" in captured.query.keys)
        assertEquals("cur-1", captured.query["before"])
    }

    @Test
    fun `sync passes since and decodes unread_count`() = runTest {
        val (api, captured) = api(
            responseBody = """{"notifications":[],"next_cursor":"z","unread_count":3}""",
        )
        val result = api.sync(since = "s-1", limit = 50)
        assertEquals("/api/v1/notifications/sync", captured.path)
        assertEquals("s-1", captured.query["since"])
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(3, (result as ApiResult.Success).data.unreadCount)
    }

    @Test
    fun `get hits id path`() = runTest {
        val (api, captured) = api(
            responseBody = """{"id":"dlv-9","type":"episode.available","profile_id":"p",
                "reason_flags":{},"created_at":"2026-06-12T09:00:00Z","read_at":null}""",
        )
        val result = api.get("dlv-9")
        assertEquals("/api/v1/notifications/dlv-9", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals("dlv-9", (result as ApiResult.Success).data.id)
    }

    @Test
    fun `unreadCount hits path and decodes count`() = runTest {
        val (api, captured) = api(responseBody = """{"count":11}""")
        val result = api.unreadCount()
        assertEquals("/api/v1/notifications/unread-count", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(11, (result as ApiResult.Success).data.count)
    }

    @Test
    fun `markRead posts to read path and maps 204 to Unit`() = runTest {
        val (api, captured) = api(status = HttpStatusCode.NoContent, responseBody = "")
        val result = api.markRead("dlv-9")
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/notifications/dlv-9/read", captured.path)
        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `markAllRead posts to read-all path and maps 204 to Unit`() = runTest {
        val (api, captured) = api(status = HttpStatusCode.NoContent, responseBody = "")
        val result = api.markAllRead()
        assertEquals("/api/v1/notifications/read-all", captured.path)
        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `getPreferences decodes full prefs`() = runTest {
        val (api, captured) = api(
            responseBody = """{"profile_id":"p","enabled":true,"notify_favorites":true,
                "notify_watchlist":true,"notify_continue_watching":true,"notify_next_up":false}""",
        )
        val result = api.getPreferences()
        assertEquals("/api/v1/notifications/preferences", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertFalse((result as ApiResult.Success).data.notifyNextUp)
    }

    @Test
    fun `updatePreferences puts partial body omitting nulls`() = runTest {
        val (api, captured) = api(
            responseBody = """{"profile_id":"p","enabled":true,"notify_favorites":true,
                "notify_watchlist":false,"notify_continue_watching":true,"notify_next_up":true}""",
        )
        val result = api.updatePreferences(NotificationPreferencesUpdate(notifyWatchlist = false))
        assertEquals(HttpMethod.Put, captured.method)
        assertEquals("/api/v1/notifications/preferences", captured.path)
        val sent = ContinuumJson.parseToJsonElement(captured.body).jsonObject
        assertEquals(setOf("notify_watchlist"), sent.keys) // only the set field
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `capability decodes android_push unavailable`() = runTest {
        val (api, captured) = api(
            responseBody = """
                {"in_app":{"enabled":true},
                 "apple_push":{"available":false,"provider":"off","supported_modes":["in_app_only"]},
                 "android_push":{"available":false,"provider":"off","supported_modes":["in_app_only"]},
                 "web_push":{"available":false},
                 "webhooks":{"available":false,"max_per_profile":0,"supported_types":[]},
                 "email":{"available":false,"modes":[],"digest_hour":0},
                 "discord":{"available":false,"modes":[],"digest_hour":0}}
            """.trimIndent(),
        )
        val result = api.capability()
        assertEquals("/api/v1/notifications/capability", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertFalse((result as ApiResult.Success).data.androidPush.available)
    }

    @Test
    fun `wsTicket posts to ws-ticket and decodes ticket`() = runTest {
        val (api, captured) = api(responseBody = """{"ticket":"tkt-x","expires_in":30}""")
        val result = api.wsTicket()
        assertEquals(HttpMethod.Post, captured.method)
        assertEquals("/api/v1/events/ws-ticket", captured.path)
        assertIs<ApiResult.Success<*>>(result)
        assertEquals("tkt-x", (result as ApiResult.Success).data.ticket)
    }

    @Test
    fun `server error surfaces as ApiResult Error with message`() = runTest {
        val (api, _) = api(
            status = HttpStatusCode.NotFound,
            responseBody = """{"error":"not_found","message":"Notification not found"}""",
        )
        val result = api.get("missing")
        assertIs<ApiResult.Error>(result)
        assertEquals(404, result.code)
        assertEquals("Notification not found", result.message)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
```
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.network.api.NotificationsApiTest"
```
Expected failure: unresolved references `NotificationsApi` / `DefaultNotificationsApi` — the API file does not exist yet.

- [ ] **Step 3: Implementation**

`shared/src/commonMain/kotlin/com/continuum/app/network/api/NotificationsApi.kt`:
```kotlin
package com.continuum.app.network.api

import com.continuum.app.model.notifications.NotificationCapability
import com.continuum.app.model.notifications.NotificationListResponse
import com.continuum.app.model.notifications.NotificationPreferences
import com.continuum.app.model.notifications.NotificationPreferencesUpdate
import com.continuum.app.model.notifications.NotificationRow
import com.continuum.app.model.notifications.NotificationSyncResponse
import com.continuum.app.model.notifications.UnreadCountResponse
import com.continuum.app.model.notifications.WsTicketResponse
import com.continuum.app.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Profile-scoped notifications inbox, preferences, capability, and the
 * websocket handshake ticket. Behind an interface so the repository and its
 * tests can fake the transport (matching CalendarApi/SubtitlesApi).
 *
 * REST is the source of truth for the notifications feature; the websocket
 * ([com.continuum.app.network.NotificationsRealtimeClient]) is a foreground
 * accelerator that reuses [wsTicket] for its handshake.
 */
interface NotificationsApi {

    /** GET /api/v1/notifications — newest-first page; [before] pages into the past. */
    suspend fun list(
        limit: Int = 25,
        unreadOnly: Boolean = false,
        before: String? = null,
    ): ApiResult<NotificationListResponse>

    /** GET /api/v1/notifications/sync — ascending catch-up from [since]; adds unread_count. */
    suspend fun sync(
        since: String? = null,
        limit: Int = 50,
    ): ApiResult<NotificationSyncResponse>

    /** GET /api/v1/notifications/{id} — 404 for other profiles' rows. */
    suspend fun get(id: String): ApiResult<NotificationRow>

    /** GET /api/v1/notifications/unread-count. */
    suspend fun unreadCount(): ApiResult<UnreadCountResponse>

    /** POST /api/v1/notifications/{id}/read — 204, idempotent. */
    suspend fun markRead(id: String): ApiResult<Unit>

    /** POST /api/v1/notifications/read-all — 204. */
    suspend fun markAllRead(): ApiResult<Unit>

    /** GET /api/v1/notifications/preferences. */
    suspend fun getPreferences(): ApiResult<NotificationPreferences>

    /** PUT /api/v1/notifications/preferences — partial; returns full prefs. */
    suspend fun updatePreferences(update: NotificationPreferencesUpdate): ApiResult<NotificationPreferences>

    /** GET /api/v1/notifications/capability — drives the settings UI. */
    suspend fun capability(): ApiResult<NotificationCapability>

    /** POST /api/v1/events/ws-ticket — single-use short-lived websocket ticket. */
    suspend fun wsTicket(): ApiResult<WsTicketResponse>
}

class DefaultNotificationsApi(private val client: HttpClient) : NotificationsApi {

    override suspend fun list(
        limit: Int,
        unreadOnly: Boolean,
        before: String?,
    ): ApiResult<NotificationListResponse> = safeApiCall {
        client.get("/api/v1/notifications") {
            parameter("limit", limit)
            if (unreadOnly) parameter("status", "unread")
            before?.let { parameter("before", it) }
        }
    }

    override suspend fun sync(
        since: String?,
        limit: Int,
    ): ApiResult<NotificationSyncResponse> = safeApiCall {
        client.get("/api/v1/notifications/sync") {
            parameter("limit", limit)
            since?.let { parameter("since", it) }
        }
    }

    override suspend fun get(id: String): ApiResult<NotificationRow> = safeApiCall {
        client.get("/api/v1/notifications/$id")
    }

    override suspend fun unreadCount(): ApiResult<UnreadCountResponse> = safeApiCall {
        client.get("/api/v1/notifications/unread-count")
    }

    override suspend fun markRead(id: String): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/notifications/$id/read")
    }

    override suspend fun markAllRead(): ApiResult<Unit> = safeApiCall {
        client.post("/api/v1/notifications/read-all")
    }

    override suspend fun getPreferences(): ApiResult<NotificationPreferences> = safeApiCall {
        client.get("/api/v1/notifications/preferences")
    }

    override suspend fun updatePreferences(
        update: NotificationPreferencesUpdate,
    ): ApiResult<NotificationPreferences> = safeApiCall {
        client.put("/api/v1/notifications/preferences") {
            contentType(ContentType.Application.Json)
            setBody(update)
        }
    }

    override suspend fun capability(): ApiResult<NotificationCapability> = safeApiCall {
        client.get("/api/v1/notifications/capability")
    }

    override suspend fun wsTicket(): ApiResult<WsTicketResponse> = safeApiCall {
        client.post("/api/v1/events/ws-ticket")
    }
}
```

In `shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt`, add after the `SubtitlesApi` registration (inside the `module { }`):
```kotlin
    single<NotificationsApi> { DefaultNotificationsApi(get()) }
```
(The existing `import com.continuum.app.network.api.*` already covers the new types.)

- [ ] **Step 4: Run tests**
```
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.network.api.NotificationsApiTest"
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**
```
git add shared/src/commonMain/kotlin/com/continuum/app/network/api/NotificationsApi.kt shared/src/commonMain/kotlin/com/continuum/app/di/NetworkModule.kt shared/src/commonTest/kotlin/com/continuum/app/network/api/NotificationsApiTest.kt && git commit -m "Add NotificationsApi + DI registration with MockEngine tests

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S4: NotificationsRealtimeClient.kt (ticket handshake + frame decode → Flow)

**Files:**
- Create: `/Users/dev/projects/prairie/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/network/NotificationsRealtimeClient.kt`
- Test: `/Users/dev/projects/prairie/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/network/NotificationRealtimeDecoderTest.kt`

Design: socket I/O is intentionally thin and untested; the load-bearing logic is the pure `decodeRealtimeFrame(json, raw): NotificationRealtimeEvent?` function, which gets full coverage (snapshot / created / read-one / read-all / unknown→null / non-notifications channel→null / malformed→null). Frame `data` shapes come straight from events_ws.go: snapshot `data` is a `[]DeliveryRowPayload`, `notification.created` `data` is one `DeliveryRowPayload`, `notification.read` `data` is `{profile_id,id}` or `{profile_id,all:true}`.

- [ ] **Step 1: Write the failing test**

`shared/src/commonTest/kotlin/com/continuum/app/network/NotificationRealtimeDecoderTest.kt`:
```kotlin
package com.continuum.app.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationRealtimeDecoderTest {

    private val json = ContinuumJson

    @Test
    fun `decodes snapshot frame into Snapshot with rows`() {
        val frame = """
            {"type":"snapshot","channel":"notifications","timestamp":"2026-06-12T09:00:00Z",
             "data":[
               {"id":"s1","type":"episode.available","profile_id":"p","reason_flags":{},
                "created_at":"2026-06-12T09:00:00Z","read_at":null},
               {"id":"s2","type":"episode.available","profile_id":"p","reason_flags":{},
                "created_at":"2026-06-12T08:59:00Z","read_at":null}]}
        """.trimIndent()
        val event = decodeRealtimeFrame(json, frame)
        val snapshot = assertIs<NotificationRealtimeEvent.Snapshot>(event)
        assertEquals(listOf("s1", "s2"), snapshot.rows.map { it.id })
    }

    @Test
    fun `decodes notification_created event into Created with one row`() {
        val frame = """
            {"type":"event","channel":"notifications","event":"notification.created",
             "event_id":"e1","timestamp":"2026-06-12T09:05:00Z",
             "data":{"id":"c1","type":"episode.available","profile_id":"p",
               "reason_flags":{"favorite":true},
               "created_at":"2026-06-12T09:05:00Z","read_at":null}}
        """.trimIndent()
        val event = decodeRealtimeFrame(json, frame)
        val created = assertIs<NotificationRealtimeEvent.Created>(event)
        assertEquals("c1", created.row.id)
        assertTrue(created.row.reasonFlagsTyped.favorite)
    }

    @Test
    fun `decodes notification_read for a single id into Read`() {
        val frame = """
            {"type":"event","channel":"notifications","event":"notification.read",
             "event_id":"e2","timestamp":"2026-06-12T09:06:00Z",
             "data":{"profile_id":"p","id":"c1"}}
        """.trimIndent()
        val event = decodeRealtimeFrame(json, frame)
        val read = assertIs<NotificationRealtimeEvent.Read>(event)
        assertEquals("c1", read.id)
    }

    @Test
    fun `decodes notification_read all into ReadAll`() {
        val frame = """
            {"type":"event","channel":"notifications","event":"notification.read",
             "event_id":"e3","timestamp":"2026-06-12T09:07:00Z",
             "data":{"profile_id":"p","all":true}}
        """.trimIndent()
        val event = decodeRealtimeFrame(json, frame)
        assertIs<NotificationRealtimeEvent.ReadAll>(event)
    }

    @Test
    fun `hello and subscribed frames decode to null (not surfaced)`() {
        assertNull(decodeRealtimeFrame(json, """{"type":"hello","schema_version":1,
            "connection_id":"c","available_channels":["notifications"],
            "required_action":"subscribe"}"""))
        assertNull(decodeRealtimeFrame(json, """{"type":"subscribed","channels":["notifications"]}"""))
    }

    @Test
    fun `unknown event type decodes to null without throwing`() {
        val frame = """
            {"type":"event","channel":"notifications","event":"notification.archived",
             "event_id":"e9","timestamp":"t","data":{"id":"x"}}
        """.trimIndent()
        assertNull(decodeRealtimeFrame(json, frame))
    }

    @Test
    fun `events on other channels decode to null`() {
        val frame = """
            {"type":"event","channel":"catalog","event":"catalog.updated",
             "event_id":"e","timestamp":"t","data":{}}
        """.trimIndent()
        assertNull(decodeRealtimeFrame(json, frame))
    }

    @Test
    fun `malformed json decodes to null without throwing`() {
        assertNull(decodeRealtimeFrame(json, "not json at all"))
        assertNull(decodeRealtimeFrame(json, "{"))
        assertNull(decodeRealtimeFrame(json, """{"type":"event","channel":"notifications",
            "event":"notification.created","data":"not-an-object"}"""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
```
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.network.NotificationRealtimeDecoderTest"
```
Expected failure: unresolved references `decodeRealtimeFrame` / `NotificationRealtimeEvent` — the file does not exist yet.

- [ ] **Step 3: Implementation**

`shared/src/commonMain/kotlin/com/continuum/app/network/NotificationsRealtimeClient.kt`:
```kotlin
package com.continuum.app.network

import com.continuum.app.model.notifications.NotificationReadPayload
import com.continuum.app.model.notifications.NotificationRealtime
import com.continuum.app.model.notifications.NotificationRow
import com.continuum.app.model.notifications.WsFrameEnvelope
import com.continuum.app.model.notifications.WsSubscribe
import com.continuum.app.network.api.NotificationsApi
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
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
 * A realtime event the repository folds into its StateFlows. The repository
 * owns reconnect; this client manages a single connection attempt and emits a
 * terminal [Closed] when the socket ends.
 */
sealed class NotificationRealtimeEvent {
    /** Initial `snapshot` frame: ≤25 recent unread rows for the bound profile. */
    data class Snapshot(val rows: List<NotificationRow>) : NotificationRealtimeEvent()

    /** `notification.created`: one new delivery row. */
    data class Created(val row: NotificationRow) : NotificationRealtimeEvent()

    /** `notification.read` for a single id (cross-device coherence). */
    data class Read(val id: String) : NotificationRealtimeEvent()

    /** `notification.read` with all=true. */
    object ReadAll : NotificationRealtimeEvent()

    /** The socket closed (or failed to connect). The repository reconnects. */
    data class Closed(val reason: String? = null) : NotificationRealtimeEvent()
}

/**
 * Foreground accelerator over the events websocket. One [connect] = one
 * connection: mint a ticket via [NotificationsApi.wsTicket], connect
 * `GET /api/v1/events/ws?ticket=`, await `hello`, send `subscribe`, then map
 * every server frame through [decodeRealtimeFrame] into the returned [Flow].
 * The flow completes (emitting [NotificationRealtimeEvent.Closed]) when the
 * socket ends; reconnect with capped backoff is the repository's job.
 *
 * Behind an interface so the repository's tests use a fake flow instead of a
 * real socket — the only logic worth unit-testing here is the pure
 * [decodeRealtimeFrame], which is fully covered.
 */
interface NotificationsRealtimeClient {
    fun connect(): Flow<NotificationRealtimeEvent>
}

class DefaultNotificationsRealtimeClient(
    private val client: HttpClient,
    private val api: NotificationsApi,
    private val json: Json = ContinuumJson,
) : NotificationsRealtimeClient {

    override fun connect(): Flow<NotificationRealtimeEvent> = callbackFlow {
        val ticket = when (val r = api.wsTicket()) {
            is ApiResult.Success -> r.data.ticket
            is ApiResult.Error -> {
                trySend(NotificationRealtimeEvent.Closed("ticket_error_${r.code}"))
                close()
                return@callbackFlow
            }
            is ApiResult.NetworkError -> {
                trySend(NotificationRealtimeEvent.Closed("ticket_network_error"))
                close()
                return@callbackFlow
            }
        }

        try {
            client.webSocket(urlString = "/api/v1/events/ws?ticket=$ticket") {
                // Subscribe to the notifications channel once connected. The
                // server sends `hello` first; we don't need to parse it before
                // subscribing (it just must arrive within 5s).
                send(
                    Frame.Text(
                        json.encodeToString(
                            WsSubscribe.serializer(),
                            WsSubscribe(channels = listOf(NotificationRealtime.Channel)),
                        ),
                    ),
                )
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    decodeRealtimeFrame(json, frame.readText())?.let { trySend(it) }
                }
            }
            trySend(NotificationRealtimeEvent.Closed())
        } catch (e: Throwable) {
            trySend(NotificationRealtimeEvent.Closed(e.message))
        } finally {
            close()
        }

        awaitClose { /* socket closes when the flow collector is cancelled */ }
    }
}

/**
 * Pure decode of one server frame's raw JSON text into a
 * [NotificationRealtimeEvent], or null when the frame is not a notifications
 * event we surface (hello/subscribed/error frames, other channels, unknown
 * event names, or malformed JSON). Never throws — this is the load-bearing,
 * fully-tested logic; socket I/O above is kept thin and untested.
 */
fun decodeRealtimeFrame(json: Json, raw: String): NotificationRealtimeEvent? {
    val envelope = try {
        json.decodeFromString(WsFrameEnvelope.serializer(), raw)
    } catch (_: Exception) {
        return null
    }

    return when (envelope.type) {
        "snapshot" -> {
            if (envelope.channel != NotificationRealtime.Channel) return null
            val array = envelope.data as? JsonArray ?: return null
            val rows = try {
                json.decodeFromJsonElement(
                    ListSerializer(NotificationRow.serializer()),
                    array,
                )
            } catch (_: Exception) {
                return null
            }
            NotificationRealtimeEvent.Snapshot(rows)
        }
        "event" -> {
            if (envelope.channel != NotificationRealtime.Channel) return null
            when (envelope.event) {
                NotificationRealtime.EventCreated -> {
                    val obj = envelope.data as? JsonObject ?: return null
                    val row = try {
                        json.decodeFromJsonElement(NotificationRow.serializer(), obj)
                    } catch (_: Exception) {
                        return null
                    }
                    NotificationRealtimeEvent.Created(row)
                }
                NotificationRealtime.EventRead -> {
                    val obj = envelope.data as? JsonObject ?: return null
                    val payload = try {
                        json.decodeFromJsonElement(NotificationReadPayload.serializer(), obj)
                    } catch (_: Exception) {
                        return null
                    }
                    when {
                        payload.all -> NotificationRealtimeEvent.ReadAll
                        !payload.id.isNullOrBlank() -> NotificationRealtimeEvent.Read(payload.id)
                        else -> null
                    }
                }
                else -> null // unknown / future event names
            }
        }
        else -> null // hello, subscribed, error, etc.
    }
}
```

- [ ] **Step 4: Run tests**
```
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.network.NotificationRealtimeDecoderTest"
```
Expected: BUILD SUCCESSFUL, all decoder tests pass.

- [ ] **Step 5: Commit**
```
git add shared/src/commonMain/kotlin/com/continuum/app/network/NotificationsRealtimeClient.kt shared/src/commonTest/kotlin/com/continuum/app/network/NotificationRealtimeDecoderTest.kt && git commit -m "Add notifications realtime client with pure frame decoder + tests

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task S5: NotificationsRepository.kt (singleton StateFlows + pure applyEvent fold)

**Files:**
- Create: `/Users/dev/projects/prairie/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/repository/NotificationsRepository.kt`
- Modify: `/Users/dev/projects/prairie/prairie-android/shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt`
- Test: `/Users/dev/projects/prairie/prairie-android/shared/src/commonTest/kotlin/com/continuum/app/repository/NotificationsRepositoryTest.kt`

Design: REST is the source of truth; `refresh()`/`loadMore()` drive the lists; `connectRealtime(scope)` collects the realtime client flow with capped-backoff reconnect and folds events into the same StateFlows. The fold is a pure top-level `applyEvent(state, event): NotificationsState` plus a pure `recomputeUnread(rows): Int`, both fully unit-tested. The socket reconnect loop and lifecycle wiring are thin and exercised only via a fake flow under `runTest`.

- [ ] **Step 1: Write the failing test**

`shared/src/commonTest/kotlin/com/continuum/app/repository/NotificationsRepositoryTest.kt`:
```kotlin
package com.continuum.app.repository

import com.continuum.app.model.notifications.NotificationCapability
import com.continuum.app.model.notifications.NotificationListResponse
import com.continuum.app.model.notifications.NotificationPreferences
import com.continuum.app.model.notifications.NotificationPreferencesUpdate
import com.continuum.app.model.notifications.NotificationRow
import com.continuum.app.model.notifications.NotificationSyncResponse
import com.continuum.app.model.notifications.UnreadCountResponse
import com.continuum.app.model.notifications.WsTicketResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.network.NotificationRealtimeEvent
import com.continuum.app.network.api.NotificationsApi
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsRepositoryTest {

    private fun row(
        id: String,
        createdAt: String,
        readAt: String? = null,
    ) = NotificationRow(
        id = id,
        rawType = "episode.available",
        profileId = "p",
        reasonFlags = JsonObject(emptyMap()),
        createdAt = createdAt,
        readAt = readAt,
    )

    // ---- pure recomputeUnread -------------------------------------------------

    @Test
    fun `recomputeUnread counts only unread rows`() {
        val rows = listOf(
            row("a", "2026-06-12T09:00:00Z"),
            row("b", "2026-06-12T08:00:00Z", readAt = "2026-06-12T09:30:00Z"),
            row("c", "2026-06-12T07:00:00Z"),
        )
        assertEquals(2, recomputeUnread(rows))
    }

    // ---- pure applyEvent fold -------------------------------------------------

    @Test
    fun `created prepends row and increments unread`() {
        val state = NotificationsState(
            rows = listOf(row("a", "2026-06-12T09:00:00Z")),
            unreadCount = 1,
        )
        val next = applyEvent(
            state,
            NotificationRealtimeEvent.Created(row("b", "2026-06-12T09:05:00Z")),
        )
        assertEquals(listOf("b", "a"), next.rows.map { it.id })
        assertEquals(2, next.unreadCount)
    }

    @Test
    fun `created dedupes an already-present id without double counting`() {
        val state = NotificationsState(
            rows = listOf(row("a", "2026-06-12T09:00:00Z")),
            unreadCount = 1,
        )
        val next = applyEvent(
            state,
            NotificationRealtimeEvent.Created(row("a", "2026-06-12T09:00:00Z")),
        )
        assertEquals(listOf("a"), next.rows.map { it.id })
        assertEquals(1, next.unreadCount)
    }

    @Test
    fun `read flips read_at, decrements unread, and is idempotent`() {
        val state = NotificationsState(
            rows = listOf(
                row("a", "2026-06-12T09:00:00Z"),
                row("b", "2026-06-12T08:00:00Z"),
            ),
            unreadCount = 2,
        )
        val once = applyEvent(state, NotificationRealtimeEvent.Read("a"))
        assertTrue(once.rows.first { it.id == "a" }.isRead)
        assertEquals(1, once.unreadCount)

        // Idempotent: reading an already-read row does not go negative.
        val twice = applyEvent(once, NotificationRealtimeEvent.Read("a"))
        assertEquals(1, twice.unreadCount)
    }

    @Test
    fun `read for unknown id is a no-op`() {
        val state = NotificationsState(
            rows = listOf(row("a", "2026-06-12T09:00:00Z")),
            unreadCount = 1,
        )
        val next = applyEvent(state, NotificationRealtimeEvent.Read("zzz"))
        assertEquals(state, next)
    }

    @Test
    fun `readAll marks every row read and zeroes unread`() {
        val state = NotificationsState(
            rows = listOf(
                row("a", "2026-06-12T09:00:00Z"),
                row("b", "2026-06-12T08:00:00Z", readAt = "2026-06-12T09:30:00Z"),
            ),
            unreadCount = 1,
        )
        val next = applyEvent(state, NotificationRealtimeEvent.ReadAll)
        assertTrue(next.rows.all { it.isRead })
        assertEquals(0, next.unreadCount)
    }

    @Test
    fun `snapshot merges by id newest-first and recomputes unread`() {
        val state = NotificationsState(
            rows = listOf(
                row("a", "2026-06-12T09:00:00Z"),
                row("old", "2026-06-12T06:00:00Z", readAt = "2026-06-12T07:00:00Z"),
            ),
            unreadCount = 1,
        )
        // snapshot brings a new unread "b" plus an updated "a" (now read).
        val next = applyEvent(
            state,
            NotificationRealtimeEvent.Snapshot(
                listOf(
                    row("b", "2026-06-12T09:10:00Z"),
                    row("a", "2026-06-12T09:00:00Z", readAt = "2026-06-12T09:20:00Z"),
                ),
            ),
        )
        // dedupe by id, snapshot copy of "a" wins, sorted newest-first.
        assertEquals(listOf("b", "a", "old"), next.rows.map { it.id })
        assertEquals(1, next.rows.distinctBy { it.id }.size.let { _ -> next.rows.count { it.id == "a" } })
        assertEquals(1, next.unreadCount) // only "b" unread
    }

    @Test
    fun `closed event leaves state unchanged`() {
        val state = NotificationsState(
            rows = listOf(row("a", "2026-06-12T09:00:00Z")),
            unreadCount = 1,
        )
        assertEquals(state, applyEvent(state, NotificationRealtimeEvent.Closed("x")))
    }

    // ---- repository over a fake API ------------------------------------------

    private class FakeNotificationsApi : NotificationsApi {
        var listResponse: ApiResult<NotificationListResponse> =
            ApiResult.Success(NotificationListResponse())
        var unreadResponse: ApiResult<UnreadCountResponse> =
            ApiResult.Success(UnreadCountResponse(0))
        var markReadResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var markAllReadResult: ApiResult<Unit> = ApiResult.Success(Unit)
        var prefsResponse: ApiResult<NotificationPreferences> =
            ApiResult.Success(NotificationPreferences())
        var capabilityResponse: ApiResult<NotificationCapability> =
            ApiResult.Success(NotificationCapability())
        var markReadCalls = mutableListOf<String>()
        var markAllReadCalls = 0

        override suspend fun list(limit: Int, unreadOnly: Boolean, before: String?) = listResponse
        override suspend fun sync(since: String?, limit: Int) =
            ApiResult.Success(NotificationSyncResponse())
        override suspend fun get(id: String) = ApiResult.Success(
            NotificationRow(id, "episode.available", "p", createdAt = ""),
        )
        override suspend fun unreadCount() = unreadResponse
        override suspend fun markRead(id: String): ApiResult<Unit> {
            markReadCalls += id
            return markReadResult
        }
        override suspend fun markAllRead(): ApiResult<Unit> {
            markAllReadCalls++
            return markAllReadResult
        }
        override suspend fun getPreferences() = prefsResponse
        override suspend fun updatePreferences(update: NotificationPreferencesUpdate) = prefsResponse
        override suspend fun capability() = capabilityResponse
        override suspend fun wsTicket() = ApiResult.Success(WsTicketResponse("t", 30))
    }

    @Test
    fun `refresh loads unread count and first page`() = kotlinx.coroutines.test.runTest {
        val api = FakeNotificationsApi().apply {
            unreadResponse = ApiResult.Success(UnreadCountResponse(2))
            listResponse = ApiResult.Success(
                NotificationListResponse(
                    notifications = listOf(
                        row("a", "2026-06-12T09:00:00Z"),
                        row("b", "2026-06-12T08:00:00Z", readAt = "2026-06-12T09:30:00Z"),
                    ),
                    nextCursor = "cur-1",
                ),
            )
        }
        val repo = NotificationsRepository(api)
        repo.refresh()
        assertEquals(2, repo.unreadCount.value)
        assertEquals(listOf("a", "b"), repo.rows.value.map { it.id })
        assertEquals("cur-1", repo.nextCursor.value)
    }

    @Test
    fun `loadMore appends the next page and dedupes`() = kotlinx.coroutines.test.runTest {
        val api = FakeNotificationsApi().apply {
            listResponse = ApiResult.Success(
                NotificationListResponse(
                    notifications = listOf(row("a", "2026-06-12T09:00:00Z")),
                    nextCursor = "cur-1",
                ),
            )
        }
        val repo = NotificationsRepository(api)
        repo.refresh()
        api.listResponse = ApiResult.Success(
            NotificationListResponse(
                notifications = listOf(
                    row("a", "2026-06-12T09:00:00Z"), // duplicate of page 1
                    row("z", "2026-06-12T07:00:00Z"),
                ),
                nextCursor = null,
            ),
        )
        repo.loadMore("cur-1")
        assertEquals(listOf("a", "z"), repo.rows.value.map { it.id })
        assertEquals(null, repo.nextCursor.value)
    }

    @Test
    fun `markRead is optimistic and reverts on failure`() = kotlinx.coroutines.test.runTest {
        val api = FakeNotificationsApi().apply {
            listResponse = ApiResult.Success(
                NotificationListResponse(notifications = listOf(row("a", "2026-06-12T09:00:00Z"))),
            )
            unreadResponse = ApiResult.Success(UnreadCountResponse(1))
        }
        val repo = NotificationsRepository(api)
        repo.refresh()
        assertEquals(1, repo.unreadCount.value)

        api.markReadResult = ApiResult.Error(500, "internal_error", "boom")
        repo.markRead("a")
        // reverted
        assertEquals(1, repo.unreadCount.value)
        assertFalse(repo.rows.value.first { it.id == "a" }.isRead)
        assertEquals(listOf("a"), api.markReadCalls)
    }

    @Test
    fun `connectRealtime folds a created event into the state flows`() = kotlinx.coroutines.test.runTest {
        val api = FakeNotificationsApi()
        val events = kotlinx.coroutines.flow.MutableSharedFlow<NotificationRealtimeEvent>(
            replay = 0, extraBufferCapacity = 8,
        )
        val realtime = object : com.continuum.app.network.NotificationsRealtimeClient {
            override fun connect(): kotlinx.coroutines.flow.Flow<NotificationRealtimeEvent> = events
        }
        val repo = NotificationsRepository(api, realtimeFactory = { realtime })
        val job = repo.connectRealtime(this)
        events.emit(NotificationRealtimeEvent.Created(row("live", "2026-06-12T10:00:00Z")))
        kotlinx.coroutines.yield()
        assertEquals(listOf("live"), repo.rows.value.map { it.id })
        assertEquals(1, repo.unreadCount.value)
        job.cancel()
    }

    @Test
    fun `reset clears rows unread and cursor on profile switch`() = kotlinx.coroutines.test.runTest {
        val api = FakeNotificationsApi().apply {
            listResponse = ApiResult.Success(
                NotificationListResponse(
                    notifications = listOf(row("a", "2026-06-12T09:00:00Z")),
                    nextCursor = "cur-1",
                ),
            )
            unreadResponse = ApiResult.Success(UnreadCountResponse(1))
        }
        val repo = NotificationsRepository(api)
        repo.refresh()
        repo.reset()
        assertTrue(repo.rows.value.isEmpty())
        assertEquals(0, repo.unreadCount.value)
        assertEquals(null, repo.nextCursor.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
```
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.NotificationsRepositoryTest"
```
Expected failure: unresolved references `NotificationsRepository`, `NotificationsState`, `applyEvent`, `recomputeUnread` — the repository file does not exist yet.

- [ ] **Step 3: Implementation**

`shared/src/commonMain/kotlin/com/continuum/app/repository/NotificationsRepository.kt`:
```kotlin
package com.continuum.app.repository

import com.continuum.app.model.notifications.NotificationCapability
import com.continuum.app.model.notifications.NotificationPreferences
import com.continuum.app.model.notifications.NotificationPreferencesUpdate
import com.continuum.app.model.notifications.NotificationRow
import com.continuum.app.network.ApiResult
import com.continuum.app.network.NotificationRealtimeEvent
import com.continuum.app.network.NotificationsRealtimeClient
import com.continuum.app.network.api.NotificationsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Immutable repository state, folded by the pure [applyEvent]. Newest-first
 * row ordering matches the inbox list API.
 */
data class NotificationsState(
    val rows: List<NotificationRow> = emptyList(),
    val unreadCount: Int = 0,
)

/** Count of unread rows. Pure. */
fun recomputeUnread(rows: List<NotificationRow>): Int = rows.count { !it.isRead }

/** Newest-first by created_at (string ISO-8601 sorts lexicographically). */
private fun List<NotificationRow>.sortedNewestFirst(): List<NotificationRow> =
    sortedByDescending { it.createdAt }

/** Dedupe by id keeping the FIRST occurrence (callers put the winner first). */
private fun List<NotificationRow>.dedupeById(): List<NotificationRow> {
    val seen = HashSet<String>(size)
    return filter { seen.add(it.id) }
}

/**
 * Pure fold of one realtime event into [state]. The repository applies the
 * result to its StateFlows; keeping this pure makes the fold fully unit
 * testable without coroutines.
 *
 *  - [NotificationRealtimeEvent.Created]: prepend (dedupe by id), recompute unread.
 *  - [NotificationRealtimeEvent.Read]: flip read_at on the row, recompute unread.
 *  - [NotificationRealtimeEvent.ReadAll]: mark every row read, unread = 0.
 *  - [NotificationRealtimeEvent.Snapshot]: merge by id (snapshot copy wins),
 *    sort newest-first, recompute unread.
 *  - [NotificationRealtimeEvent.Closed]: no-op (reconnect is the loop's job).
 */
fun applyEvent(state: NotificationsState, event: NotificationRealtimeEvent): NotificationsState =
    when (event) {
        is NotificationRealtimeEvent.Created -> {
            val merged = (listOf(event.row) + state.rows).dedupeById()
            state.copy(rows = merged, unreadCount = recomputeUnread(merged))
        }
        is NotificationRealtimeEvent.Read -> {
            if (state.rows.none { it.id == event.id }) {
                state
            } else {
                val rows = state.rows.map {
                    if (it.id == event.id && !it.isRead) it.copy(readAt = it.createdAt) else it
                }
                state.copy(rows = rows, unreadCount = recomputeUnread(rows))
            }
        }
        NotificationRealtimeEvent.ReadAll -> {
            val rows = state.rows.map { if (it.isRead) it else it.copy(readAt = it.createdAt) }
            state.copy(rows = rows, unreadCount = 0)
        }
        is NotificationRealtimeEvent.Snapshot -> {
            // Snapshot copies win on id collision, so they come first.
            val merged = (event.rows + state.rows).dedupeById().sortedNewestFirst()
            state.copy(rows = merged, unreadCount = recomputeUnread(merged))
        }
        is NotificationRealtimeEvent.Closed -> state
    }

/**
 * Singleton owner of notification inbox state. REST is the source of truth;
 * the realtime client is a foreground accelerator folded into the same flows
 * via [applyEvent]. The repository owns reconnect (capped backoff); the client
 * manages a single connection per [NotificationsRealtimeClient.connect].
 *
 * [realtimeFactory] is injected so tests can supply a fake event flow.
 */
class NotificationsRepository(
    private val api: NotificationsApi,
    private val realtimeFactory: () -> NotificationsRealtimeClient? = { null },
) {
    private val _state = MutableStateFlow(NotificationsState())

    val unreadCount: StateFlow<Int> = run {
        val flow = MutableStateFlow(0)
        flow
    }.let { _unreadCount.asStateFlow() }

    private val _unreadCount = MutableStateFlow(0)
    private val _rows = MutableStateFlow<List<NotificationRow>>(emptyList())
    private val _nextCursor = MutableStateFlow<String?>(null)
    private val _preferences = MutableStateFlow<NotificationPreferences?>(null)
    private val _capability = MutableStateFlow<NotificationCapability?>(null)

    val rows: StateFlow<List<NotificationRow>> = _rows.asStateFlow()
    val nextCursor: StateFlow<String?> = _nextCursor.asStateFlow()
    val preferences: StateFlow<NotificationPreferences?> = _preferences.asStateFlow()
    val capability: StateFlow<NotificationCapability?> = _capability.asStateFlow()

    private fun publish(state: NotificationsState) {
        _state.value = state
        _rows.value = state.rows
        _unreadCount.value = state.unreadCount
    }

    /** Foreground / inbox-open refresh: unread count + first page. */
    suspend fun refresh() {
        when (val r = api.unreadCount()) {
            is ApiResult.Success -> _unreadCount.value = r.data.count
            else -> { /* keep last value on failure (spec: badge is silent) */ }
        }
        when (val r = api.list(limit = 25, unreadOnly = false, before = null)) {
            is ApiResult.Success -> {
                val rows = r.data.notifications.dedupeById()
                publish(NotificationsState(rows = rows, unreadCount = _unreadCount.value))
                _nextCursor.value = r.data.nextCursor
            }
            else -> { /* surfaced by the caller via ApiResult */ }
        }
    }

    /** Appends the next page; dedupes by id; updates the cursor. */
    suspend fun loadMore(cursor: String) {
        when (val r = api.list(limit = 25, unreadOnly = false, before = cursor)) {
            is ApiResult.Success -> {
                val merged = (_rows.value + r.data.notifications).dedupeById()
                publish(_state.value.copy(rows = merged, unreadCount = recomputeUnread(merged)))
                _nextCursor.value = r.data.nextCursor
            }
            else -> { /* surfaced by the caller */ }
        }
    }

    /** Optimistic mark-read with revert on failure. */
    suspend fun markRead(id: String) {
        val before = _state.value
        publish(applyEvent(before, NotificationRealtimeEvent.Read(id)))
        val r = api.markRead(id)
        if (r !is ApiResult.Success) publish(before)
    }

    /** Optimistic mark-all-read with revert on failure. */
    suspend fun markAllRead() {
        val before = _state.value
        publish(applyEvent(before, NotificationRealtimeEvent.ReadAll))
        val r = api.markAllRead()
        if (r !is ApiResult.Success) publish(before)
    }

    suspend fun loadPreferences() {
        when (val r = api.getPreferences()) {
            is ApiResult.Success -> _preferences.value = r.data
            else -> { /* hidden settings on failure (spec) */ }
        }
    }

    suspend fun updatePreferences(update: NotificationPreferencesUpdate): ApiResult<NotificationPreferences> {
        val r = api.updatePreferences(update)
        if (r is ApiResult.Success) _preferences.value = r.data
        return r
    }

    suspend fun loadCapability() {
        when (val r = api.capability()) {
            is ApiResult.Success -> _capability.value = r.data
            else -> { /* hidden settings on failure (spec) */ }
        }
    }

    /** Clears all state on profile switch (badge is per-profile). */
    fun reset() {
        publish(NotificationsState())
        _nextCursor.value = null
        _preferences.value = null
        _capability.value = null
    }

    /**
     * Collects the realtime client with capped-backoff reconnect, folding each
     * event into the state flows. Returns the [Job] so the lifecycle starter
     * can cancel it when the app backgrounds. A null factory (no realtime
     * binding) makes this a no-op completed job.
     */
    fun connectRealtime(scope: CoroutineScope): Job = scope.launch {
        val client = realtimeFactory() ?: return@launch
        var backoffMs = INITIAL_BACKOFF_MS
        while (true) {
            try {
                client.connect().collect { event ->
                    if (event !is NotificationRealtimeEvent.Closed) {
                        backoffMs = INITIAL_BACKOFF_MS // healthy traffic resets backoff
                    }
                    publish(applyEvent(_state.value, event))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // fall through to backoff-reconnect
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
```

> Note for the implementer: the `unreadCount` declaration above is written redundantly to make the intent explicit; simplify it to the canonical one-liner during implementation:
> ```kotlin
> val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()
> ```
> (remove the `run { ... }.let { ... }` scaffolding and keep only the `_unreadCount`/`_rows`/etc. backing fields). The Step-4 run must pass with the simplified form.

In `shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt`, add the import and registration. Add to the imports:
```kotlin
import com.continuum.app.repository.NotificationsRepository
```
Add inside the `module { }` after the `SubtitlesRepository` registration:
```kotlin
    // Realtime factory is wired per-app (it needs the HttpClient + NotificationsApi);
    // here we register the repo with REST-only behavior plus a realtime factory that
    // builds the default client from the shared HttpClient.
    single {
        NotificationsRepository(
            api = get(),
            realtimeFactory = {
                com.continuum.app.network.DefaultNotificationsRealtimeClient(
                    client = get(),
                    api = get(),
                )
            },
        )
    }
```

- [ ] **Step 4: Run tests**
```
./gradlew :shared:testDebugUnitTest --tests "com.continuum.app.repository.NotificationsRepositoryTest"
```
Expected: BUILD SUCCESSFUL, all fold + repository tests pass. Then run the full shared suite to confirm nothing regressed:
```
./gradlew :shared:testDebugUnitTest
```

- [ ] **Step 5: Commit**
```
git add shared/src/commonMain/kotlin/com/continuum/app/repository/NotificationsRepository.kt shared/src/commonMain/kotlin/com/continuum/app/di/RepositoryModule.kt shared/src/commonTest/kotlin/com/continuum/app/repository/NotificationsRepositoryTest.kt && git commit -m "Add NotificationsRepository with pure applyEvent fold + realtime reconnect

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

## Section M: Mobile UI

### Task M1: App-scope realtime starter (foreground lifecycle)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `androidApp/build.gradle.kts`
- Create: `androidApp/src/androidMain/kotlin/com/continuum/app/android/notifications/NotificationsForegroundStarter.kt`
- Modify: `androidApp/src/androidMain/kotlin/com/continuum/app/android/ContinuumApplication.kt`

**Dependencies (assumed shared layer — executor verifies against landed code):**
- `com.continuum.app.repository.NotificationsRepository` singleton (Koin `get()`), exposing: `val unreadCount: StateFlow<Int>`, `val notifications: StateFlow<List<NotificationRow>>`, `val preferences: StateFlow<NotificationPreferences?>`, `val capability: StateFlow<NotificationCapability?>`, `suspend fun refresh()`, `suspend fun loadMore()`, `suspend fun markRead(id)`, `suspend fun markAllRead()`, `suspend fun updatePreferences(patch)`, `fun connectRealtime(scope: CoroutineScope)`, `fun reset()`.
- READ the landed `NotificationsRepository` and adapt: the realtime connect/reset surface may differ (e.g. a shared starter may already exist). Landed wins.

- [ ] **Step 1: Write the failing test** — Manual-check only; no extractable pure function (lifecycle wiring). The shared `applyEvent` tests cover correctness.

- [ ] **Step 2: Run test to verify it fails** — n.a.

- [ ] **Step 3: Implementation**

`androidx.lifecycle.ProcessLifecycleOwner` is in `androidx.lifecycle:lifecycle-process`, not currently on the classpath — add it. In `gradle/libs.versions.toml` under `[libraries]` (the `lifecycle` version ref already exists):
```toml
lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }
```
In `androidApp/build.gradle.kts`, inside `androidMain.dependencies` after the existing `lifecycle.*` lines:
```kotlin
            implementation(libs.lifecycle.process)
```

Create `NotificationsForegroundStarter.kt`:
```kotlin
package com.continuum.app.android.notifications

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.continuum.app.repository.NotificationsRepository
import com.continuum.app.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Drives the [NotificationsRepository] realtime connection off the app's
 * foreground lifecycle. ON_START: REST refresh + open socket. ON_STOP: tear
 * the socket down. Profile switch while foregrounded: reset + reconnect.
 * REST is the source of truth; the socket is only a foreground accelerator,
 * so a missing/failed connection degrades gracefully.
 */
class NotificationsForegroundStarter(
    private val repository: NotificationsRepository,
    private val profileRepository: ProfileRepository,
) : DefaultLifecycleObserver {

    private var realtimeScope: CoroutineScope? = null
    private var profileWatchJob: Job? = null
    private var lastProfileId: String? = null

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        startRealtime()
        profileWatchJob = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            profileRepository.activeProfileId
                .distinctUntilChanged()
                .collect { profileId ->
                    if (lastProfileId != null && profileId != lastProfileId) {
                        repository.reset()
                        restartRealtime()
                    }
                    lastProfileId = profileId
                }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        profileWatchJob?.cancel()
        profileWatchJob = null
        stopRealtime()
    }

    private fun startRealtime() {
        if (realtimeScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        realtimeScope = scope
        scope.launch { repository.refresh() }
        repository.connectRealtime(scope)
    }

    private fun stopRealtime() {
        realtimeScope?.cancel()
        realtimeScope = null
    }

    private fun restartRealtime() {
        stopRealtime()
        startRealtime()
    }
}
```
> Executor note: confirm `ProfileRepository` exposes an observable `activeProfileId` flow. The codebase uses `getActiveProfileId(): String?` in some VMs — if there's no flow, drop the profile-watch block and instead call `repository.reset()` from the profile-switch nav action; keep ON_START/ON_STOP socket control regardless.

Wire it in `ContinuumApplication.onCreate()` after `startKoin { … }`, guarded:
```kotlin
        runCatching {
            NotificationsForegroundStarter(
                repository = getKoin().get(),
                profileRepository = getKoin().get(),
            ).register()
        }.onFailure {
            android.util.Log.w("ContinuumApplication", "Notifications starter init failed", it)
        }
```
Add the import and the Koin handle (`org.koin.core.context.GlobalContext.getKoin` or the `startKoin {}` return).

- [ ] **Step 4: Run tests + manual checklist** — `./gradlew :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest :androidApp:assembleDebug`. Manual: cold start → no crash; background then foreground → socket reconnects, live badge within ~1s; airplane → badge still reflects REST on resume; switch profile → badge resets.

- [ ] **Step 5: Commit** — `git add` the four files; `git commit -m "Wire notifications realtime to app foreground lifecycle\n\nCo-Authored-By: Claude Fable 5 <noreply@anthropic.com>"`

---

### Task M2: Bell + badge in MainAppTopBar + Route.Inbox + nav registration

**Files:**
- Modify: `androidApp/.../ui/components/MainAppTopBar.kt`
- Modify: `androidApp/.../ui/navigation/Routes.kt`
- Modify: `androidApp/.../ui/navigation/AppNavigation.kt`
- Modify: `androidApp/.../ui/screens/MainScreen.kt`
- Modify: `androidApp/.../ui/screens/home/HomeScreen.kt` (thread `onInboxClick`, mirroring `onCalendarClick`)
- (Thread through LibrariesScreen/ReadingHubScreen wrappers too IF they host MainAppTopBar — `onInboxClick` is nullable so untouched call sites compile.)

**Dependencies:** `NotificationsRepository.unreadCount: StateFlow<Int>`; `Route.Inbox` hosts `InboxScreen` (Task M3).

- [ ] **Step 1: Write the failing test** — Manual-check only; the "99+" cap is the one pure bit, tested in Task M3's formatter file.

- [ ] **Step 2: Run test to verify it fails** — n.a.

- [ ] **Step 3: Implementation**

In `Routes.kt`, beside `data object Calendar : Route("calendar")`:
```kotlin
    // --- Notifications ---
    data object Inbox : Route("inbox")
```

In `MainAppTopBar.kt`, add a bell + badge before the profile button. Add param `onInboxClick: (() -> Unit)? = null` after `onCalendarClick`/`onRequestsClick`. Imports: `androidx.compose.material.icons.outlined.Notifications`, `androidx.compose.material3.Badge`, `androidx.compose.material3.BadgedBox`, `androidx.compose.runtime.collectAsState`, `com.continuum.app.repository.NotificationsRepository`, `org.koin.compose.koinInject`. Inside the trailing `Row`, between Search and the profile `Box`:
```kotlin
                if (onInboxClick != null) {
                    val notificationsRepository = koinInject<NotificationsRepository>()
                    val unreadCount by notificationsRepository.unreadCount.collectAsState()
                    HeaderActionButton(onClick = onInboxClick) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                            )
                        }
                    }
                }
```
> Executor note: match the existing icon-button idiom in this file (`HeaderActionButton` or whatever the Search button uses) — read it and reuse.

In `AppNavigation.kt`, register the route (mirror the `Route.Calendar` composable block). Import `com.continuum.app.android.ui.screens.notifications.InboxScreen`:
```kotlin
        composable(Route.Inbox.route) {
            InboxScreen(
                onBackClick = { navController.popBackStack() },
                onItemClick = { contentId -> navController.navigate(Route.ItemDetail(contentId).route) },
            )
        }
```

In `MainScreen.kt`, pass `onInboxClick = { navController.navigate(Route.Inbox.route) }` everywhere `onCalendarClick` is passed (Downloads-tab `MainAppTopBar`, the `HomeScreen` call, and any other top-bar host). In `HomeScreen.kt` thread `onInboxClick: () -> Unit` through the same plumbing levels as `onCalendarClick`.

- [ ] **Step 4: Run tests + manual checklist** — `./gradlew :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest :androidApp:assembleDebug`. Manual: bell left of avatar on all main tabs; badge numeric, >99 → "99+", 0 → none; tap → Inbox; live bump while foregrounded.

- [ ] **Step 5: Commit** — message "Add notifications bell + badge and Inbox route" + Co-Authored-By trailer.

---

### Task M3: InboxScreen — paginated list, rich/fallback cards, mark-read deep-link

**Files:**
- Create: `androidApp/.../ui/screens/notifications/InboxScreen.kt`
- Create: `androidApp/.../ui/screens/notifications/InboxFormatters.kt`
- Create: `androidApp/src/androidUnitTest/.../ui/screens/notifications/InboxFormattersTest.kt`

**Dependencies:** `NotificationsRepository` (`notifications`, `refresh`, `loadMore`, `markRead`, `markAllRead`, and an error/loading flow if present); `NotificationRow` fields per spec. Reuse `ThumbhashImage`, `ContinuumTopBar`, `EmptyStateView`, `LoadingIndicator`, `ErrorView` (read their real packages/signatures). Deep-link target: prefer `seriesId`, fall back to `episodeId`, else `libraryId` — confirm which maps to `Route.ItemDetail(contentId)` (CalendarScreen uses `detailContentId` → series id for episodes; mirror that).

- [ ] **Step 1: Write the failing test**

Create `InboxFormatters.kt`:
```kotlin
package com.continuum.app.android.ui.screens.notifications

import com.continuum.app.model.notifications.NotificationRow

internal data class InboxCardModel(
    val id: String,
    val isRich: Boolean,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val posterThumbhash: String?,
    val relativeTime: String,
    val isUnread: Boolean,
    val targetContentId: String?,
)

internal fun inboxCardModel(row: NotificationRow, nowEpochSeconds: Long): InboxCardModel {
    val isEpisode = row.type == "episode.available"
    val sxey = row.seasonNumber?.let { s -> row.episodeNumber?.let { e -> "S${s}E$e" } }
    val subtitle = if (isEpisode) {
        listOfNotNull(sxey, row.episodeTitle).joinToString(" • ").takeIf { it.isNotBlank() }
    } else {
        row.episodeTitle ?: row.seriesTitle
    }
    return InboxCardModel(
        id = row.id,
        isRich = isEpisode && (row.posterUrl != null || row.seriesTitle != null),
        title = if (isEpisode) (row.seriesTitle ?: "New episode available") else fallbackTitleForType(row.type),
        subtitle = subtitle,
        posterUrl = row.posterUrl,
        posterThumbhash = row.posterThumbhash,
        relativeTime = relativeTime(row.createdAtEpochSeconds, nowEpochSeconds),
        isUnread = row.readAt == null,
        targetContentId = row.seriesId ?: row.episodeId ?: row.libraryId?.toString(),
    )
}

internal fun fallbackTitleForType(type: String): String = when (type) {
    "request.fulfilled" -> "Request fulfilled"
    "webhook.auto_disabled" -> "Webhook disabled"
    else -> type.substringBefore('.').replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { "Notification" }
}

internal fun relativeTime(createdAtEpochSeconds: Long, nowEpochSeconds: Long): String {
    val delta = (nowEpochSeconds - createdAtEpochSeconds).coerceAtLeast(0)
    return when {
        delta < 60 -> "now"
        delta < 3_600 -> "${delta / 60}m"
        delta < 86_400 -> "${delta / 3_600}h"
        delta < 7 * 86_400 -> "${delta / 86_400}d"
        else -> formatAbsoluteDate(createdAtEpochSeconds)
    }
}
```
> Executor note: confirm `NotificationRow` exposes an epoch-seconds field; if it only has the ISO `createdAt` string, add a tiny pure parser in this file and key the helpers off it. `formatAbsoluteDate` = `Instant.ofEpochSecond(..).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d"))`.

Create `InboxFormattersTest.kt` with cases: episode → rich + "S1E4 • title" subtitle + series target + unread; read row → not unread; unknown type → not rich + generic title; known non-episode → friendly titles; relativeTime buckets (now/5m/2h/3d). (Construct `NotificationRow` per its real ctor.)

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :androidApp:testDebugUnitTest --tests "*InboxFormattersTest"` (fails: helpers/row shape).

- [ ] **Step 3: Implementation** — `InboxScreen.kt`: `Scaffold` + `ContinuumTopBar(title="Notifications")` with a "Mark all read" action (shown when any unread), a `LazyColumn` over `repository.notifications` mapped through `inboxCardModel`, `loadMore()` triggered near scroll-end via `derivedStateOf`, a rich card (ThumbhashImage poster + title + subtitle + relativeTime + unread dot) and a posterless fallback card, tap → optimistic `markRead` then `onItemClick(targetContentId)`, plus loading/empty/error states (mirror CalendarScreen's state switch). Refresh on open via `LaunchedEffect(Unit)`.
> Executor notes: if the repo exposes an error StateFlow, render `ErrorView(message, onRetry={refresh})`; markRead/markAllRead are optimistic-with-revert in the repo, so the screen needn't manage revert; confirm `ContinuumTopBar` has an `actions` slot (CalendarScreen uses it).

- [ ] **Step 4: Run tests + manual checklist** — formatter test then full `:androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest :androidApp:assembleDebug`. Manual: episode rich card; fallback card for other/unknown types; pagination appends; tap clears dot + deep-links; mark-all clears; empty state; REST fallback when socket down.

- [ ] **Step 5: Commit** — message "Add notifications InboxScreen with paginated rich/fallback cards" + trailer.

---

### Task M4: Settings "Notifications" section (capability-gated)

**Files:**
- Modify: `androidApp/.../ui/screens/settings/SettingsViewModel.kt`
- Modify: `androidApp/.../ui/screens/settings/SettingsScreen.kt`

**Dependencies:** `NotificationsRepository` (`preferences`, `capability` StateFlows, `updatePreferences(patch)`, and a way to fetch capability/prefs — folded into `refresh()` or dedicated calls). `NotificationPreferences`/`NotificationCapability` per spec. Inject into `SettingsViewModel` (verify the Koin definition updates — `viewModelOf`/constructor-DSL or explicit).

- [ ] **Step 1: Write the failing test** — Manual-check only; the preference partial-PUT is unit-tested in the shared layer.

- [ ] **Step 2: Run test to verify it fails** — n.a.

- [ ] **Step 3: Implementation** — Add `notificationsRepository` to the VM ctor. Extend `SettingsUiState` with `notificationsAvailable` (capability.in_app available), `notificationsEnabled`, `notifyFavorites`, `notifyWatchlist`, `notifyContinueWatching`, `notifyNextUp`. Add `observeNotifications()` (combine capability + preferences → state; refresh on init). Add setters that PUT a partial patch (one named field). In `SettingsScreen.kt`, add a capability-gated `SettingsSectionCard` "Notifications" (after Subtitles, before Downloads) with the master "In-app notifications" `SettingsSwitchRow` + the four sub-toggles (shown only when enabled). Hidden entirely when `!notificationsAvailable`. Never render push toggles.
> Executor notes: ignore `android_push.available` (out of scope); if `NotificationPreferences` is all-nullable build a true partial patch, else build from current state with one field overridden; match the real `SettingsSwitchRow`/`SettingsSectionCard`/`SettingsSectionHeader` idioms.

- [ ] **Step 4: Run tests + manual checklist** — `./gradlew :androidApp:compileDebugKotlinAndroid :androidApp:testDebugUnitTest :androidApp:assembleDebug`. Manual: section shows when in_app available + master reflects server; sub-toggle round-trips; sub-toggles hide when master off; section absent when capability false / fetch fails; no push toggles.

- [ ] **Step 5: Commit** — message "Add capability-gated Notifications preferences to Settings" + trailer.

## Section T: TV UI

### Dependencies (assumed landed by shared layer + mobile)

Assumed shared symbols (DI-registered; READ landed code, landed wins):
- `com.continuum.app.repository.NotificationsRepository` singleton: `val unreadCount: StateFlow<Int>`, `val notifications: StateFlow<List<NotificationRow>>`, `suspend fun refresh()`, `suspend fun markRead(id)` (optimistic), `suspend fun markAllRead()`, `loadMore`/`nextCursor`, `isLoading`/`errorMessage` flows, `suspend fun preferences()`, `suspend fun updatePreferences(patch)`, `suspend fun capability()`, `fun connectRealtime(scope)`/reset.
- `com.continuum.app.model.notifications.NotificationRow` (fields per spec DeliveryRow; `isUnread = readAt == null`), `NotificationPreferences`/patch, `NotificationCapability`.
- Pure card-model mapper + relative-time: REUSE if shared (`commonMain`); if mobile placed them in `androidApp` only, DUPLICATE into androidTvApp (separate module — androidApp is not a TV dependency) and note it for a future hoist to commonMain.

**Realtime starter reusability:** `androidApp` and `androidTvApp` are separate application modules with their own `Application` + `startKoin`. The mobile starter CLASS lives in androidApp, so TV cannot reuse the call site. TV needs its own equivalent invocation in `ContinuumTvApplication.onCreate()` — either duplicate the small `NotificationsForegroundStarter` into androidTvApp, or (cleaner) the shared layer exposes a `NotificationsRealtimeStarter` single both apps invoke. Pick based on what mobile landed; report the choice.

---

### Task T1: TV realtime starter + unread badge on the top bar

**Files:**
- Modify `androidTvApp/.../ContinuumTvApplication.kt`
- Modify `androidTvApp/build.gradle.kts` (only if `lifecycle-process` isn't already transitive)
- Modify `androidTvApp/.../ui/navigation/TvRoute.kt` (add the inbox route)
- Modify `androidTvApp/.../ui/shell/TvMainShell.kt` (observe unreadCount, host inbox route, nav hook)
- Modify `androidTvApp/.../ui/shell/TvTopMenuBar.kt` (unread badge on profile cluster)

- [ ] **Step 1: Write the failing test** — Manual-check only (Application startup + TV Compose). Shared `applyEvent` tests cover the fold.

- [ ] **Step 2: Run test to verify it fails** — n.a. Green baseline: `./gradlew :androidTvApp:testDebugUnitTest`.

- [ ] **Step 3: Implementation**

**3a. `ContinuumTvApplication.kt`** — after the `WorkManager.initialize` block in `onCreate()`, start the realtime connection (mirror mobile's call site; use the shared starter if mobile created one, else duplicate the small foreground starter into androidTvApp):
```kotlin
runCatching {
    // Notifications realtime: connect while foregrounded. Separate app
    // module → own Koin start, so TV invokes the starter here (mobile does
    // the equivalent in ContinuumApplication).
    org.koin.core.context.GlobalContext.get()
        .get<com.continuum.app.notifications.NotificationsRealtimeStarter>()
        .start()
}.onFailure {
    android.util.Log.w("ContinuumTvApplication", "Notifications realtime starter failed", it)
}
```
> If mobile's starter lives in androidApp (not shared), create `androidTvApp/.../notifications/NotificationsForegroundStarter.kt` as a copy and register/invoke it here instead.

**3b. `androidTvApp/build.gradle.kts`** — only if `ProcessLifecycleOwner` isn't resolvable for `:androidTvApp`: add `implementation(libs.lifecycle.process)`.

**3c. `TvRoute.kt`** — add an inbox sub-route to the nested shell graph, matching the existing `TvMainRoute` style (READ the actual sealed-route names):
```kotlin
data object Inbox : TvMainRoute("main/inbox")
```

**3d. `TvMainShell.kt`** — observe the badge and host the inbox. After the existing `koinInject()` calls:
```kotlin
val notificationsRepository: NotificationsRepository = koinInject()
val unreadCount by notificationsRepository.unreadCount.collectAsState()
```
Add an `openInbox` nav helper (close profile menu → navigate to the inbox route → move focus to content), add the `composable(TvMainRoute.Inbox.route) { TvInboxScreen(onOpenItemDetail = onOpenItemDetail, ...) }` to the NavHost, pass `unreadCount` into `TvTopMenuBar`, and add a "Notifications" row to the profile actions panel wired to `openInbox`.
> READ the actual TvMainShell structure (navigateToRoute/moveFocusToContent helpers, profile menu panel) and adapt these hooks to what exists.

**3e. `TvTopMenuBar.kt`** — add `unreadCount: Int = 0` param, thread it to the profile button, overlay a small badge on the avatar when `unreadCount > 0` (cap "9+"). Badge is decorative; the profile Surface stays the focus target (open the menu → "Notifications"), keeping the focus model intact.

- [ ] **Step 4: Run tests + manual D-pad checklist** — `./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug`. Manual: badge on profile cluster (caps "9+"); live within ~1s while foregrounded / on resume when socket down; profile menu has focusable "Notifications" row → opens inbox; profile switch resets badge.

- [ ] **Step 5: Commit** — message "Add TV notifications realtime starter and unread badge" + Co-Authored-By trailer.

---

### Task T2: TvInboxScreen — focusable notification list

**Files:**
- Create `androidTvApp/.../ui/screens/inbox/TvInboxScreen.kt`
- Create (only if shared card-model helpers weren't placed in commonMain) `androidTvApp/.../ui/screens/inbox/TvNotificationCardModel.kt` + its unit test.

- [ ] **Step 1: Write the failing test** — If the card-model mapper is duplicated TV-side (mobile-only original), add `androidTvApp/src/androidUnitTest/.../inbox/TvNotificationCardModelTest.kt`: episode.available → rich; unknown type → generic fallback; `readAt == null` → unread. If reusing shared helpers (commonTest-covered), manual-check only.

- [ ] **Step 2: Run test to verify it fails** — RED for the dup-helper test if added; else green baseline `./gradlew :androidTvApp:testDebugUnitTest`.

- [ ] **Step 3: Implementation** — Follow the established TV list idiom (READ TvRequestsScreen/TvLibraryDetailScreen): root `Column` with eyebrow + `displaySmall` header at `TvTopMenuLayout.contentTopInset`, a `LazyColumn` of focusable `androidx.tv.material3.Card` rows in a `focusGroup()`, first-row `FocusRequester` from a `LaunchedEffect` on first data, `TvLoadingScreen`/`TvErrorScreen` reuse, `ThumbhashImage` for the poster. First item = a focusable "Mark all read" Card (enabled when any unread). Each row: rich card (poster + series + SxEy + episode title + relativeTime + unread dot) for `episode.available`, generic fallback (no poster) for other/unknown types. OK on a row → optimistic `markRead` + `onOpenItemDetail(deepLinkContentId)` (episode → series detail; else series/library). Cursor pagination: `loadMore(nextCursor)` when the end is shown. Empty + error states.
> READ the real TvInbox dependencies (ThumbhashImage signature, TvLoadingScreen/TvErrorScreen, Spacing/theme tokens, TvTopMenuLayout) and adapt. Reuse the shared card-model mapper if present.

- [ ] **Step 4: Run tests + manual D-pad checklist** — `./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug`. Manual: newest-first list; rich vs fallback cards; unread dots; DOWN/UP traversal incl. Mark-all row; OK marks read + navigates + Back preserves; pagination keeps focus; empty/error+retry.

- [ ] **Step 5: Commit** — message "Add TV notifications inbox screen" (note any card-model dup) + trailer.

---

### Task T3: TV settings "Notifications" preference toggles

**Files:**
- Modify `androidTvApp/.../ui/screens/settings/TvSettingsViewModel.kt`
- Modify `androidTvApp/.../ui/screens/settings/TvSettingsScreen.kt`
- Modify `androidTvApp/.../di/AndroidTvModule.kt` (inject `NotificationsRepository` into `TvSettingsViewModel`)

- [ ] **Step 1: Write the failing test** — Manual-check only; preference PUT logic is shared-tested.

- [ ] **Step 2: Run test to verify it fails** — n.a. Green baseline `./gradlew :androidTvApp:testDebugUnitTest`.

- [ ] **Step 3: Implementation** — Inject `notificationsRepository` into `TvSettingsViewModel`. Add to `UiState`: `notificationsVisible` (default false), `notificationsEnabled`, `notifyFavorites`, `notifyWatchlist`, `notifyContinueWatching`, `notifyNextUp`. In `init`, `loadNotificationPreferences()`: capability-gate (hide section if `capability().inApp` not available OR fetch fails), then load `preferences()` into state. Add optimistic toggle handlers that PUT a partial patch and revert on failure (mirror the VM's existing revert idiom). In `TvSettingsScreen.kt`, add a capability-gated section (between Subtitles and Library Shortcuts) using the existing `SectionHeader` + `SettingsRowToggle` idiom with the five toggles. Never render push toggles. In `AndroidTvModule.kt`, add `notificationsRepository = get()` to the `TvSettingsViewModel` factory.
> READ the real TvSettings toggle idiom (`SettingsRowToggle` is a Card with On/Off text, not a Switch) + the VM's ApiResult handling and adapt.

- [ ] **Step 4: Run tests + manual D-pad checklist** — `./gradlew :androidTvApp:compileDebugKotlinAndroid :androidTvApp:testDebugUnitTest :androidTvApp:assembleDebug`. Manual: section shows when in_app available, absent otherwise; no push toggles; OK flips On/Off optimistically; round-trips on reopen; reverts on offline PUT; per-profile.

- [ ] **Step 5: Commit** — message "Add TV notifications preference toggles in settings" + trailer.
