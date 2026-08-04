package org.siloserver.silo.tv.ui.focus

/**
 * Which profile should own focus after the profile list changes.
 *
 * The screen reloads on every resume, so "the list changed" is the normal case,
 * not an exceptional one — returning from an edit, deleting a tile in manage
 * mode, or simply coming back to the screen all produce a new list. Anchoring
 * unconditionally on the first tile therefore overrode the viewer's position
 * every time.
 *
 * [previousIds] and [currentIds] are profile IDs in display order.
 * [focusedId] is the profile that owned focus before the change, if any.
 *
 * Returns the profile ID to focus, or `null` to leave focus alone — which is
 * what an already-correct focus position or an empty list both call for.
 */
internal fun tvProfileFocusTarget(
    previousIds: List<String>,
    currentIds: List<String>,
    focusedId: String?,
    hasMaterialized: Boolean,
): String? {
    if (currentIds.isEmpty()) return null

    // First time the list has ever arrived: anchor so the D-pad has a home.
    if (!hasMaterialized) return currentIds.first()

    // Nothing was focused here — a refresh must not seize focus from wherever
    // the viewer actually is, which may be another part of the screen entirely.
    val focused = focusedId ?: return null

    // Still present, possibly at a new index: it keeps focus, and the caller
    // re-requests it so a reordered tile carries focus with it.
    if (focused in currentIds) return focused

    // Deleted. Fall to the tile that took its place, or the last one if it was
    // the tail. Landing on the first tile after deleting the fourth is the
    // jump this whole function exists to avoid.
    val removedIndex = previousIds.indexOf(focused)
    if (removedIndex < 0) return null
    return currentIds[removedIndex.coerceAtMost(currentIds.lastIndex)]
}
