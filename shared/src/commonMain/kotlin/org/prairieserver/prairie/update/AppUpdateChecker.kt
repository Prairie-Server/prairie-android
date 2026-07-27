package org.prairieserver.prairie.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.prairieserver.prairie.network.skipPrairieAuth

/**
 * Checks GitHub Releases for a newer Prairie Android build.
 *
 * Uses an absolute GitHub URL with [skipPrairieAuth] so the active Prairie
 * server credentials never leave the app, and so the check still works when
 * the user is between servers.
 */
class AppUpdateChecker(
    private val client: HttpClient,
    private val releasesLatestUrl: String = DEFAULT_RELEASES_LATEST_URL,
) {
    suspend fun check(currentVersionName: String): AppUpdateStatus {
        return try {
            val response = client.get(releasesLatestUrl) {
                skipPrairieAuth()
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, "Prairie-Android")
                header("X-GitHub-Api-Version", "2022-11-28")
                timeout {
                    connectTimeoutMillis = TIMEOUT_MS
                    requestTimeoutMillis = TIMEOUT_MS
                    socketTimeoutMillis = TIMEOUT_MS
                }
            }
            // No published releases yet → treat as up to date rather than an error.
            if (response.status.value == 404) {
                return AppUpdateStatus.UpToDate(
                    currentVersion = currentVersionName,
                    latestVersion = currentVersionName,
                    changelogUrl = DEFAULT_CHANGELOG_URL,
                )
            }
            if (!response.status.isSuccess()) {
                return AppUpdateStatus.Unavailable(
                    currentVersion = currentVersionName,
                    changelogUrl = DEFAULT_CHANGELOG_URL,
                )
            }
            val release = response.body<GitHubLatestRelease>()
            val releaseUrl = release.htmlUrl
            resolveAppUpdateStatus(
                currentVersionName = currentVersionName,
                latestVersionName = release.tagName ?: release.name,
                releaseUrl = releaseUrl,
                changelogUrl = releaseUrl ?: DEFAULT_CHANGELOG_URL,
            )
        } catch (_: Exception) {
            AppUpdateStatus.Unavailable(
                currentVersion = currentVersionName,
                changelogUrl = DEFAULT_CHANGELOG_URL,
            )
        }
    }

    @Serializable
    private data class GitHubLatestRelease(
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
    )

    companion object {
        const val DEFAULT_RELEASES_LATEST_URL =
            "https://api.github.com/repos/Prairie-Server/prairie-android/releases/latest"
        const val DEFAULT_CHANGELOG_URL =
            "https://github.com/Prairie-Server/prairie-android/releases"
        private const val TIMEOUT_MS = 8_000L
    }
}
