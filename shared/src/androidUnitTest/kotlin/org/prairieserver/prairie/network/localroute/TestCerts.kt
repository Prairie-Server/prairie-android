package org.prairieserver.prairie.network.localroute

import java.io.File
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession

/** Self-signed certificates for the local-route tests, minted offline with the JDK's own keytool. */
internal object TestCerts {
    const val SAN_HOST = "lan.prairie.test"

    val server: X509Certificate by lazy { mint("prairie-lan-server") }
    val impostor: X509Certificate by lazy { mint("prairie-lan-impostor") }

    fun pin(certificate: X509Certificate): String = LocalRouteTls.spkiPin(certificate)!!

    private fun mint(alias: String): X509Certificate {
        val dir = Files.createTempDirectory("prairie-test-cert").toFile()
        try {
            val store = File(dir, "store.p12")
            val keytool = File(System.getProperty("java.home"), "bin/" + if (File.separatorChar == '\\') "keytool.exe" else "keytool")
            val process = ProcessBuilder(
                keytool.path, "-genkeypair", "-alias", alias, "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=$alias", "-ext", "san=dns:$SAN_HOST", "-validity", "2",
                "-storetype", "PKCS12", "-keystore", store.path, "-storepass", "changeit",
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "keytool failed: $output" }
            val keyStore = KeyStore.getInstance("PKCS12")
            store.inputStream().use { keyStore.load(it, "changeit".toCharArray()) }
            return keyStore.getCertificate(alias) as X509Certificate
        } finally {
            dir.deleteRecursively()
        }
    }

    /** An [SSLSession] that reports only a peer and the certificates it presented (none when [chain] is null). */
    fun session(host: String?, port: Int, chain: List<X509Certificate>?): SSLSession =
        java.lang.reflect.Proxy.newProxyInstance(
            SSLSession::class.java.classLoader,
            arrayOf(SSLSession::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getPeerHost" -> host
                "getPeerPort" -> port
                "getPeerCertificates" -> chain?.toTypedArray() ?: throw SSLPeerUnverifiedException("no peer")
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SSLSession
}
