package org.prairieserver.prairie.android.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Prairie-only: the profile-menu "Live TV" action, or null when the active
 * server has no Live TV channels (see `LiveTvFeatureStore`).
 *
 * `MainScreen` provides it once around the shell and [ProfileMenu] reads it as
 * the default for its `onLiveTvClick` parameter. Using a local instead of a
 * parameter threaded through Home, Libraries, Calendar and the shared top bar
 * keeps the Prairie wiring to two files, so an upstream sync that rewrites
 * those screens cannot silently drop the entry point again (an upstream sync
 * did exactly that once). Anchored in scripts/prairie-invariants.txt.
 */
val LocalLiveTvMenuAction = compositionLocalOf<(() -> Unit)?> { null }
