package org.prairieserver.prairie.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.network.PrairieJson

class AppUpdateCheckerTest {
    @Test
    fun check_reports_update_available_from_github_latest() = runTest {
        val client = mockClient(
            status = HttpStatusCode.OK,
            body = """{"tag_name":"v1.4.0","html_url":"https://github.com/Prairie-Server/prairie-android/releases/tag/v1.4.0"}""",
        )
        val status = AppUpdateChecker(client).check("0.3.11")
        val available = assertIs<AppUpdateStatus.UpdateAvailable>(status)
        assertEquals("1.4.0", available.latestVersion)
        assertEquals(
            "https://github.com/Prairie-Server/prairie-android/releases/tag/v1.4.0",
            available.releaseUrl,
        )
    }

    @Test
    fun check_treats_404_as_up_to_date() = runTest {
        val client = mockClient(status = HttpStatusCode.NotFound, body = """{"message":"Not Found"}""")
        val status = AppUpdateChecker(client).check("0.3.11")
        val upToDate = assertIs<AppUpdateStatus.UpToDate>(status)
        assertEquals("0.3.11", upToDate.latestVersion)
        assertEquals(AppUpdateChecker.DEFAULT_CHANGELOG_URL, upToDate.changelogUrl)
    }

    @Test
    fun check_maps_network_failure_to_unavailable() = runTest {
        val client = HttpClient(MockEngine { throw IllegalStateException("offline") }) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        val status = AppUpdateChecker(client).check("0.3.11")
        val unavailable = assertIs<AppUpdateStatus.Unavailable>(status)
        assertEquals(AppUpdateChecker.DEFAULT_CHANGELOG_URL, unavailable.changelogUrl)
    }

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient =
        HttpClient(
            MockEngine { _ ->
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
}
