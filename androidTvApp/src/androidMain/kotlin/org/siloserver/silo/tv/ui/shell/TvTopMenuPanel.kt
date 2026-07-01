package org.siloserver.silo.tv.ui.shell

/**
 * Which top-bar element an anchored Skyline panel is attached to. Mirrors tvOS
 * `TVTopMenuPanel` (`TVTopMenuBar.swift`).
 *
 * Panels are **persistently composed** by the shell (kept in the tree with
 * `alpha = 0f` and focus-blocked when inactive) — never added/removed
 * reactively — so opening/closing one never restructures Compose-for-TV's
 * focus graph. See [org.siloserver.silo.tv.ui.shell.TvMainShell].
 *
 * - [Root] — the cascade scope selector under a library-type tab.
 * - [Profile] — the profile dropdown under the avatar (Stage 5).
 */
sealed class TvTopMenuPanel {
    /** The cascade selector anchored under a library-type tab. */
    data class Root(val dest: TvRootDestination) : TvTopMenuPanel()

    /** The profile dropdown anchored under the avatar. */
    data object Profile : TvTopMenuPanel()
}
