package org.siloserver.silo.repository

import org.siloserver.silo.model.notifications.NotificationListResponse
import org.siloserver.silo.model.notifications.NotificationRow
import org.siloserver.silo.model.notifications.UnreadCountResponse
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.TokenManagerImpl
import org.siloserver.silo.network.api.ProfileApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryTest {

    // A no-op HttpClient whose MockEngine returns 200/{} for any request —
    // used to construct concrete API classes without a real server. Tests that
    // exercise ProfileRepository.selectProfile() never actually call ProfileApi,
    // so the engine is never triggered.
    private val noOpClient = HttpClient(MockEngine { _ ->
        respond(content = "{}", status = HttpStatusCode.OK, headers = headersOf("Content-Type", "application/json"))
    })

    private fun row(id: String) = NotificationRow(
        id = id,
        rawType = "episode.available",
        profileId = "profile-a",
        reasonFlags = JsonObject(emptyMap()),
        createdAt = "2026-06-12T10:00:00Z",
        readAt = null,
    )

    /**
     * A [org.siloserver.silo.network.api.NotificationsApi] whose [list] and
     * [unreadCount] return the supplied rows. All other methods throw so that
     * accidental invocations surface immediately.
     */
    private fun seedingNotificationsRepo(rows: List<NotificationRow>): NotificationsRepository {
        val api = object : org.siloserver.silo.network.api.NotificationsApi {
            override suspend fun list(limit: Int, unreadOnly: Boolean, before: String?) =
                ApiResult.Success(NotificationListResponse(notifications = rows))

            override suspend fun unreadCount() =
                ApiResult.Success(UnreadCountResponse(count = rows.count { !it.isRead }))

            override suspend fun sync(since: String?, limit: Int) = error("unused in this test")
            override suspend fun get(id: String) = error("unused in this test")
            override suspend fun markRead(id: String) = error("unused in this test")
            override suspend fun markAllRead() = error("unused in this test")
            override suspend fun getPreferences() = error("unused in this test")
            override suspend fun updatePreferences(
                update: org.siloserver.silo.model.notifications.NotificationPreferencesUpdate,
            ) = error("unused in this test")
            override suspend fun capability() = error("unused in this test")
            override suspend fun wsTicket() = error("unused in this test")
        }
        return NotificationsRepository(api = api)
    }

    @Test
    fun `selectProfile resets notifications state`() = runTest(UnconfinedTestDispatcher()) {
        val notificationsRepo = seedingNotificationsRepo(listOf(row("n1"), row("n2")))

        // Pre-populate state via refresh() so unreadCount and rows are non-zero.
        notificationsRepo.refresh()
        assertEquals(2, notificationsRepo.rows.value.size)
        assertEquals(2, notificationsRepo.unreadCount.value)

        val profileRepo = ProfileRepository(
            profileApi = ProfileApi(noOpClient),
            tokenManager = TokenManagerImpl(),
            serverRegistry = null,
            notificationsRepository = notificationsRepo,
        )

        profileRepo.selectProfile("profile-b")

        // After the profile switch, notifications state must be wiped so stale
        // cross-profile badges and inbox rows do not bleed into the new profile.
        assertEquals(0, notificationsRepo.unreadCount.value)
        assertTrue(notificationsRepo.rows.value.isEmpty())
    }

    @Test
    fun `selectProfile without notificationsRepository does not throw`() =
        runTest(UnconfinedTestDispatcher()) {
            // Verifies the nullable default keeps callers that don't inject
            // NotificationsRepository working without change.
            val profileRepo = ProfileRepository(
                profileApi = ProfileApi(noOpClient),
                tokenManager = TokenManagerImpl(),
                serverRegistry = null,
                notificationsRepository = null,
            )
            profileRepo.selectProfile("profile-b")  // must not throw
        }
}
