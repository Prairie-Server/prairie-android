package org.prairieserver.prairie.update

/**
 * Marketing semver used for client update checks.
 *
 * Tags may look like `v1.4.0`, `1.4.0+2`, or `v1.4.0-rc.1`. Only the
 * `major.minor.patch` core is compared; prerelease tags sort below the
 * matching release, and a missing/unparseable version is treated as older.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: Boolean = false,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val core = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (core != 0) return core
        return when {
            prerelease == other.prerelease -> 0
            prerelease -> -1
            else -> 1
        }
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(raw: String?): AppVersion? {
            if (raw.isNullOrBlank()) return null
            var text = raw.trim()
            if (text.startsWith("v", ignoreCase = true)) {
                text = text.substring(1)
            }
            // Drop build metadata (`+2`) before detecting prerelease.
            val withoutBuild = text.substringBefore('+')
            val prerelease = withoutBuild.contains('-')
            val core = withoutBuild.substringBefore('-')
            val parts = core.split('.')
            if (parts.size < 2) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            return AppVersion(major, minor, patch, prerelease)
        }
    }
}
