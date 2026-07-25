package org.prairieserver.prairie.tv.ui.shell

import org.prairieserver.prairie.model.personal.UserLibrary
import org.prairieserver.prairie.tv.ui.navigation.TvMainRoute

/**
 * Skyline content-type-first shell (§3.1): a fixed root order of `Home`, then
 * one tab per [TvLibraryTabType] the profile can actually see (a library of
 * that type exists), then `Calendar`. Search and For You are no longer tabs —
 * Search is a trailing icon button and For You is reached as a Home row.
 *
 * Mirrors tvOS `TVMainTabView.visibleRoots`.
 */
fun visibleTvRoots(
    libraries: List<UserLibrary>,
    /** tvOS navPrefs.showAudiobooks parity: the Audiobooks tab is opt-in
     *  (hidden by default) even when an audiobook library exists. */
    showAudiobooks: Boolean = false,
): List<TvRootDestination> = buildList {
    add(TvRootDestination.Home)
    TvLibraryTabType.entries
        .filter { type -> libraries.any { type.matches(it) } }
        .filter { type -> type != TvLibraryTabType.Audiobooks || showAudiobooks }
        .forEach { type -> add(TvRootDestination.LibraryType(type)) }
    // tvOS root order: libraries, then For You, then Calendar.
    add(TvRootDestination.ForYou)
    add(TvRootDestination.Calendar)
}

fun firstTvRoute(): String = TvMainRoute.Home.route

fun TvRootDestination.isVisibleIn(destinations: List<TvRootDestination>): Boolean =
    this in destinations
