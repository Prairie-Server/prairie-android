package org.prairieserver.prairie.android.cast

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.prairieserver.prairie.common.cast.PrairieCastPairingLink
import org.prairieserver.prairie.common.cast.PrairieCastTarget
import org.prairieserver.prairie.common.cast.PrairieCastTransport

class PrairieCastRememberedRokuTest {
    private val link = checkNotNull(
        PrairieCastPairingLink.parse(
            "prairie://cast?v=2&host=192.168.1.20&port=52817&id=roku-1&name=Den%20Roku&server=c3J2&security=sealed&key=AAAQEAYEAUDAOCAJBIFQYDIOB4",
        ),
    )

    @Test
    fun aRememberedRokuDialsAsItsLinkSaid() {
        val target = PrairieCastRememberedRoku.from(link).toTarget()
        assertEquals(link.target.copy(serviceName = target.serviceName), target)
        assertEquals(PrairieCastTransport.Sealed, target.transport)
        assertEquals("be45cb26", target.keyId)
    }

    @Test
    fun discoveryWinsOverTheRememberedAddress() {
        val remembered = listOf(PrairieCastRememberedRoku.from(link), PrairieCastRememberedRoku.from(link.target.copy(deviceId = "roku-2", name = "Attic"), null))
        val found = PrairieCastTarget(deviceId = "roku-1", name = "Den Roku", host = "192.168.1.44", port = 52818, version = 2, transport = PrairieCastTransport.Sealed)
        val merged = PrairieCastRememberedRoku.merge(listOf(found), remembered)
        assertEquals(listOf("Attic", "Den Roku"), merged.map { it.name })
        assertEquals("192.168.1.44", merged.single { it.deviceId == "roku-1" }.host)
        assertEquals(PrairieCastTransport.Plain, merged.single { it.deviceId == "roku-2" }.transport)
    }

    @Test
    fun aPlainAdvertisementDoesNotDowngradeAPairedRoku() {
        val found = PrairieCastTarget(deviceId = "roku-1", name = "Den Roku", host = "192.168.1.66", port = 52817, version = 2, transport = PrairieCastTransport.Plain)
        val merged = PrairieCastRememberedRoku.merge(listOf(found), listOf(PrairieCastRememberedRoku.from(link))).single()
        assertEquals(PrairieCastTransport.Sealed, merged.transport)
        assertEquals("be45cb26", merged.keyId)
        assertEquals("192.168.1.66", merged.host)
    }

    @Test
    fun aTlsTvIsNeverMovedOffTlsByARememberedRoku() {
        val found = PrairieCastTarget(deviceId = "roku-1", name = "Den Roku", host = "192.168.1.66", port = 52817, version = 2)
        val merged = PrairieCastRememberedRoku.merge(listOf(found), listOf(PrairieCastRememberedRoku.from(link))).single()
        assertEquals(PrairieCastTransport.TlsPsk, merged.transport)
    }

    @Test
    fun storeChangesLandInTheOrderTheyWereMade() = runBlocking {
        val firstWriteStarted = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val store = object : PrairieCastRokuStore {
            val records = mutableListOf<PrairieCastRememberedRoku>()
            var writes = 0

            override fun all() = synchronized(this) { records.toList() }

            override fun remember(roku: PrairieCastRememberedRoku) {
                if (writes == 0) {
                    firstWriteStarted.countDown()
                    releaseFirstWrite.await()
                }
                synchronized(this) {
                    writes += 1
                    records.removeAll { it.deviceId == roku.deviceId }
                    records += roku
                }
            }

            override fun forget(deviceId: String) = synchronized(this) {
                writes += 1
                records.removeAll { it.deviceId == deviceId }
                Unit
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val writer = PrairieCastRokuWriter(store, scope) { throw it }
            writer.remember(PrairieCastRememberedRoku.from(link))
            assertTrue(firstWriteStarted.await(2, TimeUnit.SECONDS))
            writer.forget("roku-1")
            // Room for an out-of-order forget to run while the remember is stuck.
            delay(200)
            releaseFirstWrite.countDown()
            withTimeout(2_000) { while (synchronized(store) { store.writes } < 2) delay(10) }
            assertEquals(emptyList(), store.all())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun thePairingKeyNeverReachesALogLine() {
        val roku = PrairieCastRememberedRoku.from(link)
        assertFalse("AAAQ" in roku.toString())
    }
}
