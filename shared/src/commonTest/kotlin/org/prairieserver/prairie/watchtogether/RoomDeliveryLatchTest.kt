package org.prairieserver.prairie.watchtogether

import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.model.watchtogether.RoomSnapshot
import org.prairieserver.prairie.model.watchtogether.TransportCommand
import org.prairieserver.prairie.repository.WatchTogetherConnectionState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomDeliveryLatchTest {
    private val open1 = WatchTogetherConnectionState(generation = 7, epoch = 1, writable = true)
    private val open2 = WatchTogetherConnectionState(generation = 7, epoch = 2, writable = true)

    @Test
    fun `attach is delivered once per connection epoch and playback session`() {
        val latch = RoomDeliveryLatch()
        val first = assertNotNull(latch.keyOrNull(open1, "session-a"))

        assertTrue(latch.needsAttach(first))
        latch.recordAttach(first, delivered = true)
        assertFalse(latch.needsAttach(first))
        assertTrue(latch.needsAttach(assertNotNull(latch.keyOrNull(open2, "session-a"))))
        assertTrue(latch.needsAttach(assertNotNull(latch.keyOrNull(open2, "session-b"))))
    }

    @Test
    fun `failed attach and readiness remain retryable`() {
        val latch = RoomDeliveryLatch()
        val key = assertNotNull(latch.keyOrNull(open1, "session-a"))

        latch.recordAttach(key, delivered = false)
        assertTrue(latch.needsAttach(key))
        latch.recordReadiness(key, buffering = true, delivered = false)
        assertTrue(latch.needsReadiness(key, buffering = true))
    }

    @Test
    fun `readiness is keyed by epoch session and buffering value`() {
        val latch = RoomDeliveryLatch()
        val first = assertNotNull(latch.keyOrNull(open1, "session-a"))
        latch.recordReadiness(first, buffering = false, delivered = true)

        assertFalse(latch.needsReadiness(first, buffering = false))
        assertTrue(latch.needsReadiness(first, buffering = true))
        assertTrue(
            latch.needsReadiness(
                assertNotNull(latch.keyOrNull(open2, "session-a")),
                buffering = false,
            ),
        )
    }

    @Test
    fun `non writable connection cannot mint a delivery key`() {
        assertNull(
            RoomDeliveryLatch().keyOrNull(
                WatchTogetherConnectionState(generation = 7, epoch = 1, writable = false),
                "session-a",
            ),
        )
    }

    @Test
    fun `ping cannot start until a playback session has attached`() {
        val latch = RoomDeliveryLatch()
        val key = latch.keyOrNull(open1, null)

        assertFalse(latch.isAttached(key))
    }

    @Test
    fun `delayed command for session A cannot mutate replacement session B`() {
        val command = TransportCommand(
            commandId = "cmd-1",
            sessionId = "session-a",
            selectionRevision = 3,
            executeAt = "2026-07-27T12:00:00Z",
        )
        val room = RoomSnapshot(roomId = "room-1", selectionRevision = 3)

        assertTrue(
            scheduledCommandStillCurrent(
                command = command,
                acceptedPlaybackSessionId = "session-a",
                acceptedRoomId = "room-1",
                acceptedConnection = open1,
                currentPlaybackSessionId = "session-a",
                currentRoom = room,
                currentConnection = open1,
            ),
        )
        assertFalse(
            scheduledCommandStillCurrent(
                command = command,
                acceptedPlaybackSessionId = "session-a",
                acceptedRoomId = "room-1",
                acceptedConnection = open1,
                currentPlaybackSessionId = "session-b",
                currentRoom = room,
                currentConnection = open1,
            ),
        )
        assertFalse(
            scheduledCommandStillCurrent(
                command = command,
                acceptedPlaybackSessionId = "session-a",
                acceptedRoomId = "room-1",
                acceptedConnection = open1,
                currentPlaybackSessionId = "session-a",
                currentRoom = room.copy(selectionRevision = 4),
                currentConnection = open1,
            ),
        )
    }

    @Test
    fun `delayed command for room A cannot mutate room B reusing session and revision`() {
        val command = TransportCommand(
            commandId = "cmd-1",
            sessionId = "session-a",
            selectionRevision = 3,
            executeAt = "2026-07-27T12:00:00Z",
        )
        val replacementRoom = RoomSnapshot(roomId = "room-b", selectionRevision = 3)

        assertFalse(
            scheduledCommandStillCurrent(
                command = command,
                acceptedPlaybackSessionId = "session-a",
                acceptedRoomId = "room-a",
                acceptedConnection = open1,
                currentPlaybackSessionId = "session-a",
                currentRoom = replacementRoom,
                currentConnection = open1.copy(generation = 8),
            ),
        )
        assertFalse(
            scheduledCommandStillCurrent(
                command = command,
                acceptedPlaybackSessionId = "session-a",
                acceptedRoomId = "room-a",
                acceptedConnection = open1,
                currentPlaybackSessionId = "session-a",
                currentRoom = replacementRoom.copy(roomId = "room-a"),
                currentConnection = open2,
            ),
        )
    }

    @Test
    fun `delayed command does not block a newer immediate command`() = runTest {
        val dispatcher = RoomCommandDispatcher(backgroundScope)
        val applied = mutableListOf<String>()

        dispatcher.dispatch {
            delay(1_000)
            applied += "older-delayed"
        }
        dispatcher.dispatch {
            applied += "newer-immediate"
        }
        runCurrent()

        assertTrue(applied == listOf("newer-immediate"))
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(applied == listOf("newer-immediate", "older-delayed"))
    }

    @Test
    fun `finishing one repeated command preserves the other dispatch token`() {
        val pending = RoomPendingExecutions()
        val firstC1 = pending.record(executeAtMs = 1_000)
        val c2 = pending.record(executeAtMs = 10)
        val secondC1 = pending.record(executeAtMs = 1_000)

        pending.finish(firstC1)
        pending.finish(c2)

        assertEquals(1_000, pending.nearestTo(nowMs = 0))
        pending.finish(secondC1)
        assertNull(pending.nearestTo(nowMs = 0))
    }

    @Test
    fun `disposing controller lifetime cancels only its child collectors`() = runTest {
        val lifetime = RoomControllerLifetime(backgroundScope)
        var childFinished = false
        lifetime.scope.launch {
            try {
                awaitCancellation()
            } finally {
                childFinished = true
            }
        }
        runCurrent()

        lifetime.dispose()
        runCurrent()

        assertTrue(childFinished)
    }
}
