package org.siloserver.silo.tv.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Restores focus to the item a flat, paginated surface was on when it opened a
 * detail page.
 *
 * The orchestration is identical everywhere it is needed — library grids,
 * personal lists, people, Collections, Requests — and it is not the kind of
 * thing to write twice. Its difficulty is not the happy path but the order in
 * which four independent things have to agree: pages arriving, a resolution
 * that changes as they do, a requester that binds only once its card composes,
 * and focus that can be requested successfully and still roll back. Each of
 * those was got wrong at least once while this was being derived on a single
 * screen; the point of gathering it here is that the next surface inherits the
 * conclusions rather than the derivation.
 *
 * Not for Search, which is not flat: it mixes library results with
 * request-provider results in separate containers with unrelated identifier
 * spaces, and needs real section ids and namespaced item ids.
 */
internal class TvFlatReturnRestoration internal constructor(
    /**
     * Held by the caller's [rememberSaveable] rather than mirrored into it.
     *
     * Mirroring meant writing to saveable state from composition and restoring
     * through a `remember` side effect — two sources of truth for the one value
     * that has to survive process death, which is exactly when identity is all
     * there is and the live focus state is gone.
     */
    private val targetState: MutableState<TvReturnTarget?>,
    /**
     * Whether a target was already saved when this holder was created — the
     * only honest way to tell "the viewer came back" from "the viewer just
     * arrived".
     *
     * Asking whether a target exists cannot answer it: browsing arms one on
     * every card focus, so on a fresh entry the answer flips the moment the
     * grid happens to take focus, and it races whatever else the screen wanted
     * to focus instead. Captured once, at creation, it cannot race anything.
     */
    val isReturning: Boolean,
) {
    internal var target: TvReturnTarget?
        get() = targetState.value
        set(value) { targetState.value = value }

    internal var focusedItemId by mutableStateOf<String?>(null)
    internal var attachedItemId by mutableStateOf<String?>(null)
    internal var destination by mutableStateOf<TvReturnResolution.Located?>(null)
    /**
     * Deliberately a plain field, not snapshot state: it is derived entirely
     * from the current inputs, so composition already re-runs when it can
     * change. Making it observable only invited an extra recomposition on
     * every change, for a value that is recomputed above the read anyway.
     */
    internal var provisionalItemIndex: Int = 0
    internal var inFlight by mutableStateOf(false)
    internal var completed by mutableStateOf(false)

    /**
     * The item index the restore requester belongs on.
     *
     * The published destination once there is one, so composition and the
     * watcher below cannot disagree about where focus is going; a provisional
     * position before that, which is what keeps a requester attached for
     * ordinary first entry.
     */
    val requesterItemIndex: Int get() = destination?.itemIndex ?: provisionalItemIndex



    /**
     * Report ordinary browse movement.
     *
     * Suppressed while a restoration runs: focus lands on other cards on the
     * way, and letting one of those redefine the launch item makes the
     * restoration confirm itself against something the viewer never opened.
     */
    fun onItemFocused(itemId: String, index: Int) {
        focusedItemId = itemId
        if (!inFlight) {
            target = TvReturnTarget(TvFlatSectionId, itemId, sectionIndex = 0, itemIndex = index)
        }
    }

    /**
     * Report a card LOSING focus. Callers must send this — without it
     * [focusedItemId] records what was focused once rather than what is focused
     * now, and the acquisition below reads it as the latter.
     *
     * That distinction is the whole game here: acquisition checks the identity
     * BEFORE issuing any request, so a stale value lets a restoration report
     * success without ever asking for focus, while focus sits on a control or
     * another container entirely.
     *
     * Guarded on identity because a gain elsewhere arrives before this loss:
     * clearing unconditionally would erase the position that just replaced it.
     */
    fun onItemFocusLost(itemId: String) {
        if (focusedItemId == itemId) focusedItemId = null
    }

    /**
     * Report a deliberate opening.
     *
     * Always arms, unlike [onItemFocused]. A card focused during a restoration
     * did not arm and its focus callback will not fire again, so opening it
     * would otherwise navigate carrying the previous trip's target.
     */
    fun onItemClicked(itemId: String, index: Int) {
        target = TvReturnTarget(
            TvFlatSectionId,
            itemId,
            sectionIndex = 0,
            // Only a fallback coordinate; keep the last one rather than
            // inventing the top of the list if the item is not in view.
            itemIndex = index.takeIf { it >= 0 } ?: target?.itemIndex ?: 0,
        )
    }

    /**
     * Report which item the restore requester is currently bound to.
     *
     * The card has to say this, because nothing else can. A card can be laid
     * out while its modifier still carries the previous binding, so "the slot
     * is visible" is not evidence that a request will reach the right node.
     */
    fun onRequesterAttached(itemId: String?) {
        attachedItemId = itemId
    }
}

/**
 * Drives one restoration for a flat, paginated surface.
 *
 * [itemIds], [hasMore], [isLoadingMore] and [errorMessage] are read live
 * throughout — a captured snapshot would freeze the hunt, and `snapshotFlow`
 * would observe nothing at all.
 *
 * [surfaceKey] identifies the surface. Changing it starts over completely —
 * target, landing state and the completion latch — because a different tab or
 * library is a different surface, not the same one with different contents.
 *
 * It is a String, and must be a stable injective one — an enum's `name`, an id,
 * a canonically encoded composite. It was `Any?` with `toString()`, which is
 * neither: `1` and `"1"` collide, `null` and `""` collide, and a default
 * `toString()` can change across process recreation and silently discard a
 * valid target. A key that quietly means two things is worse than no key.
 * The screen this was extracted from reset only the target and kept the latch,
 * which is unsound on its own terms: had its tab branches not disposed and
 * recreated the whole subtree, a second tab would have found the latch already
 * set and never restored focus at all.
 *
 * A caller changing [surfaceKey] MUST also recreate its item content —
 * separate composition branches, or `key(surfaceKey) { … }` around the list.
 * The acknowledgements this depends on come from the cards themselves, and a
 * card that neither disposes nor moves has no reason to report its attachment
 * again; the fresh holder then waits for something nobody will send and gives
 * up, losing restoration on a surface that looks fine.
 *
 * That is a precondition, not something this can rescue: see the acquisition
 * below for why a blind request is unsafe. Existing callers satisfy it by
 * construction — a new one must check.
 *
 * [scrollToItem] receives an ITEM index; a surface with headers ahead of its
 * items converts. [onRestored] fires only on a confirmed landing, so a caller
 * never tells its shell that content took focus when it did not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun rememberTvFlatReturnRestoration(
    itemIds: List<String>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    /**
     * True while the surface is REPLACING its list rather than extending it —
     * a resume refresh that reloads from offset zero.
     *
     * This is not the same signal as [isLoadingMore] and cannot be folded into
     * it. [isLoadingMore] is only consulted once resolution has already come
     * back Pending, and a stale multi-page list still CONTAINS the target, so
     * it resolves Exact and that check is never reached. Restoration would then
     * scroll and acquire against a list the refresh is about to throw away:
     * either the card vanishes mid-acquisition and the one-shot attempt fails,
     * or focus lands on a card that is removed a frame later.
     *
     * So a replacement gates the FIRST resolution, not the page hunt. Once the
     * refresh has settled and the truncated list is authoritative, a target
     * beyond it is genuinely Pending and paging back toward it is the right
     * answer rather than a race.
     */
    isReplacingContent: Boolean = false,
    errorMessage: String?,
    surfaceKey: String,
    onLoadMore: () -> Unit,
    scrollToItem: suspend (itemIndex: Int) -> Unit,
    requestFocus: () -> Boolean,
    onRestored: () -> Unit,
    /**
     * What to do on a surface with nothing recorded yet — ordinary first entry.
     *
     * True focuses the first item, which is what a surface whose entry point IS
     * its first card wants, and makes restoration subsume its plain initial
     * focus. False stands down and leaves entry to the screen: a person page
     * deliberately opens on its filter chips so the identity header stays
     * visible, and a restoration that grabbed the first poster instead would
     * break that on every visit while only being wanted on returns.
     *
     * "Fresh arrival" here means no target was saved before this composition —
     * see [TvFlatReturnRestoration.isReturning].
     */
    focusFirstItemWithoutTarget: Boolean = true,
): TvFlatReturnRestoration {
    // The key is saved WITH the target and checked on the way back.
    // rememberSaveable does not validate restored values against its inputs, so
    // a process that comes back composing a different surface first would
    // otherwise adopt the previous surface's target as its own.
    val savedTarget = rememberSaveable(
        surfaceKey,
        stateSaver = keyedTvReturnTargetSaver(surfaceKey),
    ) {
        mutableStateOf<TvReturnTarget?>(null)
    }
    val restoration = remember(surfaceKey, savedTarget) {
        TvFlatReturnRestoration(savedTarget, isReturning = savedTarget.value != null)
    }

    val currentItemIds by rememberUpdatedState(itemIds)
    val currentHasMore by rememberUpdatedState(hasMore)
    val currentIsLoadingMore by rememberUpdatedState(isLoadingMore)
    val currentIsReplacing by rememberUpdatedState(isReplacingContent)
    val currentError by rememberUpdatedState(errorMessage)
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    val currentScrollToItem by rememberUpdatedState(scrollToItem)
    val currentRequestFocus by rememberUpdatedState(requestFocus)
    val currentOnRestored by rememberUpdatedState(onRestored)

    fun resolve(final: Boolean): TvReturnResolution = resolveTvReturnTarget(
        target = restoration.target,
        sections = flatTvReturnSections(itemIds = currentItemIds, hasMore = currentHasMore),
        treatAbsenceAsFinal = final,
    )

    restoration.provisionalItemIndex = remember(itemIds, hasMore, restoration.target) {
        (resolve(final = true) as? TvReturnResolution.Located)?.itemIndex ?: 0
    }

    // One hunt, driven as a loop rather than by re-keying this effect.
    //
    // Re-keying cannot work: Pending is a singleton, so a resolution that stays
    // Pending is an unchanged key and the effect never re-runs. Every way a
    // page can fail to move the list — a page of hidden or duplicate items, a
    // failed fetch that leaves hasMore set, a request the surface rejects
    // because one is already in flight — would stall restoration forever.
    LaunchedEffect(surfaceKey, itemIds.isNotEmpty()) {
        if (restoration.completed || currentItemIds.isEmpty()) return@LaunchedEffect
        // A fresh arrival on a surface that owns its own entry focus. Decided
        // from state saved before this composition began, so it cannot depend
        // on whether a card has taken focus yet.
        if (!restoration.isReturning && !focusFirstItemWithoutTarget) return@LaunchedEffect
        // Claimed before the settle delay, not after: default or restored focus
        // can land during it, and its callback would redefine the launch item.
        restoration.inFlight = true
        try {
            delay(TvFlatReturnSettleDelayMillis)

            // Ordered after the settle delay on purpose: a resume refresh is
            // usually dispatched a frame or two after the screen recomposes, so
            // checking on arrival would sail straight past one that has not
            // raised its flag yet.
            //
            // Bounded, because a surface wedged in refresh must not hold
            // restoration open forever. Timing out ABANDONS — see below; an
            // earlier version proceeded against whatever list existed, and
            // that was the defect, not the fallback.
            //
            // Quiet has to be PROVEN, not assumed. Checking the flag on
            // arrival was the first mistake: a false reading is exactly what a
            // refresh dispatched one frame later also looks like. Watching for
            // a restart with a SECOND subscription was the next one — a
            // complete true→false pulse between the two subscriptions slips
            // past unobserved.
            //
            // One subscription, then. collectLatest restarts the quiet timer on
            // every change, so the window only elapses if nothing happened
            // during it, which is what "settled" has to mean.
            val settled = withTimeoutOrNull(TvFlatReturnReplaceWaitMillis) {
                snapshotFlow { currentIsReplacing }
                    .transformLatest { replacing ->
                        if (!replacing) {
                            delay(TvFlatReturnSettleDelayMillis)
                            emit(Unit)
                        }
                    }
                    .first()
            } != null

            // Abandon rather than proceed. Falling through was the previous
            // answer, on the reasoning that a replacement landing mid-flight
            // would fail the identity check and simply not restore — but that
            // is not guaranteed. A target from the OUTGOING list can resolve
            // Exact, skip the hunt, take focus and report a landing in the
            // moment before the replacement removes the card underneath it,
            // and then focus is somewhere nobody chose and the shell has been
            // told content owns it.
            //
            // The target is deliberately left intact: this restoration is
            // giving up, not deciding the target was wrong, so a later entry
            // can still honour it.
            if (!settled) return@LaunchedEffect

            // An even earlier version retargeted to the first item on timeout,
            // reasoning that a reload produces page one so index zero cannot be
            // invalidated. That was wrong twice over: the id came from the
            // OUTGOING list, which a replacement can reorder, empty or drop
            // that item from entirely, and it overwrote the real target in
            // saved state where no later entry could retry it.

            withTimeoutOrNull(TvFlatReturnHuntBudgetMillis) {
                var requests = 0
                while (resolve(final = false) is TvReturnResolution.Pending) {
                    val loadedBefore = currentItemIds.size
                    // A refresh in flight is a load too — asking for the next
                    // page on top of it appends at an offset the refresh is
                    // about to invalidate.
                    if (!currentIsLoadingMore && !currentIsReplacing) {
                        // The ceiling stops NEW requests. It must not stop us
                        // waiting for one already in flight, or the last page
                        // is abandoned with budget to spare.
                        if (requests >= TvFlatReturnPageRequests) break
                        currentLoadMore()
                        requests++
                    }
                    val waited = awaitFlatPageSettled(
                        loadedBefore = loadedBefore,
                        itemCount = { currentItemIds.size },
                        isLoadingMore = { currentIsLoadingMore || currentIsReplacing },
                    )

                    // Judge the OUTCOME, and only when a load actually reached
                    // a terminal state. Growth is progress whatever an older
                    // error still says; a load that finished with nothing to
                    // show and left an error behind is a real failure —
                    // including when its message matches the previous one,
                    // which comparing error values would miss.
                    //
                    // NeverActive is why that distinction is needed: the
                    // request may still be queued, and the idle flag and any
                    // error then describe the world BEFORE it.
                    val grew = currentItemIds.size != loadedBefore
                    if (waited == TvFlatPageWait.Settled &&
                        !grew &&
                        !currentIsLoadingMore &&
                        currentError != null
                    ) {
                        break
                    }
                }
            }

            val located = resolve(final = true) as? TvReturnResolution.Located
            // Published as one value so the requester's position and the
            // identity being watched for cannot drift apart. They did: a
            // provisional index tracking live data while the identity stayed
            // frozen moved the requester onto the real item and left the
            // watcher waiting for the fallback — focus arriving exactly where
            // it should and being recorded as a failure.
            restoration.destination = located
            val targetItemId = located?.itemId ?: currentItemIds.firstOrNull()

            currentScrollToItem(located?.itemIndex ?: 0)
            // Readiness is part of the acquisition rather than a wait beside
            // it. Waiting separately and requesting anyway on timeout only
            // delays a wrong-node request; reporting NotReady holds the request
            // until the requester is genuinely bound, and gives up honestly if
            // it never is.
            val landed = requestFocusUntilObserved(
                maxAttempts = TvFlatReturnFocusAttempts,
                awaitAttempt = { delay(TvFlatReturnFocusRetryMillis) },
                requestFocus = currentRequestFocus,
                isFocused = {
                    targetItemId != null && restoration.focusedItemId == targetItemId
                },
                targetState = {
                    if (targetItemId != null && restoration.attachedItemId == targetItemId) {
                        TvFocusTargetState.Ready
                    } else {
                        TvFocusTargetState.NotReady
                    }
                },
            )
            // Latch either way — nothing re-keys this effect, so not latching
            // means never trying again rather than trying later. But only
            // report success when focus actually landed on the intended item.
            //
            // No blind rescue here. One was tried: if the identity-gated
            // acquisition failed and no card had reported focus, request
            // anyway and take whatever lands. It is unsound, because "no card
            // reported focus" is not "nothing has focus" — these surfaces also
            // hold sort and filter controls, genre chips and an A–Z rail, and
            // focus can be sitting legitimately on any of them. The rescue
            // would then steal it, seconds after the viewer arrived, which is
            // worse than the restoration it was trying to salvage. Making it
            // safe needs an authoritative "nothing on this screen has focus"
            // signal that only the caller can provide.
            if (landed == TvObservedFocusResult.Focused) currentOnRestored()
        } finally {
            restoration.inFlight = false
        }
        restoration.completed = true
    }

    return restoration
}

/** Lets the surface settle before restoration competes with default focus. */
private const val TvFlatReturnSettleDelayMillis: Long = 120L

/**
 * How long to wait for a requested page to mark itself active.
 *
 * Short, because this only observes a flag set by an already-launched
 * coroutine. Sizing it like a network round trip made a missed pulse — the load
 * completing between two snapshot evaluations — cost seconds of frozen focus
 * for nothing.
 */
private const val TvFlatReturnPageActiveTimeoutMillis: Long = 300L

/** How long to wait for an active page to settle. */
private const val TvFlatReturnPageSettleTimeoutMillis: Long = 2_000L

/**
 * The whole budget for hunting a target through unloaded pages.
 *
 * A wall clock, paired with — not replaced by — [TvFlatReturnPageRequests]. The
 * two bound different things: this is what the viewer experiences, that is how
 * hard a struggling endpoint gets pushed. Counting requests alone let one slow
 * fetch spend the entire allowance on a single page and gave no ceiling at all.
 */
/**
 * How long restoration will wait for a content REPLACEMENT to settle before it
 * resolves anyway.
 *
 * Generous, because waiting costs nothing visible — the surface is mid-refresh
 * and has no stable content to focus regardless — while giving up early costs a
 * restoration against a list that is about to be discarded. It exists only so a
 * refresh that never completes cannot wedge the surface.
 */
private const val TvFlatReturnReplaceWaitMillis: Long = 3_000L

private const val TvFlatReturnHuntBudgetMillis: Long = 6_000L

/** How many pages one restoration may ask for. */
private const val TvFlatReturnPageRequests: Int = 4

private const val TvFlatReturnFocusRetryMillis: Long = 60L

private val TvFlatReturnFocusAttempts: Int =
    (TvFocusAcquisitionBudgetMillis / TvFlatReturnFocusRetryMillis).toInt()

/**
 * What a wait for a requested page actually observed.
 *
 * The distinction matters because the caller decides whether a page load
 * failed, and it can only do that honestly for a load it saw reach a terminal
 * state. [NeverActive] carries no information about any load — the flags it
 * would otherwise read describe the world before the request.
 */
private enum class TvFlatPageWait {
    /**
     * A load reached a terminal state, or the list changed. Not necessarily
     * the load this caller requested — an already-active one can be what
     * settles — which is fine, because the caller reads this only as "a result
     * exists to judge".
     */
    Settled,

    /** Nothing marked itself active in time — the request may still be queued. */
    NeverActive,

    /** Observed running, but not finished when the wait expired. */
    StillActive,
}

/**
 * Wait for a requested page, reporting what was observed.
 *
 * A surface typically launches its load, so the loading flag is not reliably
 * set by the time the request returns. Waiting only for "not loading" can
 * therefore succeed instantly against the state from before the request, and a
 * caller in a loop fires every request it has before the first fetch marks
 * itself active — which the surface then accepts, because it also still sees
 * idle. So this waits for a load to become active first, and only then for it
 * to settle.
 *
 * Settling means the list changed OR loading cleared: a page can legitimately
 * arrive without changing the visible list, and a failed fetch leaves `hasMore`
 * set. Both phases time out, so this always returns promptly.
 */
private suspend fun awaitFlatPageSettled(
    loadedBefore: Int,
    itemCount: () -> Int,
    isLoadingMore: () -> Boolean,
): TvFlatPageWait {
    val became = withTimeoutOrNull(TvFlatReturnPageActiveTimeoutMillis) {
        snapshotFlow { itemCount() to isLoadingMore() }
            .first { (count, loading) -> count != loadedBefore || loading }
    } ?: return TvFlatPageWait.NeverActive
    if (became.first != loadedBefore) return TvFlatPageWait.Settled
    val settled = withTimeoutOrNull(TvFlatReturnPageSettleTimeoutMillis) {
        snapshotFlow { itemCount() to isLoadingMore() }
            .first { (count, loading) -> count != loadedBefore || !loading }
    }
    return if (settled == null) TvFlatPageWait.StillActive else TvFlatPageWait.Settled
}

/**
 * Saves [value] alongside the surface that owns it, and refuses a restored
 * payload belonging to a different one.
 *
 * `rememberSaveable(key)` resets when a running composition observes a changed
 * input, but it does NOT validate a value RESTORED after process death against
 * that input — so a process coming back on a different surface first would
 * adopt the previous surface's state as its own. Same reasoning as
 * [keyedTvReturnTargetSaver]; this is the scalar case.
 */
internal fun keyedBooleanSaver(resetToken: String, slot: String): Saver<Boolean, Any> =
    listSaver(
        save = { value -> listOf(resetToken, slot, value) },
        restore = { saved ->
            val values = saved as? List<*> ?: return@listSaver false
            val owned = values.size == 3 && values[0] == resetToken && values[1] == slot
            // Typed, not cast. An erased `as T` validates against Any, so a
            // payload with the right owner and the wrong type passes here and
            // fails later where Compose reads it — a crash during restoration
            // rather than a value we can reject. The slot name is carried too:
            // the owner token alone cannot tell one scalar slot from another.
            if (owned) values[2] as? Boolean ?: false else false
        },
    )

internal fun keyedIntSaver(resetToken: String, slot: String): Saver<Int, Any> =
    listSaver(
        save = { value -> listOf(resetToken, slot, value) },
        restore = { saved ->
            val values = saved as? List<*> ?: return@listSaver 0
            val owned = values.size == 3 && values[0] == resetToken && values[1] == slot
            if (owned) values[2] as? Int ?: 0 else 0
        },
    )

internal fun keyedTvReturnTargetSaver(resetToken: String): Saver<TvReturnTarget?, Any> =
    listSaver(
        save = { target ->
            target?.let {
                listOf(resetToken, it.sectionId, it.itemId, it.sectionIndex, it.itemIndex)
            } ?: listOf(resetToken)
        },
        restore = { saved ->
            val values = saved as? List<*>
            if (values == null || values.size != 5 || values[0] != resetToken) {
                null
            } else {
                // Safe casts throughout: a right-owner, wrong-shape payload
                // should restore as "nothing to return to", not throw and take
                // the screen down during restoration.
                val sectionId = values[1] as? String
                val itemId = values[2] as? String
                val sectionIndex = values[3] as? Int
                val itemIndex = values[4] as? Int
                if (sectionId == null || itemId == null ||
                    sectionIndex == null || itemIndex == null
                ) {
                    null
                } else {
                    TvReturnTarget(
                        sectionId = sectionId,
                        itemId = itemId,
                        sectionIndex = sectionIndex,
                        itemIndex = itemIndex,
                    )
                }
            }
        },
    )
