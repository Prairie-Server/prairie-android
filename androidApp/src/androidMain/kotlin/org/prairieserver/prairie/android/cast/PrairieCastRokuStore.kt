package org.prairieserver.prairie.android.cast

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.prairieserver.prairie.cast.PrairieCastProtocol
import org.prairieserver.prairie.common.cast.PrairieCastPairingLink
import org.prairieserver.prairie.common.cast.PrairieCastTarget
import org.prairieserver.prairie.common.cast.PrairieCastTransport
import org.prairieserver.prairie.common.lan.PrairieCastSealed

/**
 * A Roku added from its pairing link (prairie-roku `docs/prairiecontrol.md`
 * section 5.3). The phone finds it again by [deviceId]
 * over DNS-SD and dials [host]:[port] only when DNS-SD finds nothing. At
 * level `sealed` it keeps the Roku's pairing key, so the record lives in the
 * app's encrypted preferences.
 */
@Serializable
data class PrairieCastRememberedRoku(
    val deviceId: String,
    val name: String,
    val host: String,
    val port: Int,
    val serverId: String? = null,
    val serverName: String? = null,
    val serverIdentity: String? = null,
    /** The pairing key in base32; null for a Roku at level `plain`. */
    val key: String? = null,
) {
    val pairingKey: ByteArray? get() = key?.let(PrairieCastSealed::parseKey)

    fun toTarget(): PrairieCastTarget {
        val pairingKey = pairingKey
        return PrairieCastTarget(
            deviceId = deviceId,
            name = name,
            host = host,
            port = port,
            version = PrairieCastProtocol.version,
            serviceName = name,
            serverId = serverId,
            serverName = serverName,
            serverIdentity = serverIdentity,
            transport = if (pairingKey != null) PrairieCastTransport.Sealed else PrairieCastTransport.Plain,
            keyId = pairingKey?.let(PrairieCastSealed::keyId),
        )
    }

    // The key never reaches a log line.
    override fun toString(): String =
        "PrairieCastRememberedRoku(deviceId=$deviceId, name=$name, host=$host, port=$port, sealed=${key != null})"

    companion object {
        fun from(target: PrairieCastTarget, pairingKey: ByteArray?) = PrairieCastRememberedRoku(
            deviceId = target.deviceId,
            name = target.name,
            host = target.host,
            port = target.port,
            serverId = target.serverId,
            serverName = target.serverName,
            serverIdentity = target.serverIdentity,
            key = pairingKey?.let(PrairieCastSealed::base32),
        )

        fun from(link: PrairieCastPairingLink) = from(link.target, link.pairingKey)

        /**
         * The picker's list: every discovered TV, then each remembered Roku that
         * DNS-SD has not found, at its remembered address.
         */
        fun merge(found: List<PrairieCastTarget>, remembered: List<PrairieCastRememberedRoku>): List<PrairieCastTarget> {
            val foundIds = found.mapTo(HashSet()) { it.deviceId }
            val byId = remembered.associateBy { it.deviceId }
            val discovered = found.map { keepSealed(it, byId[it.deviceId]) }
            val missing = remembered.filterNot { it.deviceId in foundIds }.map { it.toTarget() }
            if (missing.isEmpty()) return discovered
            return (discovered + missing).sortedBy { it.name.lowercase() }
        }

        /**
         * A Roku paired at level `sealed` stays sealed when an advertisement
         * with its deviceId says `plain`: anyone on the network can advertise
         * that, so only a new pairing link drops the key. A Roku that really
         * went to `plain` refuses the sealed hello, and the user is asked to
         * scan its new code.
         */
        private fun keepSealed(target: PrairieCastTarget, roku: PrairieCastRememberedRoku?): PrairieCastTarget {
            val keyId = roku?.pairingKey?.let(PrairieCastSealed::keyId)
            if (target.transport != PrairieCastTransport.Plain || keyId == null) return target
            return target.copy(transport = PrairieCastTransport.Sealed, keyId = keyId)
        }
    }
}

interface PrairieCastRokuStore {
    fun all(): List<PrairieCastRememberedRoku>

    /** Adds [roku], replacing any record with its `deviceId`. */
    fun remember(roku: PrairieCastRememberedRoku)

    /** Removes the record for [deviceId], pairing key included. */
    fun forget(deviceId: String)
}

/**
 * Applies changes to [store] one at a time, in the order they were made, off
 * the caller's thread, so a forget never lands before the remember it follows.
 */
class PrairieCastRokuWriter(
    private val store: PrairieCastRokuStore,
    scope: CoroutineScope,
    onFailure: (Throwable) -> Unit,
) {
    private val changes = Channel<() -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (change in changes) runCatching { change() }.onFailure(onFailure)
        }
    }

    fun remember(roku: PrairieCastRememberedRoku) {
        changes.trySend { store.remember(roku) }
    }

    fun forget(deviceId: String) {
        changes.trySend { store.forget(deviceId) }
    }
}

/** [PrairieCastRokuStore] in the app's encrypted preferences, as one JSON list. */
class EncryptedPrefsPrairieCastRokuStore(private val prefs: SharedPreferences) : PrairieCastRokuStore {
    override fun all(): List<PrairieCastRememberedRoku> =
        prefs.getString(KEY, null)?.let { raw ->
            runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
        }.orEmpty()

    @Synchronized
    override fun remember(roku: PrairieCastRememberedRoku) {
        write(all().filterNot { it.deviceId == roku.deviceId } + roku)
    }

    @Synchronized
    override fun forget(deviceId: String) {
        write(all().filterNot { it.deviceId == deviceId })
    }

    private fun write(rokus: List<PrairieCastRememberedRoku>) {
        prefs.edit(commit = true) {
            if (rokus.isEmpty()) remove(KEY) else putString(KEY, json.encodeToString(serializer, rokus))
        }
    }

    private companion object {
        const val KEY = "prairiecast_rokus"
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(PrairieCastRememberedRoku.serializer())
    }
}
