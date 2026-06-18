package com.continuum.app.tv.ui.shell

import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.tv.ui.navigation.TvMainRoute

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
    add(TvRootDestination.Calendar)
}

fun firstTvRoute(): String = TvMainRoute.Home.route

fun TvRootDestination.isVisibleIn(destinations: List<TvRootDestination>): Boolean =
    this in destinations
