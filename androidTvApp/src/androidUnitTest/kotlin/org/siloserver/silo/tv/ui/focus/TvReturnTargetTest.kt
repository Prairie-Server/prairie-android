package org.siloserver.silo.tv.ui.focus

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The cases here are the ones saved indices got wrong. Each names the real
 * event that produces it, because "the data changed while you were away" is the
 * normal state of a feed, not an edge case.
 */
class TvReturnTargetTest {

    private val continueWatching = TvReturnSection("continue", listOf("a", "b", "c"))
    private val recentlyAdded = TvReturnSection("recent", listOf("d", "e"))
    private val because = TvReturnSection("because", listOf("f", "g", "h"))
    private val feed = listOf(continueWatching, recentlyAdded, because)

    private fun target(
        sectionId: String = "recent",
        itemId: String = "e",
        sectionIndex: Int = 1,
        itemIndex: Int = 1,
    ) = TvReturnTarget(sectionId, itemId, sectionIndex, itemIndex)

    // ── The occurrence is still there ────────────────────────────────────

    @Test
    fun `an unchanged feed returns to the launch card`() {
        assertEquals(
            TvReturnResolution.Exact(1, 1, "recent", "e"),
            resolveTvReturnTarget(target(), feed),
        )
    }

    @Test
    fun `a feed that reordered its rows follows the row, not the saved index`() {
        // The launch row moves from index 1 to index 2. A resolver trusting the
        // saved index would land in a different row entirely.
        val reordered = listOf(because, continueWatching, recentlyAdded)
        assertEquals(
            TvReturnResolution.Exact(2, 1, "recent", "e"),
            resolveTvReturnTarget(target(), reordered),
        )
    }

    @Test
    fun `items inserted ahead of the launch card do not drag focus along`() {
        val shifted = listOf(
            continueWatching,
            recentlyAdded.copy(itemIds = listOf("new", "d", "e")),
            because,
        )
        assertEquals(
            TvReturnResolution.Exact(1, 2, "recent", "e"),
            resolveTvReturnTarget(target(), shifted),
        )
    }

    @Test
    fun `an item removed from the front of the row shifts the target left`() {
        val shifted = listOf(continueWatching, recentlyAdded.copy(itemIds = listOf("e")), because)
        assertEquals(
            TvReturnResolution.Exact(1, 0, "recent", "e"),
            resolveTvReturnTarget(target(), shifted),
        )
    }

    @Test
    fun `a flat grid resolves by identity after a sort changes the order`() {
        val grid = listOf(TvReturnSection(TvFlatSectionId, listOf("r", "q", "p")))
        val launched = TvReturnTarget(TvFlatSectionId, itemId = "p", sectionIndex = 0, itemIndex = 0)

        assertEquals(
            TvReturnResolution.Exact(0, 2, TvFlatSectionId, "p"),
            resolveTvReturnTarget(launched, grid),
        )
    }

    // ── Duplicates and relocation ────────────────────────────────────────

    @Test
    fun `a duplicate elsewhere is not mistaken for the launch card`() {
        // Feeds routinely carry the same item in several rows. "b" leaves
        // Continue Watching while a copy that was ALWAYS in Recently Added
        // stays put. Jumping to that copy throws focus across the feed to a
        // card the viewer never touched.
        val duplicated = listOf(
            continueWatching.copy(itemIds = listOf("a", "c")),
            recentlyAdded.copy(itemIds = listOf("d", "b", "e")),
            because,
        )
        val launched = target(sectionId = "continue", itemId = "b", sectionIndex = 0, itemIndex = 1)

        assertEquals(
            TvReturnResolution.Nearest(0, 1, "continue", "c"),
            resolveTvReturnTarget(launched, duplicated),
        )
    }

    @Test
    fun `a surface with disjoint rows can opt into following the item`() {
        val moved = listOf(
            continueWatching.copy(itemIds = listOf("a", "c")),
            recentlyAdded,
            because.copy(itemIds = listOf("f", "b", "g", "h")),
        )
        val launched = target(sectionId = "continue", itemId = "b", sectionIndex = 0, itemIndex = 1)

        assertEquals(
            TvReturnResolution.Exact(2, 1, "because", "b"),
            resolveTvReturnTarget(launched, moved, TvReturnRelocation.FollowAcrossSections),
        )
    }

    @Test
    fun `following the item prefers the copy nearest the original row`() {
        // With several copies, display order must not decide: a refresh that
        // merely reorders those rows would change where focus lands.
        val everywhere = listOf(
            TvReturnSection("s0", listOf("x")),
            TvReturnSection("s1", listOf("b")),
            TvReturnSection("gone-from", listOf("y")),
            TvReturnSection("s3", listOf("b")),
        )
        val launched = target(sectionId = "gone-from", itemId = "b", sectionIndex = 2, itemIndex = 0)

        assertEquals(
            TvReturnResolution.Exact(3, 0, "s3", "b"),
            resolveTvReturnTarget(launched, everywhere, TvReturnRelocation.FollowAcrossSections),
        )
    }

    // ── Incomplete data ──────────────────────────────────────────────────

    @Test
    fun `a half-loaded section does not count as proof the item is gone`() {
        // Grids, Search, personal lists, Collections and people all paginate.
        // An item on page four is missing from page one in exactly the way a
        // deleted item is, and consuming the target here strands focus on a
        // stand-in permanently.
        val firstPage = listOf(TvReturnSection(TvFlatSectionId, listOf("p", "q"), isComplete = false))
        val launched = TvReturnTarget(TvFlatSectionId, itemId = "z", sectionIndex = 0, itemIndex = 40)

        assertEquals(TvReturnResolution.Pending, resolveTvReturnTarget(launched, firstPage))
    }

    @Test
    fun `a fully loaded section that lacks the item is proof enough`() {
        val complete = listOf(TvReturnSection(TvFlatSectionId, listOf("p", "q"), isComplete = true))
        val launched = TvReturnTarget(TvFlatSectionId, itemId = "z", sectionIndex = 0, itemIndex = 40)

        assertEquals(
            TvReturnResolution.Nearest(0, 1, TvFlatSectionId, "q"),
            resolveTvReturnTarget(launched, complete),
        )
    }

    @Test
    fun `an empty but still loading surface waits rather than giving up`() {
        val loading = listOf(TvReturnSection("recent", emptyList(), isComplete = false))
        assertEquals(TvReturnResolution.Pending, resolveTvReturnTarget(target(), loading))
    }

    @Test
    fun `a loading sibling does not hold up a section that has finished`() {
        // The launch row is complete and does not list the item, so its absence
        // is already authoritative — another row still loading is irrelevant
        // unless the surface follows items across rows.
        val mixed = listOf(
            continueWatching.copy(isComplete = false),
            recentlyAdded.copy(itemIds = listOf("d"), isComplete = true),
            because,
        )
        assertEquals(
            TvReturnResolution.Nearest(1, 0, "recent", "d"),
            resolveTvReturnTarget(target(), mixed),
        )
    }

    @Test
    fun `a vanished row waits on a loading sibling only when following items`() {
        // With the row gone, a cross-section match is the only thing that could
        // still turn up — so completeness matters only to a surface that would
        // accept one.
        val loading = listOf(continueWatching.copy(isComplete = false), because)
        val launched = target(sectionId = "gone", itemId = "e", sectionIndex = 1, itemIndex = 0)

        assertEquals(
            TvReturnResolution.Pending,
            resolveTvReturnTarget(launched, loading, TvReturnRelocation.FollowAcrossSections),
        )
        assertEquals(
            TvReturnResolution.Nearest(1, 0, "because", "f"),
            resolveTvReturnTarget(launched, loading),
        )
    }

    @Test
    fun `a feed still loading its rows waits before falling back`() {
        // The launch row may simply not have arrived yet. A per-section flag
        // cannot say so — a section that has not loaded is not in the list to
        // carry one — which is why completeness is also asked of the list.
        val partialFeed = listOf(continueWatching)
        assertEquals(
            TvReturnResolution.Pending,
            resolveTvReturnTarget(target(), partialFeed, sectionsComplete = false),
        )
        // Once the feed says that is all the rows there are, the launch row is
        // genuinely gone and the fallback is honest.
        assertEquals(
            TvReturnResolution.Nearest(0, 1, "continue", "b"),
            resolveTvReturnTarget(target(), partialFeed, sectionsComplete = true),
        )
    }

    @Test
    fun `following items waits on any loading row even when the launch row is done`() {
        // The launch row has finished and does not list the item, but a surface
        // that accepts the item from anywhere could still see it arrive in the
        // row that is still filling.
        val mixed = listOf(
            continueWatching.copy(isComplete = false),
            recentlyAdded.copy(itemIds = listOf("d"), isComplete = true),
            because,
        )
        assertEquals(
            TvReturnResolution.Pending,
            resolveTvReturnTarget(target(), mixed, TvReturnRelocation.FollowAcrossSections),
        )
        // A surface that only looks in its own row has its answer already.
        assertEquals(
            TvReturnResolution.Nearest(1, 0, "recent", "d"),
            resolveTvReturnTarget(target(), mixed),
        )
    }

    // ── The occurrence is gone ───────────────────────────────────────────

    @Test
    fun `a removed item leaves focus on whatever took its place`() {
        val without = listOf(continueWatching, recentlyAdded.copy(itemIds = listOf("d", "x")), because)
        assertEquals(
            TvReturnResolution.Nearest(1, 1, "recent", "x"),
            resolveTvReturnTarget(target(), without),
        )
    }

    @Test
    fun `a removed last item falls back inside its row rather than off the end`() {
        val shorter = listOf(continueWatching, recentlyAdded.copy(itemIds = listOf("d")), because)
        assertEquals(
            TvReturnResolution.Nearest(1, 0, "recent", "d"),
            resolveTvReturnTarget(target(), shorter),
        )
    }

    @Test
    fun `a removed row falls back to the row that slid into its place`() {
        val without = listOf(continueWatching, because)
        assertEquals(
            TvReturnResolution.Nearest(1, 1, "because", "g"),
            resolveTvReturnTarget(target(), without),
        )
    }

    @Test
    fun `a removed row with no exact successor picks the nearest by distance`() {
        // The launch row was index 3 of a longer feed; only earlier rows
        // survive, so the fallback has to measure distance rather than reuse
        // the saved index.
        val survivors = listOf(continueWatching, recentlyAdded)
        val launched = target(sectionId = "gone", itemId = "z", sectionIndex = 3, itemIndex = 0)

        assertEquals(
            TvReturnResolution.Nearest(1, 0, "recent", "d"),
            resolveTvReturnTarget(launched, survivors),
        )
    }

    @Test
    fun `an emptied row is never chosen as a fallback`() {
        val emptied = listOf(
            continueWatching,
            recentlyAdded.copy(itemIds = emptyList()),
            because,
        )
        assertEquals(
            TvReturnResolution.Nearest(2, 1, "because", "g"),
            resolveTvReturnTarget(target(), emptied),
        )
    }

    @Test
    fun `a tie between the row above and below goes to the one below`() {
        // Only reachable for an emptied-but-present row: a removed row makes
        // its successor land at distance zero, which is no tie. This is a plain
        // forward bias — moving focus backwards lands the viewer in content
        // they have already scrolled past.
        val emptiedInPlace = listOf(
            continueWatching,
            recentlyAdded.copy(itemIds = emptyList()),
            because,
        )
        assertEquals(
            TvReturnResolution.Nearest(2, 0, "because", "f"),
            resolveTvReturnTarget(target(itemIndex = 0), emptiedInPlace),
        )
    }

    @Test
    fun `an entirely empty feed has nothing to restore`() {
        assertEquals(TvReturnResolution.Empty, resolveTvReturnTarget(target(), emptyList()))
        assertEquals(
            TvReturnResolution.Empty,
            resolveTvReturnTarget(target(), listOf(TvReturnSection("recent", emptyList()))),
        )
    }

    @Test
    fun `no recorded target restores nothing`() {
        assertEquals(TvReturnResolution.Empty, resolveTvReturnTarget(null, feed))
    }

    // ── Surviving process death ──────────────────────────────────────────

    private fun roundTrip(target: TvReturnTarget?): TvReturnTarget? {
        val scope = SaverScope { true }
        val saved = with(TvReturnTargetSaver) { scope.save(target) }
        return saved?.let { TvReturnTargetSaver.restore(it) }
    }

    @Test
    fun `a target survives process death intact`() {
        val launched = target()
        assertEquals(launched, roundTrip(launched))
    }

    @Test
    fun `a flat surface's target survives process death`() {
        val flat = TvReturnTarget(TvFlatSectionId, itemId = "r", sectionIndex = 0, itemIndex = 2)
        assertEquals(flat, roundTrip(flat))
    }

    @Test
    fun `a flat surface restores a surviving item by identity, not by position`() {
        // The defect this replaces: the flat section id used to be null while
        // no section could ever BE null, so a flat surface never matched its
        // own launch section and every surviving item came back as a positional
        // fallback. An earlier version of this test asserted that behaviour and
        // so made the bug look intended.
        val grid = listOf(TvReturnSection(TvFlatSectionId, listOf("r", "q", "p")))
        val launched = TvReturnTarget(TvFlatSectionId, itemId = "q", sectionIndex = 0, itemIndex = 2)

        assertEquals(
            TvReturnResolution.Exact(0, 1, TvFlatSectionId, "q"),
            resolveTvReturnTarget(launched, grid),
        )
    }

    @Test
    fun `no target survives as no target`() {
        assertEquals(null, roundTrip(null))
    }

    // ── Waiting, and stopping waiting ────────────────────────────────────

    @Test
    fun `a finished launch row does not wait on rows that have not loaded`() {
        // Under SameSectionOnly only the launch row can answer, so once it is
        // here and complete the question is settled. Waiting on rows that
        // could not change the answer is a stall, not caution.
        val partial = listOf(recentlyAdded.copy(itemIds = listOf("d"), isComplete = true))
        val launched = target(sectionIndex = 0)

        assertEquals(
            TvReturnResolution.Nearest(0, 0, "recent", "d"),
            resolveTvReturnTarget(launched, partial, sectionsComplete = false),
        )
    }

    @Test
    fun `identity still wins while the surface is loading`() {
        // Finding it ends the question immediately; completeness only governs
        // how absence is read.
        val partial = listOf(recentlyAdded.copy(isComplete = false))
        assertEquals(
            TvReturnResolution.Exact(0, 1, "recent", "e"),
            resolveTvReturnTarget(target(sectionIndex = 0), partial, sectionsComplete = false),
        )
    }

    @Test
    fun `a pending target resolves once the page carrying it arrives`() {
        val launched = TvReturnTarget(TvFlatSectionId, itemId = "z", sectionIndex = 0, itemIndex = 40)
        val firstPage = listOf(TvReturnSection(TvFlatSectionId, listOf("p", "q"), isComplete = false))
        assertEquals(TvReturnResolution.Pending, resolveTvReturnTarget(launched, firstPage))

        val withNextPage = listOf(
            TvReturnSection(TvFlatSectionId, listOf("p", "q", "y", "z"), isComplete = false),
        )
        assertEquals(
            TvReturnResolution.Exact(0, 3, TvFlatSectionId, "z"),
            resolveTvReturnTarget(launched, withNextPage),
        )
    }

    @Test
    fun `a caller that has waited long enough can force an answer`() {
        // The terminal half of Pending. Without it a caller has to either lie
        // about completeness or rebuild the fallback itself at every surface.
        val launched = TvReturnTarget(TvFlatSectionId, itemId = "z", sectionIndex = 0, itemIndex = 40)
        val stillLoading = listOf(TvReturnSection(TvFlatSectionId, listOf("p", "q"), isComplete = false))

        assertEquals(TvReturnResolution.Pending, resolveTvReturnTarget(launched, stillLoading))
        assertEquals(
            TvReturnResolution.Nearest(0, 1, TvFlatSectionId, "q"),
            resolveTvReturnTarget(launched, stillLoading, treatAbsenceAsFinal = true),
        )
    }

    @Test
    fun `forcing an answer on an empty loading surface gives up rather than waiting`() {
        val nothingYet = listOf(TvReturnSection("recent", emptyList(), isComplete = false))
        assertEquals(
            TvReturnResolution.Empty,
            resolveTvReturnTarget(target(), nothingYet, treatAbsenceAsFinal = true),
        )
    }

    // ── Duplicate ids ────────────────────────────────────────────────────

    @Test
    fun `a repeated item within a row resolves to the copy nearest the viewer`() {
        val repeated = listOf(TvReturnSection("recent", listOf("e", "d", "x", "e")))
        assertEquals(
            TvReturnResolution.Exact(0, 3, "recent", "e"),
            resolveTvReturnTarget(target(sectionIndex = 0, itemIndex = 3), repeated),
        )
        assertEquals(
            TvReturnResolution.Exact(0, 0, "recent", "e"),
            resolveTvReturnTarget(target(sectionIndex = 0, itemIndex = 0), repeated),
        )
    }

    @Test
    fun `a duplicated section id does not shadow the row actually holding the item`() {
        // An empty namesake appearing first would otherwise hide the real row
        // and send focus to a positional fallback.
        val shadowed = listOf(
            TvReturnSection("recent", emptyList()),
            TvReturnSection("recent", listOf("d", "e")),
        )
        assertEquals(
            TvReturnResolution.Exact(1, 1, "recent", "e"),
            resolveTvReturnTarget(target(sectionIndex = 0), shadowed),
        )
    }

    @Test
    fun `following the item prefers a nearer copy over a further one`() {
        val everywhere = listOf(
            TvReturnSection("s0", listOf("b")),
            TvReturnSection("s1", listOf("x")),
            TvReturnSection("gone-from", listOf("y")),
            TvReturnSection("s3", listOf("b")),
        )
        val launched = target(sectionId = "gone-from", itemId = "b", sectionIndex = 3, itemIndex = 0)

        // s3 is adjacent to the remembered coordinate; s0 is three rows away.
        assertEquals(
            TvReturnResolution.Exact(3, 0, "s3", "b"),
            resolveTvReturnTarget(launched, everywhere, TvReturnRelocation.FollowAcrossSections),
        )
    }

    // ── Duplicate section ids, disambiguated ─────────────────────────────

    @Test
    fun `the nearest namesake holding the item wins, not the first`() {
        // Matching every namesake but taking the first contradicts the same
        // nearest-coordinate policy used everywhere else, and sends focus to
        // the far end of the feed.
        val namesakes = listOf(
            TvReturnSection("recent", listOf("d", "e")),
            TvReturnSection("filler", listOf("x")),
            TvReturnSection("recent", listOf("d", "e")),
        )
        assertEquals(
            TvReturnResolution.Exact(2, 1, "recent", "e"),
            resolveTvReturnTarget(target(sectionIndex = 2), namesakes),
        )
        assertEquals(
            TvReturnResolution.Exact(0, 1, "recent", "e"),
            resolveTvReturnTarget(target(sectionIndex = 0), namesakes),
        )
    }

    @Test
    fun `the nearest populated namesake wins the positional fallback too`() {
        val namesakes = listOf(
            TvReturnSection("recent", listOf("p")),
            TvReturnSection("filler", listOf("x")),
            TvReturnSection("recent", listOf("q")),
        )
        assertEquals(
            TvReturnResolution.Nearest(2, 0, "recent", "q"),
            resolveTvReturnTarget(target(sectionIndex = 2, itemIndex = 0), namesakes),
        )
    }

    @Test
    fun `a namesake still loading keeps the target waiting`() {
        val namesakes = listOf(
            TvReturnSection("recent", listOf("p"), isComplete = true),
            TvReturnSection("recent", listOf("q"), isComplete = false),
        )
        assertEquals(TvReturnResolution.Pending, resolveTvReturnTarget(target(), namesakes))
    }

    @Test
    fun `equidistant repeated items resolve to the earlier copy`() {
        // Deterministic rather than arbitrary: a refresh must not move focus
        // between two equally close copies.
        val repeated = listOf(TvReturnSection("recent", listOf("e", "x", "e")))
        assertEquals(
            TvReturnResolution.Exact(0, 0, "recent", "e"),
            resolveTvReturnTarget(target(sectionIndex = 0, itemIndex = 1), repeated),
        )
    }

    // ── Forcing an answer never skips one that exists ────────────────────

    @Test
    fun `forcing an answer still returns an exact match that is present`() {
        assertEquals(
            TvReturnResolution.Exact(1, 1, "recent", "e"),
            resolveTvReturnTarget(target(), feed, treatAbsenceAsFinal = true),
        )
    }

    @Test
    fun `forcing an answer still returns a relocated match that is present`() {
        val moved = listOf(
            continueWatching.copy(itemIds = listOf("a", "c")),
            recentlyAdded,
            because.copy(itemIds = listOf("f", "b")),
        )
        val launched = target(sectionId = "continue", itemId = "b", sectionIndex = 0, itemIndex = 1)

        assertEquals(
            TvReturnResolution.Exact(2, 1, "because", "b"),
            resolveTvReturnTarget(
                launched,
                moved,
                TvReturnRelocation.FollowAcrossSections,
                treatAbsenceAsFinal = true,
            ),
        )
    }

    @Test
    fun `following items waits on an unloaded row even when every present row is done`() {
        val allComplete = listOf(continueWatching, because)
        val launched = target(sectionId = "gone", itemId = "e", sectionIndex = 1, itemIndex = 0)

        assertEquals(
            TvReturnResolution.Pending,
            resolveTvReturnTarget(
                launched,
                allComplete,
                TvReturnRelocation.FollowAcrossSections,
                sectionsComplete = false,
            ),
        )
    }

    // ── Heterogeneous surfaces ───────────────────────────────────────────

    @Test
    fun `namespaced identities keep two identifier spaces from colliding`() {
        // Search mixes library results with request-provider results, where a
        // bare "1234" is a content id in one space and a TMDB id in the other.
        // The domains have to be made to COMPETE for this to prove anything:
        // the launch section is gone and relocation is on, so an unnamespaced
        // id would match the unrelated library card and throw focus to it.
        val results = listOf(TvReturnSection("search:library", listOf("catalog:1234")))

        val namespaced = TvReturnTarget("search:requests", "request:movie:1234", 1, 0)
        assertEquals(
            TvReturnResolution.Nearest(0, 0, "search:library", "catalog:1234"),
            resolveTvReturnTarget(namespaced, results, TvReturnRelocation.FollowAcrossSections),
        )

        // The same shape with bare ids resolves as an Exact match on a wholly
        // unrelated item — what the namespacing prevents.
        val collidingResults = listOf(TvReturnSection("search:library", listOf("1234")))
        val bare = TvReturnTarget("search:requests", "1234", 1, 0)
        assertEquals(
            TvReturnResolution.Exact(0, 0, "search:library", "1234"),
            resolveTvReturnTarget(bare, collidingResults, TvReturnRelocation.FollowAcrossSections),
        )
    }

    @Test
    fun `a duplicate section id degrades predictably but is not a supported shape`() {
        // Section ids are required to be unique: the no-stall rule treats a
        // present, finished launch section as final, which only holds if no
        // second section can later arrive bearing the same id. This pins the
        // known consequence rather than pretending it away — with a namesake
        // still unloaded, the present one settles the question and the target
        // is spent.
        val onePresent = listOf(TvReturnSection("recent", listOf("d"), isComplete = true))

        assertEquals(
            TvReturnResolution.Nearest(0, 0, "recent", "d"),
            resolveTvReturnTarget(target(sectionIndex = 0), onePresent, sectionsComplete = false),
        )
    }

    @Test
    fun `equidistant namesakes resolve forward, like every other tie`() {
        val namesakes = listOf(
            TvReturnSection("recent", listOf("d", "e")),
            TvReturnSection("filler", listOf("x")),
            TvReturnSection("recent", listOf("d", "e")),
        )
        // Remembered at index 1, so both namesakes are one row away.
        assertEquals(
            TvReturnResolution.Exact(2, 1, "recent", "e"),
            resolveTvReturnTarget(target(sectionIndex = 1), namesakes),
        )
    }

    @Test
    fun `equidistant populated namesakes fall back forward too`() {
        val namesakes = listOf(
            TvReturnSection("recent", listOf("p")),
            TvReturnSection("filler", listOf("x")),
            TvReturnSection("recent", listOf("q")),
        )
        assertEquals(
            TvReturnResolution.Nearest(2, 0, "recent", "q"),
            resolveTvReturnTarget(target(sectionIndex = 1, itemIndex = 0), namesakes),
        )
    }

    // ── Targets are partitioned by the surface that saved them ───────────

    @Test
    fun `a saved target is discarded when it comes back on a different surface`() {
        // rememberSaveable does not validate restored values against its keys,
        // so a process returning on a different tab would otherwise adopt the
        // previous surface's target and restore focus to somewhere the viewer
        // was in another list entirely.
        val saver = keyedTvReturnTargetSaver("browse")
        val scope = SaverScope { true }
        val saved = with(saver) { scope.save(TvReturnTarget(TvFlatSectionId, "q", 0, 3)) }

        assertEquals(null, keyedTvReturnTargetSaver("genres").restore(saved!!))
        assertEquals(
            TvReturnTarget(TvFlatSectionId, "q", 0, 3),
            keyedTvReturnTargetSaver("browse").restore(saved),
        )
    }

    @Test
    fun `an empty target round-trips as empty on its own surface`() {
        val saver = keyedTvReturnTargetSaver("browse")
        val scope = SaverScope { true }
        val saved = with(saver) { scope.save(null) }
        assertEquals(null, saved?.let { saver.restore(it) })
    }

    @Test
    fun `a right-owner target with malformed fields restores as empty`() {
        assertEquals(
            null,
            keyedTvReturnTargetSaver("browse")
                .restore(listOf("browse", TvFlatSectionId, "q", "zero", 3)),
        )
        assertEquals(
            null,
            keyedTvReturnTargetSaver("browse")
                .restore(listOf("browse", TvFlatSectionId, "q", 0, 3, "extra")),
        )
    }

    @Test
    fun `a saved boolean is discarded for a different owner or slot`() {
        val saver = keyedBooleanSaver("home", slot = "pending")
        val saved = with(saver) { SaverScope { true }.save(true) }

        assertEquals(false, keyedBooleanSaver("library", slot = "pending").restore(saved!!))
        assertEquals(false, keyedBooleanSaver("home", slot = "generation").restore(saved))
        assertEquals(true, keyedBooleanSaver("home", slot = "pending").restore(saved))
    }

    @Test
    fun `a right-owner boolean payload with the wrong type restores its default`() {
        assertEquals(
            false,
            keyedBooleanSaver("home", slot = "pending")
                .restore(listOf("home", "pending", 1)),
        )
    }

    @Test
    fun `a saved int is discarded for a different owner or slot`() {
        val saver = keyedIntSaver("home", slot = "generation")
        val saved = with(saver) { SaverScope { true }.save(7) }

        assertEquals(0, keyedIntSaver("library", slot = "generation").restore(saved!!))
        assertEquals(0, keyedIntSaver("home", slot = "pending").restore(saved))
        assertEquals(7, keyedIntSaver("home", slot = "generation").restore(saved))
    }

    @Test
    fun `a right-owner int payload with the wrong type restores its default`() {
        assertEquals(
            0,
            keyedIntSaver("home", slot = "generation")
                .restore(listOf("home", "generation", true)),
        )
    }
}
