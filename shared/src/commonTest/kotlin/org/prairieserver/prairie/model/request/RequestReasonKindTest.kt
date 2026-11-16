package org.prairieserver.prairie.model.request

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestReasonKindTest {
    @Test
    fun tokensMapToKinds() {
        assertEquals(RequestReasonKind.AlreadyRequested, RequestReasonKind.of(RequestReason.AlreadyRequested))
        assertEquals(RequestReasonKind.AlreadyAvailable, RequestReasonKind.of(RequestReason.AlreadyAvailable))
        assertEquals(RequestReasonKind.LimitReached, RequestReasonKind.of(RequestReason.QuotaExceeded))
        assertEquals(RequestReasonKind.LimitReached, RequestReasonKind.of("limit_reached"))
        assertEquals(RequestReasonKind.RequestsOff, RequestReasonKind.of(RequestReason.RequestsDisabled))
        assertEquals(RequestReasonKind.Blocked, RequestReasonKind.of(RequestReason.Blocked))
        assertEquals(RequestReasonKind.Blocked, RequestReasonKind.of(" requesting_blocked "))
        assertEquals(RequestReasonKind.NotSubmitted, RequestReasonKind.of("validation_failed"))
        assertEquals(RequestReasonKind.CannotChange, RequestReasonKind.of("invalid_state"))
        assertEquals(RequestReasonKind.NotFound, RequestReasonKind.of("not_found"))
        assertEquals(RequestReasonKind.Unconfirmed, RequestReasonKind.of(RequestUnconfirmedToken))
    }

    @Test
    fun unknownOrMissingTokensAreNull() {
        assertNull(RequestReasonKind.of(null))
        assertNull(RequestReasonKind.of("The server said no."))
    }
}
