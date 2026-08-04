package org.siloserver.silo.tv.ui.focus

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import kotlin.math.abs

/**
 * The section id a surface with no sections uses.
 *
 * Library grids, personal lists and people have exactly one implicit section,
 * and it is spelled out rather than left null so that "same section" means the
 * same thing everywhere. A nullable section id looked tidier and was a trap:
 * nothing in the section list could ever equal null, so a flat surface could
 * never match its own launch section and every surviving item resolved as a
 * positional fallback — the exact behaviour this contract exists to replace.
 *
 * Search is NOT one of these, despite looking like a list of results: it mixes
 * library items with request-provider results in separate containers, which
 * carry unrelated identifier spaces. It uses real section ids per container and
 * namespaced item ids, as below.
 *
 * Surfaces whose identity is composite must namespace and encode it the same
 * way on both sides — `catalog:<contentId>` versus `request:<mediaType>:<tmdbId>`
 * — so that two identifier spaces cannot collide on a bare number. Ids are
 * opaque to this contract, which is exactly why the encoding has to be
 * deliberate at the surface.
 *
 * The encoding must be stable, canonical and injective: every domain on a
 * heterogeneous surface tagged, and any component that could itself contain the
 * separator escaped or length-prefixed. Reserving a separator by convention is
 * not enough — a title or provider key containing it silently forges a
 * different identity.
 */
internal const val TvFlatSectionId: String = ""

/**
 * Where a surface was when it opened a detail page, and how to find that place
 * again afterwards.
 *
 * Restoration used to be a pair of saved indices, which is only correct while
 * the data is identical on the way back. It rarely is: a feed refreshes on
 * resume, Continue Watching reorders the moment something is played, a finished
 * item leaves the row it was in, a grid is re-sorted, and a recreated process
 * rebuilds from whatever the server says now. Saved indices survive all of that
 * syntactically and point at different content, so focus lands on something the
 * viewer never chose — the failure being removed here.
 *
 * Identity is therefore the item itself, qualified by the section it was in.
 * The pair is the *occurrence*: feeds routinely carry the same item in more
 * than one row, and "the copy in Continue Watching" is not the same place as
 * "the copy in Recently Added". Indices are kept, but only as coordinates for
 * the fallback: they answer "roughly where was I" once the occurrence is gone,
 * and nothing else.
 *
 * [itemId] is deliberately not called a content id. Surfaces restore to
 * profiles, libraries, people and requests as well as media, and each brings
 * its own identifier. Where a surface's natural identity is composite (a
 * request's provider and id, say), it composes one string and uses it
 * consistently on both sides.
 */
internal data class TvReturnTarget(
    /** The row/section the item was in; [TvFlatSectionId] on a flat surface. */
    val sectionId: String,
    val itemId: String,
    /** Fallback coordinate only. Never the primary identity. */
    val sectionIndex: Int,
    /** Fallback coordinate only. Never the primary identity. */
    val itemIndex: Int,
)

/**
 * Persists a [TvReturnTarget] across process death.
 *
 * Recreation is the case that most needs identity and least has it: the saved
 * *node* the focus restorer would have used is gone, live state like the
 * currently focused item id was never saveable, and what comes back is whatever
 * the server says now. Indices alone survive that, and are least trustworthy
 * exactly when they would otherwise be all that is left.
 */
internal val TvReturnTargetSaver: Saver<TvReturnTarget?, Any> = listSaver(
    save = { target ->
        target?.let { listOf(it.sectionId, it.itemId, it.sectionIndex, it.itemIndex) } ?: emptyList()
    },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        val values = saved as List<Any>
        if (values.isEmpty()) {
            null
        } else {
            TvReturnTarget(
                sectionId = values[0] as String,
                itemId = values[1] as String,
                sectionIndex = values[2] as Int,
                itemIndex = values[3] as Int,
            )
        }
    },
)

/** A section of restorable content, as it stands *now*. */
internal data class TvReturnSection(
    val id: String,
    val itemIds: List<String>,
    /**
     * Whether [itemIds] is everything this section will hold *for the load in
     * progress* — not for all time. A later refresh is a new snapshot, and a
     * complete section can come back different.
     *
     * False while pages are still loading. Most surfaces here paginate, and an
     * item on page four is missing from page one in exactly the way a deleted
     * item is — so without this the resolver reads "not loaded yet" as "gone"
     * and burns the target on a positional fallback before the real answer
     * arrives.
     */
    val isComplete: Boolean = true,
)

/**
 * Whether an item found in a *different* section counts as the same place.
 *
 * Off by default, because the two situations that produce it are
 * indistinguishable from the data: an item genuinely moved between rows, or the
 * launched occurrence disappeared while a copy that was always there sits
 * elsewhere. Treating the second as a match throws focus vertically across the
 * feed to a card the viewer never touched, which is worse than landing where
 * they were.
 *
 * Overlapping feeds — Home, where a title can sit in Continue Watching and
 * Recently Added at once — must stay on [SameSectionOnly]. Once the occurrence
 * is defined as the pair, an item leaving its row means that occurrence is
 * gone, and the positional fallback is the honest answer. Only surfaces whose
 * sections are disjoint by construction can opt in.
 */
internal enum class TvReturnRelocation { SameSectionOnly, FollowAcrossSections }

/** Where a surface should put focus when it comes back. */
internal sealed interface TvReturnResolution {
    /** A destination, carrying what it resolved to and not only where. */
    sealed interface Located : TvReturnResolution {
        val sectionIndex: Int
        val itemIndex: Int
        val sectionId: String
        val itemId: String
    }

    /** The occurrence the viewer launched from is still here. */
    data class Exact(
        override val sectionIndex: Int,
        override val itemIndex: Int,
        override val sectionId: String,
        override val itemId: String,
    ) : Located

    /**
     * The occurrence is gone. This is the closest surviving position — what
     * the viewer would reasonably expect under the cursor instead.
     */
    data class Nearest(
        override val sectionIndex: Int,
        override val itemIndex: Int,
        override val sectionId: String,
        override val itemId: String,
    ) : Located

    /**
     * Not found, but its absence is not authoritative — something that could
     * still produce it has not finished loading. Keep the target and ask again;
     * do not consume it.
     *
     * Two caller obligations come with this, and neither can live in the
     * resolver. It must *drive* the loading it is waiting on, because a
     * demand-paged surface that only waits will wait forever. And it must bound
     * the wait, then re-ask with `treatAbsenceAsFinal = true`, because pages can
     * fail, `hasMore` can stay stuck true, and an unbounded wait leaves focus
     * unrestored — the dead D-pad this campaign exists to remove.
     */
    data object Pending : TvReturnResolution

    /** There is nothing focusable to return to. */
    data object Empty : TvReturnResolution
}

/**
 * Resolve a recorded [target] against the content as it stands now.
 *
 * Preference order:
 *
 * 1. The same occurrence — same section identity, same item. Matched on
 *    identity rather than index, so a reordered feed or a re-sorted grid still
 *    finds it.
 * 2. The same item in another section, only when [relocation] allows it.
 * 3. Its old position in its section, if that section survives: whatever took
 *    the slot. Clamped, because it may have been last.
 * 4. The nearest surviving section, if the section went too.
 *
 * Absence is only acted on once it is authoritative — see
 * [TvReturnSection.isComplete], [sectionsComplete] and [treatAbsenceAsFinal].
 * Sections with no items are never chosen, since there is nothing in them to
 * focus.
 *
 * This says *where*, not *when*. A caller still has to scroll the destination
 * into composition, attach a requester, and request focus under the observed
 * policy; the identities in the result are what let it confirm afterwards that
 * it landed on the thing that was resolved, rather than on whatever now
 * occupies those coordinates.
 *
 * Section ids are REQUIRED to be unique and stable within a surface. That is
 * not a stylistic preference: the no-stall rule below reads a present, finished
 * launch section as the final word, which is only sound if no second section
 * could later arrive bearing the same id. A surface that cannot guarantee
 * unique ids must namespace them until it can.
 *
 * Duplicates are nonetheless handled deterministically rather than left to list
 * order, so a data bug degrades predictably instead of moving focus about on
 * every refresh: every namesake is searched, the one nearest the remembered
 * coordinate wins, and within a section a repeated item resolves to the copy
 * nearest the remembered position — an exact tie taking the earlier copy. That
 * is damage control for a shape the contract does not support, and it is only
 * meaningful once the section list is complete.
 */
internal fun resolveTvReturnTarget(
    target: TvReturnTarget?,
    sections: List<TvReturnSection>,
    relocation: TvReturnRelocation = TvReturnRelocation.SameSectionOnly,
    /**
     * Whether [sections] is every section this surface will have.
     *
     * The same problem as [TvReturnSection.isComplete], one level up: a feed
     * still loading its rows is missing the launch row in exactly the way a
     * deleted row is. A per-section flag cannot express that, because a section
     * that has not arrived yet is not in the list to carry one.
     */
    sectionsComplete: Boolean = true,
    /**
     * Stop waiting and answer from what is here.
     *
     * The terminal half of [TvReturnResolution.Pending]. A caller that has
     * exhausted its budget sets this instead of misreporting completeness, so
     * the fallback stays inside the contract rather than being reimplemented,
     * differently, at each of the surfaces.
     */
    treatAbsenceAsFinal: Boolean = false,
): TvReturnResolution {
    if (target == null) return TvReturnResolution.Empty

    // Every section carrying the launch id, not merely the first: a duplicated
    // section id could otherwise shadow the real one with an empty namesake.
    val sameSections = sections.withIndex().filter { it.value.id == target.sectionId }
    val populated = sections.withIndex().filter { it.value.itemIds.isNotEmpty() }

    // 1 — the occurrence, wherever its section has moved to. When a section id
    // appears more than once, the nearest namesake holding the item wins: the
    // same nearest-coordinate policy relocation uses, rather than whichever the
    // list happens to reach first.
    val sameSectionHit = sameSections
        .filter { it.value.itemIds.contains(target.itemId) }
        .minWithOrNull(nearestTo(target.sectionIndex))
    if (sameSectionHit != null) {
        return TvReturnResolution.Exact(
            sectionIndex = sameSectionHit.index,
            itemIndex = sameSectionHit.value.itemIds.nearestIndexOf(target.itemId, target.itemIndex),
            sectionId = sameSectionHit.value.id,
            itemId = target.itemId,
        )
    }

    // 2 — the item elsewhere, for surfaces that have said that is meaningful.
    // Ties go to the section nearest where it was, so a duplicate three rows
    // away does not win over one adjacent.
    if (relocation == TvReturnRelocation.FollowAcrossSections) {
        val relocated = populated
            .filter { it.value.id != target.sectionId && it.value.itemIds.contains(target.itemId) }
            .minWithOrNull(nearestTo(target.sectionIndex))
        if (relocated != null) {
            return TvReturnResolution.Exact(
                sectionIndex = relocated.index,
                itemIndex = relocated.value.itemIds.nearestIndexOf(target.itemId, target.itemIndex),
                sectionId = relocated.value.id,
                itemId = target.itemId,
            )
        }
    }

    // Nothing found. Before falling back, decide whether "not here" is final —
    // consuming the target early strands focus on a stand-in for good.
    if (!treatAbsenceAsFinal && couldStillArrive(sections, sameSections, relocation, sectionsComplete)) {
        return TvReturnResolution.Pending
    }

    if (populated.isEmpty()) return TvReturnResolution.Empty

    // 3 — the slot it used to occupy, in the section that outlived it.
    val survivingSameSection = sameSections
        .filter { it.value.itemIds.isNotEmpty() }
        .minWithOrNull(nearestTo(target.sectionIndex))
    if (survivingSameSection != null) {
        val itemIndex = target.itemIndex.coerceIn(0, survivingSameSection.value.itemIds.lastIndex)
        return TvReturnResolution.Nearest(
            sectionIndex = survivingSameSection.index,
            itemIndex = itemIndex,
            sectionId = survivingSameSection.value.id,
            itemId = survivingSameSection.value.itemIds[itemIndex],
        )
    }

    // 4 — the section is gone or empty. Nearest survivor by the remembered
    // coordinate, then the remembered position inside it.
    val fallbackSection = populated.minWithOrNull(nearestTo(target.sectionIndex))
        ?: return TvReturnResolution.Empty
    val itemIndex = target.itemIndex.coerceIn(0, fallbackSection.value.itemIds.lastIndex)
    return TvReturnResolution.Nearest(
        sectionIndex = fallbackSection.index,
        itemIndex = itemIndex,
        sectionId = fallbackSection.value.id,
        itemId = fallbackSection.value.itemIds[itemIndex],
    )
}

/**
 * Whether anything still loading could yet produce the target.
 *
 * Deliberately policy-sensitive. Waiting on data that could not change the
 * answer is not caution, it is a stall: under [TvReturnRelocation.SameSectionOnly]
 * a present, finished launch section has already settled the question, and
 * sections yet to load are irrelevant to it.
 */
private fun couldStillArrive(
    sections: List<TvReturnSection>,
    sameSections: List<IndexedValue<TvReturnSection>>,
    relocation: TvReturnRelocation,
    sectionsComplete: Boolean,
): Boolean = when (relocation) {
    // Any section still filling could carry the item, and a section not yet
    // loaded could arrive carrying it.
    TvReturnRelocation.FollowAcrossSections -> !sectionsComplete || sections.any { !it.isComplete }
    // Only the launch section can answer. If it is here, its own pages decide
    // and sections yet to load are irrelevant — which relies on section ids
    // being unique, as the contract requires. If it is absent, it may still be
    // on its way.
    TvReturnRelocation.SameSectionOnly ->
        if (sameSections.isEmpty()) !sectionsComplete else sameSections.any { !it.value.isComplete }
}

/**
 * Index of [itemId], preferring the copy closest to [preferredIndex].
 *
 * Ids are expected to be unique within a section, but a feed that repeats one
 * should land the viewer near where they were rather than at whichever copy
 * comes first.
 */
private fun List<String>.nearestIndexOf(itemId: String, preferredIndex: Int): Int =
    withIndex()
        .filter { it.value == itemId }
        .minByOrNull { abs(it.index - preferredIndex) }
        ?.index
        ?: -1

/**
 * Closest to [sectionIndex], preferring the later section when two are equally
 * close.
 *
 * A forward bias, chosen because moving focus backwards lands the viewer in
 * content they have already scrolled past.
 *
 * Not "the row that slid up": for the positional fallback a removed section
 * makes its successor land at distance zero, which is no tie at all, so the tie
 * there is only reachable around a section that is present but empty. The other
 * callers can tie for their own reasons — two equidistant namesakes, or two
 * equidistant relocation candidates — and the same bias applies to them.
 */
private fun nearestTo(sectionIndex: Int): Comparator<IndexedValue<TvReturnSection>> =
    compareBy(
        { candidate -> abs(candidate.index - sectionIndex) },
        { candidate -> if (candidate.index >= sectionIndex) 0 else 1 },
    )
