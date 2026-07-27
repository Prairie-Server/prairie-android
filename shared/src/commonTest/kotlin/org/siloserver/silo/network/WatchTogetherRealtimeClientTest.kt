package org.siloserver.silo.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WatchTogetherRealtimeClientTest {

    @Test
    fun `normal socket EOF is transport termination not protocol room close`() = runTest {
        val connection = FakeConnection().apply { incoming.close() }
        val client = client(FakeConnector(connection))

        val events = mutableListOf<RoomRealtimeEvent>()
        client.connect("room-1", "room-token").collect(events::add)

        assertIs<RoomRealtimeEvent.Opened>(events[0])
        assertIs<RoomRealtimeEvent.TransportTerminated>(events[1])
        assertNull(events[1].let { it as RoomRealtimeEvent.TransportTerminated }.cause)
        assertTrue(events.none { it is RoomRealtimeEvent.Closed })
    }

    @Test
    fun `socket receive failure is typed transport termination not protocol room close`() = runTest {
        val connection = FakeConnection().apply {
            incoming.close(IllegalStateException("socket broke"))
        }
        val client = client(FakeConnector(connection))

        val events = mutableListOf<RoomRealtimeEvent>()
        client.connect("room-1", "room-token").collect(events::add)

        val terminated = assertIs<RoomRealtimeEvent.TransportTerminated>(events.last())
        assertEquals("socket broke", terminated.cause?.message)
        assertTrue(events.none { it is RoomRealtimeEvent.Closed })
    }

    @Test
    fun `handshake failure is typed transport termination not protocol room close`() = runTest {
        val client = client(
            object : WatchTogetherSocketConnector {
                override suspend fun open(url: String): WatchTogetherSocketConnection {
                    throw IllegalStateException("handshake rejected")
                }
            },
        )

        val events = mutableListOf<RoomRealtimeEvent>()
        client.connect("room-1", "room-token").collect(events::add)

        val terminated = assertIs<RoomRealtimeEvent.TransportTerminated>(events.single())
        assertEquals("handshake rejected", terminated.cause?.message)
    }

    @Test
    fun `collector cancellation emits no transport or protocol close`() = runTest {
        val connection = FakeConnection()
        val opened = CompletableDeferred<Unit>()
        val events = mutableListOf<RoomRealtimeEvent>()
        val job = launch {
            client(FakeConnector(connection))
                .connect("room-1", "room-token")
                .collect {
                    events += it
                    if (it is RoomRealtimeEvent.Opened) opened.complete(Unit)
                }
        }
        opened.await()

        job.cancelAndJoin()

        assertEquals(1, events.size)
        assertIs<RoomRealtimeEvent.Opened>(events.single())
    }

    @Test
    fun `sends report delivery only while a writable connection is current`() = runTest {
        val connection = FakeConnection()
        val client = client(FakeConnector(connection))
        assertFalse(client.attachSession("session-before-open"))

        val opened = CompletableDeferred<Unit>()
        val job = launch {
            client.connect("room-1", "room-token").collect {
                if (it is RoomRealtimeEvent.Opened) opened.complete(Unit)
            }
        }
        opened.await()

        assertTrue(client.attachSession("session-1"))
        assertTrue(client.transportRequest("seek", 12.5, false))
        assertTrue(client.stateReport("session-1", 13.5, false))
        assertTrue(client.ready("session-1", 14.5, false))
        assertTrue(client.buffering("session-1", 15.5, true))
        assertTrue(client.ping("2026-07-27T10:00:00Z"))
        assertEquals(
            listOf(
                "attach_session",
                "transport_request",
                "state_report",
                "ready",
                "buffering",
                "ping",
            ),
            connection.sent.map { SiloJson.parseToJsonElement(it).jsonObject.getValue("type").jsonPrimitive.content },
        )

        connection.sendFailure = IllegalStateException("closed")
        assertFalse(client.ping("2026-07-27T10:00:01Z"))
        job.cancelAndJoin()
        assertFalse(client.attachSession("session-after-close"))
    }

    @Test
    fun `old connection finalizer cannot clear a writable replacement`() = runTest {
        val first = FakeConnection()
        val second = FakeConnection()
        val connector = FakeConnector(first, second)
        val client = client(connector)
        val firstOpened = CompletableDeferred<Unit>()
        val secondOpened = CompletableDeferred<Unit>()
        val firstJob = launch {
            client.connect("room-1", "room-token").collect {
                if (it is RoomRealtimeEvent.Opened) firstOpened.complete(Unit)
            }
        }
        firstOpened.await()
        val secondJob = launch {
            client.connect("room-1", "room-token").collect {
                if (it is RoomRealtimeEvent.Opened) secondOpened.complete(Unit)
            }
        }
        secondOpened.await()

        firstJob.cancelAndJoin()
        assertTrue(client.ping("replacement-ping"))
        assertTrue(first.sent.isEmpty())
        assertEquals(1, second.sent.size)

        secondJob.cancelAndJoin()
    }

    @Test
    fun `connection URL encodes room path and excludes access JWT`() = runTest {
        val connection = FakeConnection()
        val connector = FakeConnector(connection)
        val client = client(
            connector = connector,
            accessToken = "ACCESS_SECRET",
            profileId = "profile id",
            profileToken = "profile&secret",
        )
        val opened = CompletableDeferred<Unit>()
        val job = launch {
            client.connect("room/with ?#", "room&secret").collect {
                if (it is RoomRealtimeEvent.Opened) opened.complete(Unit)
            }
        }
        opened.await()

        val url = connector.urls.single()
        assertTrue(url.startsWith("/api/v1/watch-together/rooms/room%2Fwith%20%3F%23/ws?"))
        assertFalse(url.contains("ACCESS_SECRET"))
        assertFalse(url.contains("token=ACCESS_SECRET"))
        assertTrue(url.contains("room_token=room%26secret"))
        assertTrue(url.contains("profile_id=profile%20id"))
        assertTrue(url.contains("profile_token=profile%26secret"))

        job.cancelAndJoin()
    }

    private suspend fun client(
        connector: WatchTogetherSocketConnector,
        accessToken: String = "access",
        profileId: String = "profile",
        profileToken: String = "profile-token",
    ): DefaultWatchTogetherRealtimeClient {
        val tokens = TokenManagerImpl().apply {
            saveTokens(accessToken, "refresh", 3_600)
            setProfileId(profileId)
            setProfileToken(profileToken)
        }
        return DefaultWatchTogetherRealtimeClient(
            tokenManager = tokens,
            socketConnector = connector,
        )
    }

    private class FakeConnector(
        vararg connections: FakeConnection,
    ) : WatchTogetherSocketConnector {
        private val connections = Channel<FakeConnection>(Channel.UNLIMITED).apply {
            connections.forEach { trySend(it) }
        }
        val urls = mutableListOf<String>()

        override suspend fun open(url: String): WatchTogetherSocketConnection {
            urls += url
            return connections.receive()
        }
    }

    private class FakeConnection : WatchTogetherSocketConnection {
        val incoming = Channel<String>(Channel.UNLIMITED)
        val sent = mutableListOf<String>()
        var sendFailure: Throwable? = null

        override suspend fun receiveText(): String? {
            val result = incoming.receiveCatching()
            result.exceptionOrNull()?.let { throw it }
            return result.getOrNull()
        }

        override suspend fun sendText(text: String) {
            sendFailure?.let { throw it }
            sent += text
        }

        override suspend fun close() {
            incoming.cancel(CancellationException("test connection closed"))
        }
    }
}
