package org.prairieserver.prairie.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.prairieserver.prairie.model.request.RequestAttention
import org.prairieserver.prairie.model.request.RequestAvailability
import org.prairieserver.prairie.model.request.RequestDisplayState
import org.prairieserver.prairie.model.request.RequestMediaDetail
import org.prairieserver.prairie.model.request.RequestMediaType
import org.prairieserver.prairie.model.request.RequestOutcome
import org.prairieserver.prairie.model.request.RequestState
import org.prairieserver.prairie.model.request.RequestStatus
import org.prairieserver.prairie.model.request.RequestUserState
import org.prairieserver.prairie.model.request.record

/** The detail page's status decisions (Apple `RequestDetailStatusTitleTests` / `RequestsV2Tests` parity). */
class RequestDetailStatusTest {

    private val requestable = RequestMediaDetail(
        mediaType = RequestMediaType.Movie,
        tmdbId = 1,
        title = "Film",
        availability = RequestAvailability.Missing,
        request = RequestState(requestable = true),
    )

    @Test
    fun aFinishedDownloadTheTitleReadCallsRequestableStaysOnItsWay() {
        // The server's per-title state skips a completed request that hasn't
        // reached the library; the user's own record still knows.
        val state = RequestDetailUiState(
            detail = requestable,
            record = record(status = RequestStatus.Completed, state = RequestUserState.Processing),
        )
        assertEquals(RequestPrimaryAction.Status(RequestDisplayState.OnTheWay), state.primaryAction)
        assertEquals("Adding to your library", state.primaryActionTitle)
    }

    @Test
    fun aFailedRequestBecomesThePageStatusAndTheRequesterCanAskAgain() {
        val failed = record(status = RequestStatus.Downloading, outcome = RequestOutcome.Failed)
        val requester = RequestDetailUiState(detail = requestable, record = failed)
        assertEquals(RequestPrimaryAction.Request, requester.primaryAction)
        assertEquals("Request Again", requester.primaryActionTitle)
        assertEquals(RequestDisplayState.NeedsAttention(RequestAttention.Failed, null), requester.progress?.display)
    }

    @Test
    fun anOldFailureStopsCountingOnceAnotherRequestIsUnderWay() {
        val detail = requestable.copy(
            request = RequestState(status = RequestStatus.Approved, requestable = false, requestId = "someone-else"),
        )
        val state = RequestDetailUiState(detail = detail, record = record(id = "mine", outcome = RequestOutcome.Failed))
        assertNull(state.endedRequest)
        assertEquals(RequestPrimaryAction.Status(RequestDisplayState.OnTheWay), state.primaryAction)
    }

    @Test
    fun pendingRequestsOfferCancelOnlyToTheRequester() {
        val pending = record(status = RequestStatus.Pending)
        val detail = requestable.copy(request = RequestState(status = RequestStatus.Pending, requestId = pending.id))
        assertEquals(true, RequestDetailUiState(detail = detail, record = pending).canCancel)
        // Someone else's pending request: the page shows its status and offers
        // no action on it. Deciding on requests stays in web admin.
        val someoneElses = RequestDetailUiState(detail = detail)
        assertEquals(false, someoneElses.canCancel)
        assertEquals(RequestPrimaryAction.Status(RequestDisplayState.Pending), someoneElses.primaryAction)
        assertEquals("Requested", someoneElses.eyebrow)
    }

    @Test
    fun aRequestableFailedAnnotationStillOffersRequestToTheRequester() {
        // A card patched from the user's failed record carries its state but
        // stays requestable; the page must not block on it.
        val detail = requestable.copy(
            request = RequestState(status = RequestStatus.Downloading, state = RequestUserState.Failed, requestable = true),
        )
        assertEquals(RequestPrimaryAction.Request, RequestDetailUiState(detail = detail).primaryAction)
    }
}
