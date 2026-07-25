package org.prairieserver.prairie.pairing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * The TV's advertised state, carried in the NSD TXT record and in [PairingMessage.Hello].
 *
 * Serialized as its lowercase raw value to match the Swift `PairingReceiverState`.
 */
enum class PairingReceiverState(val wire: String) {
    /** Blank TV with no server configured — needs a URL pushed. */
    Setup("setup"),

    /** TV already has a server — only needs a user signed in. */
    Login("login");

    companion object {
        fun fromWire(value: String): PairingReceiverState =
            entries.firstOrNull { it.wire == value }
                ?: throw PairingMessageException("Unknown PairingReceiverState: $value")
    }
}

/** Terminal per-server outcome status. Serialized as its raw value. */
enum class PairingServerStatus(val wire: String) {
    SignedIn("signedIn"),
    Failed("failed");

    companion object {
        fun fromWire(value: String): PairingServerStatus =
            entries.firstOrNull { it.wire == value }
                ?: throw PairingMessageException("Unknown PairingServerStatus: $value")
    }
}

/** Thrown when a message cannot be encoded or decoded against the wire schema. */
class PairingMessageException(message: String) : Exception(message)

/**
 * A message on the wire. Encoded as a JSON object with a `type` discriminator
 * and a `v` (version) field; per-type fields are flattened alongside. Tokens
 * NEVER appear in any message — the server delivers those to the TV over HTTPS.
 *
 * Byte-compatible with the prairie-apple `PairingMessage` Codable.
 */
sealed class PairingMessage {
    /** TV → phone, first message after the connection opens. */
    data class Hello(
        val tvName: String,
        val tvDeviceId: String,
        val state: PairingReceiverState,
        val supportedVersions: List<Int>,
    ) : PairingMessage()

    /** phone → TV, one per chosen server. */
    data class PushServer(
        val serverURL: String,
        val serverName: String?,
    ) : PairingMessage()

    /**
     * TV → phone, after the TV called device/start for a pushed server.
     * `matchCode` is advisory display only; the phone re-fetches the
     * authoritative match code from the server via lookup before approving.
     */
    data class DeviceStarted(
        val serverURL: String,
        val userCode: String,
        val matchCode: String,
    ) : PairingMessage()

    /** TV → phone, terminal per-server outcome. */
    data class ServerResult(
        val serverURL: String,
        val status: PairingServerStatus,
        val error: String?,
    ) : PairingMessage()

    /** phone → TV, no more servers; finish. */
    data object Done : PairingMessage()

    /** either direction, abort. */
    data class Cancel(
        val reason: String,
    ) : PairingMessage()
}

/**
 * Hand-written kotlinx.serialization codec producing the exact wire shape used
 * by prairie-apple. The discriminator key is `type`; `v` is always
 * [PairingProtocol.VERSION]. Optional fields (`serverName`, `error`) are omitted
 * when null.
 */
object PairingMessageCodec {
    private const val KEY_TYPE = "type"
    private const val KEY_V = "v"

    private val json = Json {
        // Deterministic, compact output matching the iOS encoder's defaults.
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(msg: PairingMessage): String = json.encodeToString(JsonObject.serializer(), toJsonObject(msg))

    fun decode(json: String): PairingMessage {
        val element = this.json.parseToJsonElement(json)
        val obj = element as? JsonObject
            ?: throw PairingMessageException("Pairing message must be a JSON object")
        return fromJsonObject(obj)
    }

    private fun toJsonObject(msg: PairingMessage): JsonObject = buildJsonObject {
        put(KEY_V, JsonPrimitive(PairingProtocol.VERSION))
        when (msg) {
            is PairingMessage.Hello -> {
                put(KEY_TYPE, JsonPrimitive("hello"))
                put("tvName", JsonPrimitive(msg.tvName))
                put("tvDeviceId", JsonPrimitive(msg.tvDeviceId))
                put("state", JsonPrimitive(msg.state.wire))
                put(
                    "supportedVersions",
                    buildJsonArray { msg.supportedVersions.forEach { add(JsonPrimitive(it)) } },
                )
            }
            is PairingMessage.PushServer -> {
                put(KEY_TYPE, JsonPrimitive("pushServer"))
                put("serverURL", JsonPrimitive(msg.serverURL))
                if (msg.serverName != null) put("serverName", JsonPrimitive(msg.serverName))
            }
            is PairingMessage.DeviceStarted -> {
                put(KEY_TYPE, JsonPrimitive("deviceStarted"))
                put("serverURL", JsonPrimitive(msg.serverURL))
                put("userCode", JsonPrimitive(msg.userCode))
                put("matchCode", JsonPrimitive(msg.matchCode))
            }
            is PairingMessage.ServerResult -> {
                put(KEY_TYPE, JsonPrimitive("serverResult"))
                put("serverURL", JsonPrimitive(msg.serverURL))
                put("status", JsonPrimitive(msg.status.wire))
                if (msg.error != null) put("error", JsonPrimitive(msg.error))
            }
            is PairingMessage.Done -> {
                put(KEY_TYPE, JsonPrimitive("done"))
            }
            is PairingMessage.Cancel -> {
                put(KEY_TYPE, JsonPrimitive("cancel"))
                put("reason", JsonPrimitive(msg.reason))
            }
        }
    }

    private fun fromJsonObject(obj: JsonObject): PairingMessage {
        val type = obj.requireString(KEY_TYPE)
        return when (type) {
            "hello" -> PairingMessage.Hello(
                tvName = obj.requireString("tvName"),
                tvDeviceId = obj.requireString("tvDeviceId"),
                state = PairingReceiverState.fromWire(obj.requireString("state")),
                supportedVersions = obj.requireIntArray("supportedVersions"),
            )
            "pushServer" -> PairingMessage.PushServer(
                serverURL = obj.requireString("serverURL"),
                serverName = obj.optionalString("serverName"),
            )
            "deviceStarted" -> PairingMessage.DeviceStarted(
                serverURL = obj.requireString("serverURL"),
                userCode = obj.requireString("userCode"),
                matchCode = obj.requireString("matchCode"),
            )
            "serverResult" -> PairingMessage.ServerResult(
                serverURL = obj.requireString("serverURL"),
                status = PairingServerStatus.fromWire(obj.requireString("status")),
                error = obj.optionalString("error"),
            )
            "done" -> PairingMessage.Done
            "cancel" -> PairingMessage.Cancel(
                reason = obj.requireString("reason"),
            )
            else -> throw PairingMessageException("Unknown pairing message type: $type")
        }
    }

    private fun JsonObject.requireString(key: String): String {
        val prim = (this[key] as? JsonPrimitive)
            ?: throw PairingMessageException("Missing field: $key")
        return prim.content
    }

    private fun JsonObject.optionalString(key: String): String? =
        (this[key] as? JsonPrimitive)?.content

    private fun JsonObject.requireIntArray(key: String): List<Int> {
        val arr = this[key]?.jsonArray
            ?: throw PairingMessageException("Missing field: $key")
        return arr.map { it.jsonPrimitive.int }
    }
}
