package org.siloserver.silo.common.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Keeps HLS manifest/segment loads alive across short server restarts.
 *
 * The server can reconstruct transcode sessions after restart, but only if the
 * client keeps retrying instead of letting Media3's short default retry budget
 * turn the outage into a fatal PlaybackException.
 */
@UnstableApi
internal class SiloMediaLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val invalidResponse = loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException
        return siloMediaLoadRetryDelayMs(
            responseCode = invalidResponse?.responseCode,
            retryAfterHeaders = invalidResponse?.retryAfterHeaders().orEmpty(),
            cause = loadErrorInfo.exception,
            errorCount = loadErrorInfo.errorCount,
        )
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        Int.MAX_VALUE
}

internal fun siloMediaLoadRetryDelayMs(
    responseCode: Int? = null,
    retryAfterHeaders: List<String> = emptyList(),
    cause: Throwable? = null,
    errorCount: Int,
    retryWindowMs: Long = MEDIA_LOAD_RETRY_WINDOW_MS,
): Long {
    if (!isRetryableMediaLoadFailure(responseCode, cause)) return C.TIME_UNSET

    val delayMs = retryAfterHeaders.firstNotNullOfOrNull(::parseRetryAfterDelayMs)
        ?: mediaLoadBackoffDelayMs(errorCount)
    val elapsedBeforeRetry = mediaLoadElapsedBeforeRetryMs(errorCount)
    return if (elapsedBeforeRetry + delayMs <= retryWindowMs) delayMs else C.TIME_UNSET
}

private fun isRetryableMediaLoadFailure(responseCode: Int?, cause: Throwable?): Boolean {
    if (responseCode != null) return responseCode.isRetryableMediaStatus()
    return cause is SocketTimeoutException ||
        cause is ConnectException ||
        cause is SocketException ||
        cause is UnknownHostException
}

private fun Int.isRetryableMediaStatus(): Boolean =
    this == 404 ||
        this == 500 ||
        this == 502 ||
        this == 503 ||
        this == 504 ||
        this in 520..527 ||
        this == 530

private fun parseRetryAfterDelayMs(value: String): Long? {
    val seconds = value.trim().toLongOrNull() ?: return null
    if (seconds < 0) return null
    return seconds * 1_000L
}

private fun mediaLoadBackoffDelayMs(errorCount: Int): Long =
    when (errorCount.coerceAtLeast(1)) {
        1 -> 1_000L
        2 -> 2_000L
        3 -> 4_000L
        else -> 8_000L
    }

private fun mediaLoadElapsedBeforeRetryMs(errorCount: Int): Long {
    var elapsed = 0L
    for (count in 1 until errorCount.coerceAtLeast(1)) {
        elapsed += mediaLoadBackoffDelayMs(count)
    }
    return elapsed
}

private fun HttpDataSource.InvalidResponseCodeException.retryAfterHeaders(): List<String> =
    headerFields.entries
        .firstOrNull { (key, _) -> key.equals("Retry-After", ignoreCase = true) }
        ?.value
        .orEmpty()

internal const val MEDIA_LOAD_RETRY_WINDOW_MS: Long = 90_000L
