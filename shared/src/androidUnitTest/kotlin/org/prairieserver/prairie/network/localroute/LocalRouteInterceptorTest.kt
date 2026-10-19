package org.prairieserver.prairie.network.localroute

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.prairieserver.prairie.network.HttpOrigin

/**
 * The interceptors against a fake network (an interceptor that answers in place
 * of the socket), so routing, fallback and pin checks run with no connection.
 */
class LocalRouteInterceptorTest {
    private val state = LocalRouteState()
    private val route = LocalRoute(
        serverId = "s",
        savedOrigin = HttpOrigin("https", "saved.test", 443),
        localOrigin = HttpOrigin("https", "10.0.0.5", 8920),
        generation = 7,
    )
    private val seen = mutableListOf<String>()

    /** Answers the request, noting the URL it was sent to. [onLocal] answers requests aimed at the LAN address. */
    private fun client(
        media: Boolean = false,
        onLocal: (Request) -> Response = { ok(it) },
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(LocalRouteInterceptor(state, media))
        .addInterceptor { chain ->
            val request = chain.request()
            seen += request.url.toString()
            if (request.url.host == "10.0.0.5") onLocal(request) else ok(request, "saved")
        }
        .build()

    private fun ok(request: Request, body: String = "local", responseBody: ResponseBody = body.toResponseBody()): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(responseBody).build()

    private fun routed(pin: String = "pin") {
        state.trust(route.localOrigin.host, route.localOrigin.port, pin)
        state.publish(route)
    }

    private fun get(path: String = "/api/v2/home", vararg headers: Pair<String, String>) =
        Request.Builder().url("https://saved.test$path").apply { headers.forEach { (k, v) -> header(k, v) } }.build()

    private fun post(path: String = "/api/v2/playback") =
        Request.Builder().url("https://saved.test$path").post("{}".toRequestBody("application/json".toMediaType())).build()

    private fun failingBody(failure: IOException = IOException("reset")): ResponseBody = object : ResponseBody() {
        override fun contentType(): MediaType? = "text/plain".toMediaType()
        override fun contentLength() = -1L
        override fun source(): BufferedSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = throw failure
            override fun timeout() = Timeout.NONE
            override fun close() = Unit
        }.buffer()
    }

    // routing decisions

    @Test
    fun aRoutedRequestGoesToTheLocalAddressAndReportsTheSavedOne() {
        routed()
        client().newCall(get("/api/v2/home?limit=3")).execute().use {
            assertEquals("local", it.body!!.string())
            assertEquals("https://saved.test/api/v2/home?limit=3", it.request.url.toString())
        }
        assertEquals(listOf("https://10.0.0.5:8920/api/v2/home?limit=3"), seen)
        assertNotNull(state.active.value)
    }

    @Test
    fun withoutAnActiveRouteTheSavedAddressIsUsed() {
        state.trust("10.0.0.5", 8920, "pin")
        client().newCall(get()).execute().use { assertEquals("saved", it.body!!.string()) }
        assertEquals(listOf("https://saved.test/api/v2/home"), seen)
    }

    @Test
    fun withoutAPinTheRequestStaysOnTheSavedAddress() {
        state.publish(route)
        client().newCall(get()).execute().use { assertEquals("saved", it.body!!.string()) }
    }

    @Test
    fun authWebSocketAndSavedAddressRequestsAreNeverRouted() {
        routed()
        val client = client()
        client.newCall(get("/api/v2/auth/refresh")).execute().close()
        client.newCall(get("/ws", "Upgrade" to "websocket")).execute().close()
        client.newCall(get("/api/v2/home", LocalRouteHeaders.SAVED_ADDRESS to "1")).execute().close()
        assertTrue(seen.all { it.startsWith("https://saved.test") }, seen.toString())
        assertEquals(3, seen.size)
    }

    @Test
    fun otherHostsAreNotRouted() {
        routed()
        client().newCall(Request.Builder().url("https://cdn.test/a.png").build()).execute().close()
        assertEquals(listOf("https://cdn.test/a.png"), seen)
    }

    @Test
    fun markersNeverReachTheNetwork() {
        routed()
        var markers = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(LocalRouteInterceptor(state, media = true))
            .addInterceptor { chain ->
                if (chain.request().header(LocalRouteHeaders.MEDIA) != null) markers++
                ok(chain.request())
            }
            .build()
        client.newCall(get("/video", LocalRouteHeaders.MEDIA to "7")).execute().close()
        assertEquals(0, markers)
    }

    @Test
    fun mediaIsRoutedOnlyForTheGenerationItStartedOn() {
        routed()
        val client = client(media = true)
        client.newCall(get("/video")).execute().close()
        client.newCall(get("/video", LocalRouteHeaders.MEDIA to "6")).execute().close()
        client.newCall(get("/video", LocalRouteHeaders.MEDIA to "7")).execute().close()
        assertEquals(
            listOf("https://saved.test/video", "https://saved.test/video", "https://10.0.0.5:8920/video"),
            seen,
        )
    }

    // failure and fallback

    @Test
    fun aGetThatFailsOnTheRouteDropsItAndGoesToTheSavedAddress() {
        routed()
        client(onLocal = { throw IOException("refused") }).newCall(get()).execute().use {
            assertEquals("saved", it.body!!.string())
        }
        assertNull(state.active.value)
    }

    @Test
    fun aPostThatNeverConnectedGoesAgain() {
        routed()
        client(onLocal = { throw IOException("refused") }).newCall(post()).execute().use {
            assertEquals("saved", it.body!!.string())
        }
        assertNull(state.active.value)
    }

    @Test
    fun aPostThatMayHaveReachedTheServerIsNotRepeated() {
        routed()
        val client = client(onLocal = {
            it.tag(LocalRouteAttempt::class.java)!!.connected = true
            throw IOException("reset after write")
        })
        assertFailsWith<IOException> { client.newCall(post()).execute() }
        assertNull(state.active.value)
        assertEquals(1, seen.size)
    }

    @Test
    fun aTimeoutBeforeConnectingIsARouteFailureButAfterConnectingIsNot() {
        routed()
        client(onLocal = { throw SocketTimeoutException("connect") }).newCall(get()).execute().close()
        assertNull(state.active.value)

        routed()
        val slow = client(onLocal = {
            it.tag(LocalRouteAttempt::class.java)!!.connected = true
            throw SocketTimeoutException("slow server")
        })
        assertFailsWith<SocketTimeoutException> { slow.newCall(get()).execute() }
        assertNotNull(state.active.value, "a slow server says nothing about the route")
    }

    @Test
    fun anInterruptedThreadLeavesTheRouteAlone() {
        routed()
        val client = client(onLocal = { throw InterruptedIOException("interrupted") })
        assertFailsWith<InterruptedIOException> { client.newCall(get()).execute() }
        assertNotNull(state.active.value)
    }

    @Test
    fun aCancelledCallLeavesTheRouteAlone() {
        routed()
        lateinit var call: okhttp3.Call
        val client = client(onLocal = {
            call.cancel()
            throw IOException("Canceled")
        })
        call = client.newCall(get())
        assertFailsWith<IOException> { call.execute() }
        assertNotNull(state.active.value)
    }

    @Test
    fun aBodyThatFailsBeforeItIsHandedOnIsRetriedAtTheSavedAddress() {
        routed()
        client(onLocal = { ok(it, responseBody = failingBody()) }).newCall(get()).execute().use {
            assertEquals("saved", it.body!!.string())
        }
        assertNull(state.active.value)
    }

    @Test
    fun aPostWhoseBodyFailsEarlyIsNotRepeated() {
        routed()
        val client = client(onLocal = { ok(it, responseBody = failingBody()) })
        assertFailsWith<IOException> { client.newCall(post()).execute() }
        assertNull(state.active.value)
    }

    @Test
    fun aStreamThatDiesLaterReportsTheRouteAndSurfacesTheFailure() {
        routed()
        val client = client(media = true, onLocal = { ok(it, responseBody = failingBody()) })
        val response = client.newCall(get("/video", LocalRouteHeaders.MEDIA to "7")).execute()
        response.use {
            val body = it.body!!
            assertEquals("text/plain".toMediaType(), body.contentType())
            assertEquals(-1L, body.contentLength())
            assertFailsWith<IOException> { body.string() }
        }
        assertNull(state.active.value)
    }

    @Test
    fun aHealthyBodyKeepsTheRouteAndCloses() {
        routed()
        client().newCall(get()).execute().use {
            assertEquals(5L, it.body!!.contentLength())
            assertEquals("local", it.body!!.string())
        }
        assertNotNull(state.active.value)
    }

    // asRoutedFor and the builder extension

    @Test
    fun asRoutedForMapsASavedUrlOntoTheLocalOriginOnlyForARoutedAttempt() {
        val saved = get("/a").url
        val attempt = LocalRouteAttempt(route, "pin")
        val routedRequest = Request.Builder().url(saved).tag(LocalRouteAttempt::class.java, attempt).build()
        assertEquals("https://10.0.0.5:8920/a", saved.asRoutedFor(routedRequest).toString())
        assertEquals(saved, saved.asRoutedFor(get("/a")))
        val other = "https://cdn.test/a".let { Request.Builder().url(it).build().url }
        assertEquals(other, other.asRoutedFor(routedRequest))
    }

    @Test
    fun localRouteInstallsBothInterceptorsAndTheTls() {
        val builder = OkHttpClient.Builder()
        builder.localRoute(state, LocalRouteTls(state, NoTrust), media = true)
        assertTrue(builder.interceptors().any { it is LocalRouteInterceptor })
        assertTrue(builder.networkInterceptors().first() is LocalRouteConnectionCheck)
    }

    private object NoTrust : javax.net.ssl.X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
    }

    // LocalRouteConnectionCheck

    private fun chainFor(request: Request, proceeded: MutableList<Request>): Interceptor.Chain =
        java.lang.reflect.Proxy.newProxyInstance(
            Interceptor.Chain::class.java.classLoader,
            arrayOf(Interceptor.Chain::class.java),
        ) { _, method, args ->
            when (method.name) {
                "request" -> request
                "connection" -> null
                "proceed" -> {
                    proceeded += args!![0] as Request
                    ok(args[0] as Request)
                }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Interceptor.Chain

    @Test
    fun anUnroutedNetworkRequestPassesThroughTheConnectionCheck() {
        val proceeded = mutableListOf<Request>()
        LocalRouteConnectionCheck(state).intercept(chainFor(get(), proceeded)).close()
        assertEquals(1, proceeded.size)
    }

    @Test
    fun aRoutedRequestOnAConnectionWithoutThePinnedKeyIsRefusedAndReported() {
        state.publish(route)
        val attempt = LocalRouteAttempt(route, "pin")
        val request = Request.Builder().url("https://10.0.0.5:8920/a").tag(LocalRouteAttempt::class.java, attempt).build()
        val proceeded = mutableListOf<Request>()

        assertFailsWith<IOException> { LocalRouteConnectionCheck(state).intercept(chainFor(request, proceeded)) }
        assertTrue(proceeded.isEmpty())
        assertNull(state.active.value)
    }

    @Test
    fun aRoutedAttemptAimedElsewhereIsNotChecked() {
        val attempt = LocalRouteAttempt(route, "pin")
        val request = Request.Builder().url("https://cdn.test/a").tag(LocalRouteAttempt::class.java, attempt).build()
        val proceeded = mutableListOf<Request>()
        LocalRouteConnectionCheck(state).intercept(chainFor(request, proceeded)).close()
        assertEquals(1, proceeded.size)
    }
}
