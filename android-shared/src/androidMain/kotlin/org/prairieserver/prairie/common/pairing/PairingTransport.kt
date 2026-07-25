package org.prairieserver.prairie.common.pairing

import org.prairieserver.prairie.pairing.PairingMessage
import kotlinx.coroutines.flow.Flow

/**
 * A bidirectional channel for one pairing connection, carrying decoded
 * [PairingMessage]s in both directions.
 *
 * This abstracts the real TLS-PSK socket transport away from the
 * [PairingReceiver] state machine so the state machine can be unit-tested over
 * an in-memory fake (no socket / no TLS). The real implementation is
 * [TlsPskPairingTransport]; tests substitute their own.
 *
 * One transport == one connection. Both [send] and [incoming] become inert once
 * the connection closes (transport error, EOF, or [close]); [incoming]
 * completes and [send] throws.
 */
interface PairingTransport {
    /**
     * The stream of inbound messages decoded off the wire. Completes normally on
     * a clean EOF / [close], or completes exceptionally on a transport / decode
     * error. Collect exactly once.
     */
    val incoming: Flow<PairingMessage>

    /** Encode and send one [message] as a single length-prefixed frame. */
    suspend fun send(message: PairingMessage)

    /** Tear down the connection. Idempotent. */
    fun close()
}
