package org.siloserver.silo.common.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.siloserver.silo.network.CleartextOriginConsent
import org.siloserver.silo.network.CleartextOriginNotApprovedException
import org.siloserver.silo.network.DefaultWatchTogetherRealtimeClient
import org.siloserver.silo.network.RoomRealtimeEvent
import org.siloserver.silo.network.SiloJson
import org.siloserver.silo.network.TokenManagerImpl
import org.siloserver.silo.network.canonicalHttpOrigin
import org.siloserver.silo.network.createSiloClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchTogetherRealtimeWebSocketTest {

    @Test
    fun `real handshake protects credentials orders open before snapshot and delivers outbound frames`() = runBlocking {
        val server = MockWebServer()
        val outbound = Channel<String>(Channel.UNLIMITED)
        val serverSocket = CompletableDeferred<WebSocket>()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        serverSocket.complete(webSocket)
                        webSocket.send(
                            """{"type":"snapshot","room":{"room_id":"room/segment ?#","code":"ABCD1234"}}""",
                        )
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        outbound.trySend(text)
                    }
                },
            ),
        )
        server.start()
        val tokens = configuredTokens(server)
        val httpClient = createSiloClient(
            tokenManager = tokens,
            cleartextOriginConsent = approvedConsent(server),
        )
        try {
            val realtime = DefaultWatchTogetherRealtimeClient(httpClient, tokens)
            val events = Channel<RoomRealtimeEvent>(Channel.UNLIMITED)
            val collection = launch {
                realtime.connect("room/segment ?#", "ROOM_SECRET").collect(events::send)
            }

            assertIs<RoomRealtimeEvent.Opened>(withTimeout(5_000) { events.receive() })
            val snapshot = assertIs<RoomRealtimeEvent.SnapshotEvent>(
                withTimeout(5_000) { events.receive() },
            )
            assertEquals("room/segment ?#", snapshot.room.roomId)

            assertTrue(realtime.attachSession("playback-1"))
            assertTrue(realtime.ping("2026-07-27T10:00:00Z"))
            assertEquals(
                listOf("attach_session", "ping"),
                listOf(
                    withTimeout(5_000) { outbound.receive() },
                    withTimeout(5_000) { outbound.receive() },
                ).map { SiloJson.parseToJsonElement(it).jsonObject.getValue("type").jsonPrimitive.content },
            )

            val request = assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals(
                "/api/v1/watch-together/rooms/room%2Fsegment%20%3F%23/ws",
                request.requestUrl?.encodedPath,
            )
            assertNull(request.requestUrl?.queryParameter("token"))
            assertEquals("ROOM_SECRET", request.requestUrl?.queryParameter("room_token"))
            assertEquals("profile-1", request.requestUrl?.queryParameter("profile_id"))
            assertEquals("PROFILE_SECRET", request.requestUrl?.queryParameter("profile_token"))
            assertEquals("Bearer ACCESS_SECRET", request.getHeader("Authorization"))
            assertEquals("profile-1", request.getHeader("X-Profile-Id"))
            assertEquals("PROFILE_SECRET", request.getHeader("X-Profile-Token"))

            assertTrue(serverSocket.await().close(1000, "physical EOF"))
            val terminated = assertIs<RoomRealtimeEvent.TransportTerminated>(
                withTimeout(5_000) { events.receive() },
            )
            assertNull(terminated.cause)
            assertTrue(collection.join().let { collection.isCompleted })
        } finally {
            httpClient.close()
            server.shutdown()
        }
    }

    @Test
    fun `real socket cancellation is silent`() = runBlocking {
        val server = MockWebServer()
        val serverClosed = CompletableDeferred<Unit>()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        serverClosed.complete(Unit)
                    }
                },
            ),
        )
        server.start()
        val tokens = configuredTokens(server)
        val httpClient = createSiloClient(
            tokenManager = tokens,
            cleartextOriginConsent = approvedConsent(server),
        )
        try {
            val realtime = DefaultWatchTogetherRealtimeClient(httpClient, tokens)
            val events = Channel<RoomRealtimeEvent>(Channel.UNLIMITED)
            val collection = launch {
                realtime.connect("room-1", "ROOM_SECRET").collect(events::send)
            }
            assertIs<RoomRealtimeEvent.Opened>(withTimeout(5_000) { events.receive() })

            collection.cancelAndJoin()
            withTimeout(5_000) { serverClosed.await() }

            assertNull(events.tryReceive().getOrNull())
            assertFalse(realtime.ping("after-cancel"))
        } finally {
            httpClient.close()
            server.shutdown()
        }
    }

    @Test
    fun `unapproved ws origin fails before credentials reach OkHttp`() = runBlocking {
        val server = MockWebServer()
        server.start()
        val tokens = configuredTokens(server)
        val httpClient = createSiloClient(
            tokenManager = tokens,
            cleartextOriginConsent = object : CleartextOriginConsent {
                override suspend fun isApproved(origin: String): Boolean = false
            },
        )
        try {
            val events = mutableListOf<RoomRealtimeEvent>()
            DefaultWatchTogetherRealtimeClient(httpClient, tokens)
                .connect("room-1", "ROOM_SECRET")
                .collect(events::add)

            val terminated = assertIs<RoomRealtimeEvent.TransportTerminated>(events.single())
            assertIs<CleartextOriginNotApprovedException>(terminated.cause)
            assertEquals(0, server.requestCount)
        } finally {
            httpClient.close()
            server.shutdown()
        }
    }

    private suspend fun configuredTokens(server: MockWebServer): TokenManagerImpl =
        TokenManagerImpl().apply {
            setServerUrl(server.url("/").toString())
            saveTokens("ACCESS_SECRET", "refresh", 3_600)
            setProfileId("profile-1")
            setProfileToken("PROFILE_SECRET")
        }

    private fun approvedConsent(server: MockWebServer): CleartextOriginConsent {
        val approvedOrigin = canonicalHttpOrigin(server.url("/").toString())
        return object : CleartextOriginConsent {
            override suspend fun isApproved(origin: String): Boolean =
                origin == approvedOrigin
        }
    }
}
