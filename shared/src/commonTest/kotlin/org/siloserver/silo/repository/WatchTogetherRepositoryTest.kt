package org.siloserver.silo.repository

import org.siloserver.silo.model.watchtogether.AddSuggestionRequest
import org.siloserver.silo.model.watchtogether.CreateRoomRequest
import org.siloserver.silo.model.watchtogether.JoinRoomRequest
import org.siloserver.silo.model.watchtogether.PromoteSuggestionRequest
import org.siloserver.silo.model.watchtogether.RoomResponse
import org.siloserver.silo.model.watchtogether.RoomSnapshot
import org.siloserver.silo.model.watchtogether.SetSelectionRequest
import org.siloserver.silo.model.watchtogether.Suggestion
import org.siloserver.silo.model.watchtogether.SuggestionsResponse
import org.siloserver.silo.model.watchtogether.UpdatePolicyRequest
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.AuthScopeSnapshot
import org.siloserver.silo.network.RoomRealtimeEvent
import org.siloserver.silo.network.WatchTogetherRealtimeClient
import org.siloserver.silo.network.api.WatchTogetherApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
        var lastRoomId: String? = null
        var lastSelection: SetSelectionRequest? = null
        var lastAuthScope: AuthScopeSnapshot? = null
        var createResult: CompletableDeferred<ApiResult<RoomResponse>>? = null
        var selectionResult: CompletableDeferred<ApiResult<RoomResponse>>? = null
        override suspend fun createRoom(request: CreateRoomRequest, scope: AuthScopeSnapshot?): ApiResult<RoomResponse> {
            lastAuthScope = scope
            return createResult?.await() ?: createResponse
        }
        override suspend fun joinRoom(request: JoinRoomRequest, scope: AuthScopeSnapshot?) =
            createResponse.also { lastAuthScope = scope }
        override suspend fun getRoom(roomId: String, roomToken: String, scope: AuthScopeSnapshot?) =
            createResponse.also { lastRoomToken = roomToken; lastAuthScope = scope }
        override suspend fun setSelection(
            roomId: String,
            roomToken: String,
            request: SetSelectionRequest,
            scope: AuthScopeSnapshot?,
        ): ApiResult<RoomResponse> {
            lastRoomId = roomId
            lastRoomToken = roomToken
            lastSelection = request
            lastAuthScope = scope
            return selectionResult?.await() ?: createResponse
        }
        override suspend fun updatePolicy(
            roomId: String,
            roomToken: String,
            request: UpdatePolicyRequest,
            scope: AuthScopeSnapshot?,
        ) = createResponse.also { lastRoomToken = roomToken; lastAuthScope = scope }
        override suspend fun closeRoom(
            roomId: String,
            roomToken: String,
            scope: AuthScopeSnapshot?,
        ): ApiResult<Unit> {
            lastRoomToken = roomToken
            lastAuthScope = scope
            return ApiResult.Success(Unit)
        }
        override suspend fun listSuggestions(roomId: String, roomToken: String, scope: AuthScopeSnapshot?) =
            ApiResult.Success(SuggestionsResponse()).also { lastRoomToken = roomToken; lastAuthScope = scope }
        override suspend fun addSuggestion(
            roomId: String,
            roomToken: String,
            request: AddSuggestionRequest,
            scope: AuthScopeSnapshot?,
        ) = ApiResult.Success(SuggestionsResponse()).also { lastRoomToken = roomToken; lastAuthScope = scope }
        override suspend fun deleteSuggestion(
            roomId: String,
            roomToken: String,
            suggestionId: String,
            scope: AuthScopeSnapshot?,
        ) = ApiResult.Success(SuggestionsResponse()).also { lastRoomToken = roomToken; lastAuthScope = scope }
        override suspend fun vote(
            roomId: String,
            roomToken: String,
            suggestionId: String,
            scope: AuthScopeSnapshot?,
        ) = ApiResult.Success(SuggestionsResponse()).also { lastRoomToken = roomToken; lastAuthScope = scope }
        override suspend fun unvote(
            roomId: String,
            roomToken: String,
            suggestionId: String,
            scope: AuthScopeSnapshot?,
        ) = ApiResult.Success(SuggestionsResponse()).also { lastRoomToken = roomToken; lastAuthScope = scope }
        override suspend fun promoteSuggestion(
            roomId: String,
            roomToken: String,
            request: PromoteSuggestionRequest,
            scope: AuthScopeSnapshot?,
        ) = createResponse.also { lastRoomToken = roomToken; lastAuthScope = scope }
    }

    private class FakeRealtime(
        val events: MutableSharedFlow<RoomRealtimeEvent> = MutableSharedFlow(replay = 0, extraBufferCapacity = 64),
    ) : WatchTogetherRealtimeClient {
        var connectCount = 0
        /** When true, the returned flow throws immediately on collection. */
        var failConnect = false
        var terminateImmediately = false
        var attachCount = 0
        var connectBehavior: ((Int) -> Flow<RoomRealtimeEvent>)? = null
        /**
         * When non-null, each connect attempt emits this one event and then throws.
         * Models a flapping server: healthy traffic is observed but the connection
         * always drops — the classic scenario that could bypass the failure cap if
         * failures were reset per-event rather than per-clean-completion.
         */
        var flappingEvent: RoomRealtimeEvent? = null
        override fun connect(roomId: String, roomToken: String): Flow<RoomRealtimeEvent> {
            connectCount++
            connectedRoomIds += roomId
            connectedRoomTokens += roomToken
            connectBehavior?.let { return it(connectCount) }
            if (failConnect) return flow { throw IllegalStateException("boom") }
            if (terminateImmediately) return flow {
                emit(RoomRealtimeEvent.Opened)
                emit(RoomRealtimeEvent.TransportTerminated())
            }
            val flapEvent = flappingEvent
            if (flapEvent != null) return flow {
                emit(flapEvent)
                throw IllegalStateException("connection dropped after event")
            }
            return events.asSharedFlow()
        }
        val connectedRoomIds = mutableListOf<String>()
        val connectedRoomTokens = mutableListOf<String>()
        override suspend fun attachSession(sessionId: String): Boolean {
            attachCount++
            return true
        }
        override suspend fun transportRequest(action: String, positionSeconds: Double?, isPaused: Boolean) = true
        override suspend fun stateReport(sessionId: String, positionSeconds: Double, isPaused: Boolean) = true
        override suspend fun ready(sessionId: String, positionSeconds: Double, isPaused: Boolean) = true
        override suspend fun buffering(sessionId: String, positionSeconds: Double, isPaused: Boolean) = true
        override suspend fun ping(clientSentAt: String) = true
    }

    private val scopeA = AuthScopeSnapshot(
        serverId = "server-a",
        profileId = "profile-a",
        serverUrl = "https://a.example",
        profileToken = "profile-token-a",
        identityGeneration = 1L,
    )

    private fun repo(
        api: FakeApi = FakeApi(),
        realtime: FakeRealtime = FakeRealtime(),
        authScopeProvider: suspend () -> AuthScopeSnapshot? = { scopeA },
    ) = WatchTogetherRepository(
        api = api,
        realtimeFactory = { realtime },
        authScopeProvider = authScopeProvider,
    )

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

    @Test
    fun `room binding retains the auth scope captured when it was created`() = runTest {
        val api = FakeApi()
        var currentScope = scopeA
        val r = repo(api = api, authScopeProvider = { currentScope })
        r.createRoom(CreateRoomRequest())
        currentScope = scopeA.copy(
            serverId = "server-b",
            serverUrl = "https://b.example",
            identityGeneration = 2L,
        )

        r.setSelection(SetSelectionRequest(contentId = "tt-9"))

        assertEquals(scopeA, api.lastAuthScope)
    }

    @Test
    fun `create response cannot mint a room after the auth identity changes`() = runTest {
        val api = FakeApi()
        val pending = CompletableDeferred<ApiResult<RoomResponse>>()
        api.createResult = pending
        var currentScope = scopeA
        val r = repo(api = api, authScopeProvider = { currentScope })
        val create = launch { r.createRoom(CreateRoomRequest()) }
        runCurrent()
        currentScope = scopeA.copy(identityGeneration = 2L)

        pending.complete(api.createResponse)
        create.join()

        assertNull(r.roomSnapshot.value)
    }

    @Test
    fun `blank room token fails closed without replacing the active room`() = runTest {
        val api = FakeApi()
        val r = repo(api = api)
        r.createRoom(CreateRoomRequest())
        api.createResponse = ApiResult.Success(
            RoomResponse(RoomSnapshot(roomId = "room-2", code = "EFGH5678"), ""),
        )

        val result = r.createRoom(CreateRoomRequest())

        assertIs<ApiResult.Error>(result)
        assertEquals("room-1", r.roomSnapshot.value?.roomId)
        r.setSelection(SetSelectionRequest(contentId = "still-a"))
        assertEquals("room-1", api.lastRoomId)
        assertEquals("jwt-room", api.lastRoomToken)
    }

    @Test
    fun `mismatched room response fails closed without replacing the active room`() = runTest {
        val api = FakeApi()
        val r = repo(api = api)
        r.createRoom(CreateRoomRequest())
        api.createResponse = ApiResult.Success(
            RoomResponse(RoomSnapshot(roomId = "", code = "EFGH5678"), "jwt-room-2"),
        )

        val result = r.createRoom(CreateRoomRequest())

        assertIs<ApiResult.Error>(result)
        assertEquals("room-1", r.roomSnapshot.value?.roomId)
    }

    @Test
    fun `room scoped response for another room is rejected`() = runTest {
        val api = FakeApi()
        val r = repo(api = api)
        r.createRoom(CreateRoomRequest())
        api.createResponse = ApiResult.Success(
            RoomResponse(RoomSnapshot(roomId = "room-2", code = "EFGH5678"), "jwt-room-2"),
        )

        val result = r.setSelection(SetSelectionRequest(contentId = "wrong-room"))

        assertIs<ApiResult.Error>(result)
        assertEquals("room-1", r.roomSnapshot.value?.roomId)
    }

    @Test
    fun `create fails closed when there is no authenticated scope`() = runTest {
        val r = repo(authScopeProvider = { null })

        val result = r.createRoom(CreateRoomRequest())

        assertIs<ApiResult.Error>(result)
        assertNull(r.roomSnapshot.value)
    }

    @Test
    fun `connect rejects a room id that does not match the active binding`() = runTest {
        val realtime = FakeRealtime()
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())

        val job = launch { r.connect("room-2") }
        runCurrent()

        assertEquals(0, realtime.connectCount)
        job.cancel()
    }

    @Test
    fun `late room A REST completion cannot overwrite replacement room B`() = runTest {
        val api = FakeApi()
        val r = repo(api = api)
        r.createRoom(CreateRoomRequest())
        val pending = CompletableDeferred<ApiResult<RoomResponse>>()
        api.selectionResult = pending
        val stale = launch { r.setSelection(SetSelectionRequest(contentId = "late-a")) }
        runCurrent()

        api.selectionResult = null
        api.createResponse = ApiResult.Success(
            RoomResponse(RoomSnapshot(roomId = "room-2", code = "EFGH5678"), "jwt-room-2"),
        )
        r.createRoom(CreateRoomRequest())
        pending.complete(
            ApiResult.Success(
                RoomResponse(RoomSnapshot(roomId = "room-1", selectionRevision = 99), "jwt-room"),
            ),
        )
        stale.join()

        assertEquals("room-2", r.roomSnapshot.value?.roomId)
    }

    @Test
    fun `late room A socket event cannot overwrite replacement room B`() = runTest {
        val api = FakeApi()
        val realtime = FakeRealtime()
        val r = repo(api = api, realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val oldConnection = launch { r.connect("room-1") }
        runCurrent()

        api.createResponse = ApiResult.Success(
            RoomResponse(RoomSnapshot(roomId = "room-2", code = "EFGH5678"), "jwt-room-2"),
        )
        r.createRoom(CreateRoomRequest())
        realtime.events.emit(RoomRealtimeEvent.SnapshotEvent(snapshot(roomId = "room-1", revision = 99)))
        advanceUntilIdle()

        assertEquals("room-2", r.roomSnapshot.value?.roomId)
        assertTrue(oldConnection.isCompleted)
        assertTrue(!r.attachSession("session-b"))
        assertEquals(0, realtime.attachCount)
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
        val result = r.setSelection(SetSelectionRequest(contentId = "x"))
        assertIs<ApiResult.Error>(result)
        assertNull(api.lastRoomToken) // fail fast: no request is sent with an empty token
        job.cancel()
    }

    // ---- closed/error surface ------------------------------------------------

    @Test
    fun `room_closed populates roomClosedReason and reset clears it`() = runTest {
        val realtime = FakeRealtime()
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()
        assertNull(r.roomClosedReason.value)

        realtime.events.emit(RoomRealtimeEvent.Closed("host_left"))
        advanceUntilIdle()
        assertEquals("host_left", r.roomClosedReason.value)

        r.reset()
        assertNull(r.roomClosedReason.value)
        job.cancel()
    }

    @Test
    fun `error frame surfaces on errors and does not populate roomClosedReason`() = runTest {
        val realtime = FakeRealtime()
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()
        assertNull(r.roomClosedReason.value)

        // Collect the transient errors stream; it's a hot SharedFlow so we must
        // be subscribed before the event is emitted.
        val seen = mutableListOf<String>()
        val errorJob = launch { r.errors.collect { seen.add(it) } }
        advanceUntilIdle()

        // A transient server `error` frame (e.g. a rejected transport_request)
        // must NOT eject the user: roomClosedReason stays null and the message
        // surfaces on the errors stream instead.
        realtime.events.emit(RoomRealtimeEvent.Error(code = "rejected", message = "transport rejected"))
        advanceUntilIdle()

        assertNull(r.roomClosedReason.value)
        assertEquals(listOf("transport rejected"), seen)

        errorJob.cancel()
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

        // A terminal binding is tombstoned. Reusing the old room id cannot
        // create a fresh connection without a new successful create/join.
        r.connect("room-1")
        assertEquals(1, realtime.connectCount)
    }

    @Test
    fun `reconnect loop stops after room_closed with no reason`() = runTest {
        val realtime = FakeRealtime()
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()
        assertEquals(1, realtime.connectCount)

        // Server-initiated close with no reason: repository must NOT reconnect.
        realtime.events.emit(RoomRealtimeEvent.Closed(null))
        advanceUntilIdle()
        assertEquals(1, realtime.connectCount)
        assertTrue(job.isCompleted || job.isCancelled)
    }

    // ---- reconnect gives up after max consecutive failures ----------------------

    @Test
    fun `reconnect loop gives up after the max consecutive failures`() = runTest {
        val realtime = FakeRealtime().apply { failConnect = true }
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()
        // The loop must have stopped on its own (job completed, not still running).
        assertTrue(job.isCompleted || job.isCancelled)
        // And the closed reason must signal connection_lost.
        assertEquals("connection_lost", r.roomClosedReason.value)
        // Verify the exact number of attempts so an off-by-one or wrong-constant
        // regression is caught immediately. Expected: MAX_RECONNECT_ATTEMPTS = 6.
        assertEquals(WatchTogetherRepository.MAX_RECONNECT_ATTEMPTS, realtime.connectCount)
        // Stale room state must not be observable after giving up.
        assertNull(r.roomSnapshot.value)
    }

    @Test
    fun `physical transport termination reconnects and counts toward the cap`() = runTest {
        val realtime = FakeRealtime().apply { terminateImmediately = true }
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())

        val job = launch { r.connect("room-1") }
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(WatchTogetherRepository.MAX_RECONNECT_ATTEMPTS, realtime.connectCount)
        assertEquals("connection_lost", r.roomClosedReason.value)
        assertTrue(!r.connectionState.value.writable)
        assertEquals(
            WatchTogetherRepository.MAX_RECONNECT_ATTEMPTS.toLong(),
            r.connectionState.value.epoch,
        )
    }

    @Test
    fun `snapshot plus thirty stable seconds resets the reconnect failure window`() = runTest {
        var nowMs = 0L
        val realtime = FakeRealtime().apply {
            connectBehavior = { attempt ->
                when {
                    attempt <= 5 -> flow { throw IllegalStateException("early failure") }
                    attempt == 6 -> flow {
                        emit(RoomRealtimeEvent.Opened)
                        nowMs = WatchTogetherRepository.STABLE_CONNECTION_MS
                        emit(RoomRealtimeEvent.SnapshotEvent(snapshot()))
                        emit(RoomRealtimeEvent.TransportTerminated())
                    }
                    attempt <= 10 -> flow { throw IllegalStateException("later failure") }
                    else -> events.asSharedFlow()
                }
            }
        }
        val r = WatchTogetherRepository(
            api = FakeApi(),
            realtimeFactory = { realtime },
            monotonicNowMs = { nowMs },
            authScopeProvider = { scopeA },
        )
        r.createRoom(CreateRoomRequest())

        val job = launch { r.connect("room-1") }
        advanceUntilIdle()

        assertEquals(11, realtime.connectCount)
        assertTrue(job.isActive)
        assertNull(r.roomClosedReason.value)
        job.cancel()
    }

    // ---- flapping server hits the cap (regression for Issue 1) -----------------

    @Test
    fun `flapping server that emits then drops still hits the reconnect cap`() = runTest {
        // Each connect attempt emits one healthy SnapshotEvent, then throws.
        // Under the buggy code (failures reset per healthy event), this would loop
        // forever because failures never accumulates to MAX_RECONNECT_ATTEMPTS.
        // Under the correct code (failures reset only on clean completion), each
        // throwing attempt still increments failures and the loop terminates.
        val realtime = FakeRealtime().apply {
            flappingEvent = RoomRealtimeEvent.SnapshotEvent(
                RoomSnapshot(roomId = "room-1", code = "ABCD1234"),
            )
        }
        val r = repo(realtime = realtime)
        r.createRoom(CreateRoomRequest())
        val job = launch { r.connect("room-1") }
        advanceUntilIdle()

        assertTrue(job.isCompleted || job.isCancelled)
        assertEquals("connection_lost", r.roomClosedReason.value)
        // Must have stopped after exactly MAX_RECONNECT_ATTEMPTS attempts.
        assertEquals(WatchTogetherRepository.MAX_RECONNECT_ATTEMPTS, realtime.connectCount)
        // Stale room state must not be observable after the cap is hit.
        assertNull(r.roomSnapshot.value)
    }
}
