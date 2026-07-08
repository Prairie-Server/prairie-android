package org.siloserver.silo.tv.ui.shell

import org.siloserver.silo.model.personal.UserLibrary
import org.siloserver.silo.tv.ui.navigation.TvMainRoute

/**
 * Skyline content-type-first shell (§3.1): a fixed root order of `Home`, then
 * one tab per [TvLibraryTabType] the profile can actually see (a library of
 * that type exists), then `Calendar`. Search and For You are no longer tabs —
 * Search is a trailing icon button and For You is reached as a Home row.
 *
 * Mirrors tvOS `TVMainTabView.visibleRoots`.
 */
fun visibleTvRoots(libraries: List<UserLibrary>): List<TvRootDestination> = buildList {
    add(TvRootDestination.Home)
    TvLibraryTabType.entries
        .filter { type -> libraries.any { type.matches(it) } }
        .forEach { type -> add(TvRootDestination.LibraryType(type)) }
    // tvOS root order: libraries, then For You, then Calendar.
    add(TvRootDestination.ForYou)
    add(TvRootDestination.Calendar)
}

fun firstTvRoute(): String = TvMainRoute.Home.route

fun TvRootDestination.isVisibleIn(destinations: List<TvRootDestination>): Boolean =
    this in destinations
