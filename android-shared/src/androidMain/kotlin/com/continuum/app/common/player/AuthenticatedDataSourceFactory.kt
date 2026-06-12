package com.continuum.app.common.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.continuum.app.network.TokenManager
import okhttp3.OkHttpClient

/**
 * DataSource.Factory that resolves relative stream URLs against the server
 * base URL and lets [MediaAuthInterceptor] on the shared [OkHttpClient] inject
 * `Authorization: Bearer` + handle 401-refresh. Explicit header injection here
 * would shadow the interceptor and leave long HLS sessions stranded on stale
 * tokens.
 *
 * For offline playback the `streamUrl` is `file://…` (the downloaded media
 * file on disk); the routed source below delegates those to a [FileDataSource]
 * instead of OkHttp.
 */
@UnstableApi
class AuthenticatedDataSourceFactory(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    @Suppress("unused") private val tokenManager: TokenManager,
    private val serverUrlProvider: () -> String,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val http = OkHttpDataSource.Factory(okHttpClient).createDataSource()
        val file = FileDataSource()
        val content = ContentDataSource(context)
        return RoutedDataSource(http = http, file = file, content = content, serverUrl = serverUrlProvider())
    }
}

/**
 * Picks between a [FileDataSource] (offline media playback) and the shared
 * [OkHttpDataSource] (every other scheme) based on the DataSpec's URI. Also
 * folds in the relative-URL resolution that used to live in
 * `RelativeUrlDataSource`: a URI with no scheme is prefixed with the server
 * base URL and routed via OkHttp.
 */
@UnstableApi
private class RoutedDataSource(
    private val http: DataSource,
    private val file: DataSource,
    private val content: DataSource,
    private val serverUrl: String,
) : DataSource {

    /** Which downstream the most recent [open] call delegated to. Both
     *  [read]/[getUri]/[close] need to hit the same one to avoid double-
     *  closing or pulling from a stale stream. */
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        http.addTransferListener(transferListener)
        file.addTransferListener(transferListener)
        content.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val resolved = resolveDataSpec(dataSpec)
        val downstream = when {
            resolved.uri.scheme.equals("file", ignoreCase = true) -> file
            resolved.uri.scheme.equals("content", ignoreCase = true) -> content
            else -> http
        }
        active = downstream
        return downstream.open(resolved)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        (active ?: http).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun close() {
        active?.close()
        active = null
    }

    private fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme == null || uri.scheme!!.isEmpty()) {
            val baseUrl = serverUrl.trimEnd('/')
            val path = uri.toString()
            val absoluteUrl = "$baseUrl$path"
            return dataSpec.buildUpon()
                .setUri(Uri.parse(absoluteUrl))
                .build()
        }
        return dataSpec
    }
}
