package org.prairieserver.prairie.android.ui.screens.admin

/**
 * Pure helpers for the admin Logs screen: normalising the filter inputs into a
 * stable query map, ranking app-log severity for badge colouring, and rendering
 * the audit one-liner. Kept side-effect free so they can be unit tested without
 * the Android/Compose toolchain.
 *
 * NOTE: the landed [org.prairieserver.prairie.repository.AdminRepository.getAppLogs] /
 * [org.prairieserver.prairie.repository.AdminRepository.getAuditLogs] take individual
 * named parameters rather than a filter map. [buildLogQuery] still produces the
 * normalised string map (the shape the spec's tests assert on); the ViewModel
 * reads `level`/`q`/`component`/`limit` back out of it when calling the repo, so
 * the trim/clamp/sentinel logic lives in exactly one tested place.
 */

internal const val LOG_LEVEL_ALL = "All"
internal val LOG_LEVELS = listOf(LOG_LEVEL_ALL, "debug", "info", "warn", "error")
internal const val LOG_PAGE_LIMIT = 100
private const val LOG_SERVER_MAX = 200

internal fun buildLogQuery(
    level: String?,
    query: String?,
    component: String?,
    limit: Int = LOG_PAGE_LIMIT,
): Map<String, String> = buildMap {
    level?.trim()?.takeIf { it.isNotEmpty() && it != LOG_LEVEL_ALL }?.let { put("level", it) }
    query?.trim()?.takeIf { it.isNotEmpty() }?.let { put("q", it) }
    component?.trim()?.takeIf { it.isNotEmpty() }?.let { put("component", it) }
    put("limit", limit.coerceIn(1, LOG_SERVER_MAX).toString())
}

internal fun logLevelRank(level: String): Int = when (level.lowercase()) {
    "error", "fatal" -> 4
    "warn", "warning" -> 3
    "info" -> 2
    "debug" -> 1
    else -> 0
}

internal fun auditSummaryLine(method: String, path: String, statusCode: Int): String =
    "${method.uppercase()} $path → $statusCode"
