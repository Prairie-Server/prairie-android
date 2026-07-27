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
    ) : AppUpdateStatus()

    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseUrl: String?,
    ) : AppUpdateStatus()

    data class Unavailable(
        val currentVersion: String,
        val reason: String = "Couldn't check for updates",
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

/**
 * Resolve a status from the installed marketing version and a latest release
 * tag / display version. Pure so unit tests cover the decision table without
 * hitting the network.
 */
fun resolveAppUpdateStatus(
    currentVersionName: String,
    latestVersionName: String?,
    releaseUrl: String?,
): AppUpdateStatus {
    val current = AppVersion.parse(currentVersionName)
        ?: return AppUpdateStatus.Unavailable(currentVersionName)
    val latestRaw = latestVersionName?.trim().orEmpty()
    if (latestRaw.isEmpty()) {
        return AppUpdateStatus.Unavailable(currentVersionName)
    }
    val latest = AppVersion.parse(latestRaw)
        ?: return AppUpdateStatus.Unavailable(currentVersionName)
    val latestDisplay = latest.toString()
    return if (latest > current) {
        AppUpdateStatus.UpdateAvailable(
            currentVersion = currentVersionName,
            latestVersion = latestDisplay,
            releaseUrl = releaseUrl,
        )
    } else {
        AppUpdateStatus.UpToDate(
            currentVersion = currentVersionName,
            latestVersion = latestDisplay,
        )
    }
}
