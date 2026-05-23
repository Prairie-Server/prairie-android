package com.continuum.app.common.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
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
 */
@UnstableApi
class AuthenticatedDataSourceFactory(
    private val okHttpClient: OkHttpClient,
    @Suppress("unused") private val tokenManager: TokenManager,
    private val serverUrlProvider: () -> String,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val upstream = OkHttpDataSource.Factory(okHttpClient).createDataSource()
        return RelativeUrlDataSource(upstream, serverUrlProvider())
    }
}

@UnstableApi
private class RelativeUrlDataSource(
    private val upstream: DataSource,
    private val serverUrl: String,
) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val resolvedSpec = resolveDataSpec(dataSpec)
        return upstream.open(resolvedSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        upstream.close()
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
