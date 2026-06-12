package com.continuum.app.android.ui.navigation

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Maps scanned/opened device-login URLs into the in-app pairing route.
 *
 * TV QR sessions may surface either app-scheme links (`silo://device?...`,
 * `continuum://device?...`) or server HTTPS links (`/device`, `/auth/device`).
 * Android App Links for arbitrary self-hosted server domains are best-effort,
 * but whenever the OS delivers a URI to us this parser keeps routing identical.
 */
internal fun deviceLoginPairRouteOrNull(rawUri: String?): String? {
    val uri = rawUri
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { URI(it) }.getOrNull() }
        ?: return null

    if (!uri.isDeviceLoginUri()) return null

    val params = uri.queryParameters()
    val token = params["token"]?.takeIf { it.isNotBlank() }
    val code = params["code"]?.takeIf { it.isNotBlank() }
    if (token == null && code == null) return null

    return buildPairDeviceRoute(token = token, code = if (token == null) code else null)
}

private fun URI.isDeviceLoginUri(): Boolean {
    val scheme = scheme?.lowercase()
    return when (scheme) {
        "silo", "continuum" -> host.equals("device", ignoreCase = true)
        "http", "https" -> normalizedPath.endsWith("/device")
        else -> false
    }
}

private fun URI.queryParameters(): Map<String, String> =
    rawQuery
        .orEmpty()
        .split("&")
        .filter { it.isNotBlank() }
        .mapNotNull { pair ->
            val idx = pair.indexOf("=")
            if (idx < 0) return@mapNotNull null
            val key = pair.substring(0, idx).urlDecode()
            val value = pair.substring(idx + 1).urlDecode()
            key to value
        }
        .toMap()

private fun String.urlDecode(): String =
    URLDecoder.decode(this, Charsets.UTF_8.name())

private fun buildPairDeviceRoute(token: String?, code: String?): String = buildString {
    append("pair_device")
    val params = listOfNotNull(
        token?.takeIf { it.isNotBlank() }?.let { "token=${it.routeEncode()}" },
        code?.takeIf { it.isNotBlank() }?.let { "code=${it.routeEncode()}" },
    )
    if (params.isNotEmpty()) {
        append("?")
        append(params.joinToString("&"))
    }
}

private fun String.routeEncode(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private val URI.normalizedPath: String
    get() = path.orEmpty().trimEnd('/').lowercase()
