package org.prairieserver.prairie.tv.ui.focus

import org.prairieserver.prairie.model.section.ResolvedSection

/**
 * Projects a Skyline row feed into the shape [resolveTvReturnTarget] reads.
 *
 * Completeness asks one question: can more items still arrive in this row? Not
 * whether more exist somewhere. The two look alike and pull in opposite
 * directions, and a Skyline feed contains both cases:
 *
 * - A row with cards is **complete**, even when `totalCount` runs to hundreds.
 *   Such a row is *capped* — the server trims it to `itemLimit` — and nothing
 *   will ever fetch the remainder. Calling it incomplete would park every
 *   unresolved return in [TvReturnResolution.Pending] forever, waiting on a
 *   page no one requests.
 * - A row with no cards but a non-zero `totalCount` is **incomplete**. That is
 *   not an empty row, it is a placeholder: `hydrateHomeSections` fetches those
 *   separately and fills them in. Calling it complete would read "not fetched
 *   yet" as "nothing here" and spend the return target on a fallback moments
 *   before the real row appeared.
 * - A row with no cards and no total is genuinely empty, and complete.
 *
 * The caller supplies the matching container-level answer by passing the
 * hydration's `fullyResolved` as `sectionsComplete`, which covers rows that had
 * not arrived at all.
 *
 * Project the list the feed actually RENDERS, not an upstream one. Skyline
 * drops empty rows before laying them out, so a projection taken from further
 * up carries sections the feed never shows and puts every resolved index in a
 * different coordinate space from the rows they are meant to address.
 */
internal fun List<ResolvedSection>.toTvReturnSections(): List<TvReturnSection> =
    map { section ->
        TvReturnSection(
            id = section.id,
            itemIds = section.items.map { it.contentId },
            isComplete = section.items.isNotEmpty() || section.totalCount == 0,
        )
    }

/**
 * Projects a flat, paginated surface — a library grid, personal list, people or
 * collection — into the shape [resolveTvReturnTarget] reads.
 *
 * One implicit section, and [hasMore] is the honest answer to the question
 * completeness asks. These surfaces load a page at a time, so an item further
 * in is absent from what is loaded in exactly the way a deleted item is;
 * reporting complete while pages remain spends the return target on whichever
 * card happens to be nearby.
 *
 * The caller that receives [TvReturnResolution.Pending] owes two things: ask
 * for the next page, and bound the hunt — by a clock, a request ceiling, or
 * both — then re-ask with `treatAbsenceAsFinal`. A surface that only waits
 * waits forever.
 */
internal fun flatTvReturnSections(
    itemIds: List<String>,
    hasMore: Boolean,
): List<TvReturnSection> = listOf(
    TvReturnSection(id = TvFlatSectionId, itemIds = itemIds, isComplete = !hasMore),
)
