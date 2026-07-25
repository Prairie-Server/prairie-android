package org.prairieserver.prairie.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.prairieserver.prairie.model.notifications.WsFrameEnvelope
import org.prairieserver.prairie.model.notifications.WsSubscribe
import org.prairieserver.prairie.network.api.NotificationsApi

/** `user_state.changed` payload (server: user_state_events.go). */
@Serializable
data class UserStateChange(
    @SerialName("profile_id") val profileId: String,
    @SerialName("content_id") val contentId: String? = null,
    @SerialName("series_id") val seriesId: String? = null,
    /** progress | favorite | watchlist | history | watched | home_dismissal */
    val change: String,
)

sealed class HomeRealtimeEvent {
    /**
     * The `subscribed` frame arrived. Snapshots are null for user_state and
     * catalog, so this is the reconnect catch-up point: trigger one REST
     * reconcile for anything missed while disconnected.
     */
    object Connected : HomeRealtimeEvent()

    /** `user_state.changed` — account-scoped; filter by active profile. */
    data class UserState(val change: UserStateChange) : HomeRealtimeEvent()

    /** Any `catalog` channel event — library surface changed, refetch Home. */
    object CatalogChanged : HomeRealtimeEvent()

    /** The socket closed (or failed to connect). The coordinator reconnects. */
    data class Closed(val reason: String? = null) : HomeRealtimeEvent()
}

/**
 * Live-home accelerator over the events websocket (Apple realtime-updates
 * spec, Layer 2). One [connect] = one connection: mint a single-use ticket via
 * [NotificationsApi.wsTicket] and hand it to the upgrade as `?ticket=` — the
 * same handshake the notifications socket uses. The previous "plain
 * authenticated" upgrade (bearer only, no ticket) never connected in the
 * field: every attempt died as a 4xx handshake rejection, silently costing a
 * reconnect every backoff interval. Subscribe both channels, then map frames
 * through [decodeHomeRealtimeFrame]. REST stays the source of truth; events
 * only trigger refetches.
 */
interface HomeRealtimeClient {
    fun connect(): Flow<HomeRealtimeEvent>
}

class DefaultHomeRealtimeClient(
    private val client: HttpClient,
    private val notificationsApi: NotificationsApi,
    private val json: Json = PrairieJson,
) : HomeRealtimeClient {

    override fun connect(): Flow<HomeRealtimeEvent> = callbackFlow {
        val ticket = when (val r = notificationsApi.wsTicket()) {
            is ApiResult.Success -> r.data.ticket
            is ApiResult.Error -> {
                trySend(HomeRealtimeEvent.Closed("ticket_error_${r.code}"))
                close()
                return@callbackFlow
            }
            is ApiResult.NetworkError -> {
                trySend(HomeRealtimeEvent.Closed("ticket_network_error"))
                close()
                return@callbackFlow
            }
        }
        try {
            client.webSocket(urlString = "/api/v1/events/ws?ticket=$ticket") {
                // Server sends `hello` first and closes if no subscribe lands
                // within 5s; subscribing immediately satisfies that without
                // parsing the hello.
                send(
                    Frame.Text(
                        json.encodeToString(
                            WsSubscribe.serializer(),
                            WsSubscribe(channels = listOf(CHANNEL_USER_STATE, CHANNEL_CATALOG)),
                        ),
                    ),
                )
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    decodeHomeRealtimeFrame(json, frame.readText())?.let { trySend(it) }
                }
            }
            trySend(HomeRealtimeEvent.Closed())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            trySend(HomeRealtimeEvent.Closed(e.message))
        } finally {
            close()
        }

        awaitClose { /* socket closes when the flow collector is cancelled */ }
    }

    companion object {
        const val CHANNEL_USER_STATE = "user_state"
        const val CHANNEL_CATALOG = "catalog"
    }
}

/**
 * Pure decode of one server frame into a [HomeRealtimeEvent], or null for
 * frames we don't surface (hello, foreign channels, null snapshots, unknown
 * events, malformed JSON). Never throws.
 */
fun decodeHomeRealtimeFrame(json: Json, raw: String): HomeRealtimeEvent? {
    val envelope = try {
        json.decodeFromString(WsFrameEnvelope.serializer(), raw)
    } catch (_: Exception) {
        return null
    }

    return when (envelope.type) {
        "subscribed" -> HomeRealtimeEvent.Connected
        "event" -> when (envelope.channel) {
            DefaultHomeRealtimeClient.CHANNEL_USER_STATE -> {
                if (envelope.event != "user_state.changed") return null
                val obj = envelope.data as? JsonObject ?: return null
                val change = try {
                    json.decodeFromJsonElement(UserStateChange.serializer(), obj)
                } catch (_: Exception) {
                    return null
                }
                HomeRealtimeEvent.UserState(change)
            }
            DefaultHomeRealtimeClient.CHANNEL_CATALOG -> HomeRealtimeEvent.CatalogChanged
            else -> null
        }
        else -> null // hello, snapshot (always null for these channels), error
    }
}

/**
 * Should this event trigger a Home refetch? user_state events are
 * account-scoped (all profiles of the account), so foreign-profile changes
 * are dropped; an unknown active profile refreshes rather than risk staleness.
 */
fun homeRefreshTrigger(event: HomeRealtimeEvent, activeProfileId: String?): Boolean = when (event) {
    is HomeRealtimeEvent.Connected -> true
    is HomeRealtimeEvent.CatalogChanged -> true
    is HomeRealtimeEvent.UserState ->
        activeProfileId == null || event.change.profileId == activeProfileId
    is HomeRealtimeEvent.Closed -> false
}
