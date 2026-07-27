package org.prairieserver.prairie.update

/**
 * Result of comparing the installed client version against the latest
 * published GitHub release for this app family.
 */
sealed class AppUpdateStatus {
    data object Checking : AppUpdateStatus()

    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
        val changelogUrl: String? = null,
    ) : AppUpdateStatus()

    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseUrl: String?,
        val changelogUrl: String? = releaseUrl,
    ) : AppUpdateStatus()

    data class Unavailable(
        val currentVersion: String,
        val reason: String = "Couldn't check for updates",
        val changelogUrl: String? = null,
    ) : AppUpdateStatus()
}

fun AppUpdateStatus.statusLabel(): String = when (this) {
    is AppUpdateStatus.Checking -> "Checking…"
    is AppUpdateStatus.UpToDate -> "Up to date"
    is AppUpdateStatus.UpdateAvailable -> "Update available"
    is AppUpdateStatus.Unavailable -> reason
}

fun AppUpdateStatus.latestVersionLabel(): String? = when (this) {
    is AppUpdateStatus.Checking -> null
    is AppUpdateStatus.UpToDate -> latestVersion
    is AppUpdateStatus.UpdateAvailable -> latestVersion
    is AppUpdateStatus.Unavailable -> null
}

fun AppUpdateStatus.releaseUrlOrNull(): String? = when (this) {
    is AppUpdateStatus.UpdateAvailable -> releaseUrl
    else -> null
}

fun AppUpdateStatus.changelogUrlOrNull(): String? = when (this) {
    is AppUpdateStatus.Checking -> null
    is AppUpdateStatus.UpToDate -> changelogUrl
    is AppUpdateStatus.UpdateAvailable -> changelogUrl ?: releaseUrl
    is AppUpdateStatus.Unavailable -> changelogUrl
}

/**
 * Resolve a status from the installed marketing version and a latest release
 * tag / display version. Pure so unit tests cover the decision table without
 * hitting the network.
 */
fun resolveAppUpdateStatus(
    currentVersionName: String,
    latestVersionName: String?,
    releaseUrl: String?,
    changelogUrl: String? = releaseUrl,
): AppUpdateStatus {
    val current = AppVersion.parse(currentVersionName)
        ?: return AppUpdateStatus.Unavailable(
            currentVersion = currentVersionName,
            changelogUrl = changelogUrl,
        )
    val latestRaw = latestVersionName?.trim().orEmpty()
    if (latestRaw.isEmpty()) {
        return AppUpdateStatus.Unavailable(
            currentVersion = currentVersionName,
            changelogUrl = changelogUrl,
        )
    }
    val latest = AppVersion.parse(latestRaw)
        ?: return AppUpdateStatus.Unavailable(
            currentVersion = currentVersionName,
            changelogUrl = changelogUrl,
        )
    val latestDisplay = latest.toString()
    val notesUrl = changelogUrl ?: releaseUrl
    return if (latest > current) {
        AppUpdateStatus.UpdateAvailable(
            currentVersion = currentVersionName,
            latestVersion = latestDisplay,
            releaseUrl = releaseUrl,
            changelogUrl = notesUrl,
        )
    } else {
        AppUpdateStatus.UpToDate(
            currentVersion = currentVersionName,
            latestVersion = latestDisplay,
            changelogUrl = notesUrl,
        )
    }
}
