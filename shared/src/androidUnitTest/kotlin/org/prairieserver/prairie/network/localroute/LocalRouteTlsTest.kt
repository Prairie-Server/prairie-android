package org.prairieserver.prairie.network.localroute

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import org.prairieserver.prairie.network.HttpOrigin

/** Pinning behavior of the local-route TLS, with no network beyond loopback socket pairs. */
class LocalRouteTlsTest {
    private val server = TestCerts.server
    private val impostor = TestCerts.impostor
    private val state = LocalRouteState()

    /** Records which platform entry points were reached; rejects everything unless [accept]. */
    private class FakePlatform(val accept: Boolean = false) : X509TrustManager {
        val calls = mutableListOf<String>()
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = record("server")
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = record("client")
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        private fun record(call: String) {
            calls += call
            if (!accept) throw CertificateException("platform rejects")
        }
    }

    private class ExtendedPlatform : X509ExtendedTrustManager() {
        val calls = mutableListOf<String>()
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) { calls += "server-socket" }
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) { calls += "server-engine" }
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { calls += "server" }
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) { calls += "client-socket" }
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) { calls += "client-engine" }
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { calls += "client" }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun loopbackSocket(block: (Socket, Int) -> Unit) =
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
            Socket("127.0.0.1", listener.localPort).use { block(it, listener.localPort) }
        }

    private fun engine(host: String, port: Int): SSLEngine = SSLContext.getDefault().createSSLEngine(host, port)

    private fun routeAt(host: String, port: Int) =
        LocalRoute("s", HttpOrigin("https", "saved.test", 443), HttpOrigin("https", host, port), 1)

    // spkiPin

    @Test
    fun spkiPinIsStablePerKeyAndDistinctAcrossKeys() {
        assertEquals(TestCerts.pin(server), TestCerts.pin(server))
        assertNotEquals(TestCerts.pin(server), TestCerts.pin(impostor))
    }

    @Test
    fun spkiPinOfNothingOrANonX509IsNull() {
        assertNull(LocalRouteTls.spkiPin(null))
        val notX509 = object : java.security.cert.Certificate("fake") {
            override fun getEncoded() = ByteArray(0)
            override fun verify(key: java.security.PublicKey?) = Unit
            override fun verify(key: java.security.PublicKey?, sigProvider: String?) = Unit
            override fun toString() = "fake"
            override fun getPublicKey(): java.security.PublicKey? = null
        }
        assertNull(LocalRouteTls.spkiPin(notX509))
    }

    @Test
    fun platformTrustManagerIsTheSystemsX509() {
        assertNotNull(LocalRouteTls.platformTrustManager())
    }

    // PinningTrustManager: sockets

    @Test
    fun pinnedSocketAcceptsTheMatchingKeyWithoutAskingThePlatform() = loopbackSocket { socket, port ->
        val platform = FakePlatform()
        state.trust("127.0.0.1", port, TestCerts.pin(server))
        PinningTrustManager(platform, state).checkServerTrusted(arrayOf(server), "ECDHE_ECDSA", socket)
        assertTrue(platform.calls.isEmpty())
    }

    @Test
    fun pinnedSocketRejectsAnotherKeyAndReportsTheMismatch() = loopbackSocket { socket, port ->
        val platform = FakePlatform(accept = true)
        state.trust("127.0.0.1", port, TestCerts.pin(server))
        state.publish(routeAt("127.0.0.1", port))

        assertFailsWith<CertificateException> {
            PinningTrustManager(platform, state).checkServerTrusted(arrayOf(impostor), "ECDHE_ECDSA", socket)
        }
        assertTrue(platform.calls.isEmpty(), "a pinned address never falls through to platform trust")
        assertNull(state.active.value)
    }

    @Test
    fun pinnedSocketRejectsAnEmptyChain() = loopbackSocket { socket, port ->
        state.trust("127.0.0.1", port, TestCerts.pin(server))
        assertFailsWith<CertificateException> {
            PinningTrustManager(FakePlatform(accept = true), state).checkServerTrusted(emptyArray(), "ECDHE_ECDSA", socket)
        }
    }

    @Test
    fun unpinnedSocketGoesToThePlatform() = loopbackSocket { socket, _ ->
        val platform = FakePlatform(accept = true)
        PinningTrustManager(platform, state).checkServerTrusted(arrayOf(server), "x", socket)
        assertEquals(listOf("server"), platform.calls)

        assertFailsWith<CertificateException> {
            PinningTrustManager(FakePlatform(accept = false), state).checkServerTrusted(arrayOf(server), "x", socket)
        }
    }

    @Test
    fun nullSocketGoesToThePlatform() {
        val platform = FakePlatform(accept = true)
        PinningTrustManager(platform, state).checkServerTrusted(arrayOf(server), "x", null as Socket?)
        assertEquals(listOf("server"), platform.calls)
    }

    @Test
    fun unpinnedSocketUsesTheExtendedPlatformWhenItIsOne() = loopbackSocket { socket, _ ->
        val platform = ExtendedPlatform()
        PinningTrustManager(platform, state).checkServerTrusted(arrayOf(server), "x", socket)
        assertEquals(listOf("server-socket"), platform.calls)
    }

    // PinningTrustManager: engines

    @Test
    fun pinnedEngineAcceptsTheMatchingKeyAndRejectsAnotherOrEmpty() {
        state.trust("lan.test", 8920, TestCerts.pin(server))
        val platform = FakePlatform(accept = true)
        val manager = PinningTrustManager(platform, state)

        manager.checkServerTrusted(arrayOf(server), "x", engine("lan.test", 8920))
        assertTrue(platform.calls.isEmpty())
        assertFailsWith<CertificateException> { manager.checkServerTrusted(arrayOf(impostor), "x", engine("lan.test", 8920)) }
        assertFailsWith<CertificateException> { manager.checkServerTrusted(emptyArray(), "x", engine("lan.test", 8920)) }
    }

    @Test
    fun unpinnedOrMissingEngineGoesToThePlatform() {
        val platform = FakePlatform(accept = true)
        val manager = PinningTrustManager(platform, state)
        manager.checkServerTrusted(arrayOf(server), "x", engine("other.test", 443))
        manager.checkServerTrusted(arrayOf(server), "x", null as SSLEngine?)
        assertEquals(listOf("server", "server"), platform.calls)

        val extended = ExtendedPlatform()
        PinningTrustManager(extended, state).checkServerTrusted(arrayOf(server), "x", engine("other.test", 443))
        assertEquals(listOf("server-engine"), extended.calls)
    }

    // PinningTrustManager: the rest

    @Test
    fun plainEntryPointsDelegateToThePlatform() {
        val platform = FakePlatform(accept = true)
        val manager = PinningTrustManager(platform, state)
        manager.checkServerTrusted(arrayOf(server), "x")
        manager.checkClientTrusted(arrayOf(server), "x")
        assertEquals(listOf("server", "client"), platform.calls)
        assertEquals(0, manager.acceptedIssuers.size)
    }

    @Test
    fun clientTrustVariantsDelegateToThePlatform() = loopbackSocket { socket, _ ->
        val plain = FakePlatform(accept = true)
        PinningTrustManager(plain, state).apply {
            checkClientTrusted(arrayOf(server), "x", socket)
            checkClientTrusted(arrayOf(server), "x", engine("a.test", 1))
        }
        assertEquals(listOf("client", "client"), plain.calls)

        val extended = ExtendedPlatform()
        PinningTrustManager(extended, state).apply {
            checkClientTrusted(arrayOf(server), "x", socket)
            checkClientTrusted(arrayOf(server), "x", engine("a.test", 1))
        }
        assertEquals(listOf("client-socket", "client-engine"), extended.calls)
    }

    @Test
    fun hostAwareCheckAcceptsAPinnedLeafAndReturnsTheChain() {
        state.trust("lan.test", 8920, TestCerts.pin(server))
        val result = PinningTrustManager(FakePlatform(), state).checkServerTrusted(arrayOf(server), "x", "lan.test")
        assertEquals(listOf(server), result)
    }

    // PinOnlyTrustManager (the probe's), private, reached by reflection

    private fun pinOnly(pin: String): X509TrustManager {
        val type = Class.forName("org.prairieserver.prairie.network.localroute.PinOnlyTrustManager")
        val constructor = type.getDeclaredConstructor(String::class.java).apply { isAccessible = true }
        return constructor.newInstance(pin) as X509TrustManager
    }

    private fun presentedOtherKey(manager: X509TrustManager): Boolean =
        manager.javaClass.getMethod("getPresentedOtherKey").invoke(manager) as Boolean

    @Test
    fun pinOnlyAcceptsExactlyThePinnedLeaf() {
        val manager = pinOnly(TestCerts.pin(server))
        manager.checkServerTrusted(arrayOf(server), "x")
        assertFalse(presentedOtherKey(manager))
        assertEquals(0, manager.acceptedIssuers.size)
    }

    @Test
    fun pinOnlyRejectsAnotherKeyAndAnEmptyChainAndRemembersIt() {
        val other = pinOnly(TestCerts.pin(server))
        assertFailsWith<CertificateException> { other.checkServerTrusted(arrayOf(impostor), "x") }
        assertTrue(presentedOtherKey(other))

        val empty = pinOnly(TestCerts.pin(server))
        assertFailsWith<CertificateException> { empty.checkServerTrusted(emptyArray(), "x") }
        assertTrue(presentedOtherKey(empty))
    }

    @Test
    fun pinOnlyNeverAcceptsClientCertificates() {
        assertFailsWith<CertificateException> { pinOnly(TestCerts.pin(server)).checkClientTrusted(arrayOf(server), "x") }
    }

    // PinningHostnameVerifier, through the client LocalRouteTls configures

    private fun client(): OkHttpClient = LocalRouteTls(state, FakePlatform()).applyTo(OkHttpClient.Builder()).build()

    @Test
    fun pinnedHostVerifiesOnlyWhenTheSessionPresentsThePin() {
        state.trust("10.0.0.5", 8920, TestCerts.pin(server))
        val verifier = client().hostnameVerifier
        assertTrue(verifier.verify("10.0.0.5", TestCerts.session("10.0.0.5", 8920, listOf(server))))
        assertFalse(verifier.verify("10.0.0.5", TestCerts.session("10.0.0.5", 8920, listOf(impostor))))
    }

    @Test
    fun pinnedHostRejectsASessionWithoutCertificatesOrAnEmptyChain() {
        state.trust("10.0.0.5", 8920, TestCerts.pin(server))
        val verifier = client().hostnameVerifier
        assertFalse(verifier.verify("10.0.0.5", TestCerts.session("10.0.0.5", 8920, null)))
        assertFalse(verifier.verify("10.0.0.5", TestCerts.session("10.0.0.5", 8920, emptyList())))
    }

    @Test
    fun pinnedMismatchTakesTheActiveRouteOutOfUse() {
        state.trust("10.0.0.5", 8920, TestCerts.pin(server))
        state.publish(routeAt("10.0.0.5", 8920))
        assertFalse(client().hostnameVerifier.verify("10.0.0.5", TestCerts.session("10.0.0.5", 8920, listOf(impostor))))
        assertNull(state.active.value)
    }

    @Test
    fun unpinnedHostUsesTheNormalHostnameCheck() {
        val verifier = client().hostnameVerifier
        assertTrue(verifier.verify(TestCerts.SAN_HOST, TestCerts.session(TestCerts.SAN_HOST, 443, listOf(server))))
        assertFalse(verifier.verify("evil.test", TestCerts.session("evil.test", 443, listOf(server))))
    }

    // LocalRouteTls and HandshakeTimeoutSocketFactory

    @Test
    fun pinnedLayeredSocketGetsTheShortHandshakeTimeout() = loopbackSocket { plain, port ->
        state.trust("127.0.0.1", port, TestCerts.pin(server))
        client().sslSocketFactory.createSocket(plain, "127.0.0.1", port, false).use { ssl ->
            assertEquals(LocalRouteTls.HANDSHAKE_TIMEOUT_MS, ssl.soTimeout)
        }
    }

    @Test
    fun unpinnedOrHostlessLayeredSocketKeepsItsTimeout() = loopbackSocket { plain, port ->
        val factory = client().sslSocketFactory
        plain.soTimeout = 1234
        factory.createSocket(plain, "127.0.0.1", port, false).use { assertEquals(1234, it.soTimeout) }
        state.trust("127.0.0.1", port, TestCerts.pin(server))
        factory.createSocket(plain, null, port, false).use { assertEquals(1234, it.soTimeout) }
    }

    @Test
    fun otherFactoryMethodsDelegateToTheRealFactory() {
        val factory = client().sslSocketFactory
        assertTrue(factory.defaultCipherSuites.isNotEmpty())
        assertTrue(factory.supportedCipherSuites.isNotEmpty())
        factory.createSocket().use { assertFalse(it.isConnected) }
        val loopback = InetAddress.getByName("127.0.0.1")
        ServerSocket(0, 4, loopback).use { listener ->
            factory.createSocket("127.0.0.1", listener.localPort).use { assertTrue(it.isConnected) }
            factory.createSocket(loopback, listener.localPort).use { assertTrue(it.isConnected) }
            factory.createSocket("127.0.0.1", listener.localPort, loopback, 0).use { assertTrue(it.isConnected) }
            factory.createSocket(loopback, listener.localPort, loopback, 0).use { assertTrue(it.isConnected) }
        }
    }
}
