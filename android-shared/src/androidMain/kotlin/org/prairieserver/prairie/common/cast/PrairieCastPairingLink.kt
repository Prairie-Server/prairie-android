package org.prairieserver.prairie.common.cast

import java.net.URLDecoder
import org.prairieserver.prairie.cast.PrairieCastProtocol
import org.prairieserver.prairie.common.lan.PrairieCastSealed

/**
 * A Roku's pairing link, from the QR code or address on its Connect a Phone
 * screen (prairie-roku `docs/prairiecontrol.md` section 5.3), for networks where
 * DNS-SD cannot find it:
 *
 * `prairie://cast?v=2&host=<ip>&port=<port>&id=<deviceId>&name=<name>&server=<serverId>&security=plain|sealed[&key=<base32>]`
 *
 * The phone remembers the Roku by [target]'s `deviceId` and [pairingKey], finds
 * it over DNS-SD like any other TV, and dials `host:port` when DNS-SD finds
 * nothing. Unknown parameters are ignored.
 */
class PrairieCastPairingLink(val target: PrairieCastTarget, val pairingKey: ByteArray?) {
    companion object {
        fun parse(text: String): PrairieCastPairingLink? {
            val trimmed = text.trim()
            val prefix = "prairie://cast?"
            if (!trimmed.startsWith(prefix, ignoreCase = true)) return null
            val query = trimmed.substring(prefix.length).substringBefore('#')
                .split('&')
                .filter { '=' in it }
                .associate { part ->
                    decode(part.substringBefore('=')) to decode(part.substringAfter('='))
                }
            fun string(key: String) = query[key]?.trim()?.takeIf { it.isNotEmpty() }
            if (string("v")?.toIntOrNull() != PrairieCastProtocol.version) return null
            val host = string("host")?.takeIf(::isIpv4Literal) ?: return null
            val port = string("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val deviceId = string("id") ?: return null
            val (transport, key) = when (string("security")?.lowercase()) {
                null, "plain" -> PrairieCastTransport.Plain to null
                "sealed" -> PrairieCastTransport.Sealed to (string("key")?.let(PrairieCastSealed::parseKey) ?: return null)
                else -> return null
            }
            val name = string("name") ?: "Roku"
            return PrairieCastPairingLink(
                target = PrairieCastTarget(
                    deviceId = deviceId,
                    name = name,
                    host = host,
                    port = port,
                    version = PrairieCastProtocol.version,
                    serviceName = name,
                    serverId = string("server"),
                    transport = transport,
                    keyId = key?.let(PrairieCastSealed::keyId),
                ),
                pairingKey = key,
            )
        }

        private fun isIpv4Literal(host: String): Boolean {
            val parts = host.split('.')
            return parts.size == 4 && parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) && part.toInt() <= 255
            }
        }

        private fun decode(text: String): String = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
    }
}
