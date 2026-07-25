package org.prairieserver.prairie.pairing

/**
 * Constants for the companion-pairing LAN protocol. Platform-neutral so this
 * mirrors the prairie-apple implementation byte-for-byte (Android NSD + sockets
 * layer is built on top of this).
 */
object PairingProtocol {
    /** Wire protocol version. Bump on any breaking change to message shapes. */
    const val VERSION = 1

    /** Bonjour/NSD service type the TV advertises and the phone browses for. */
    const val SERVICE_TYPE = "_prairiepair._tcp"
}
