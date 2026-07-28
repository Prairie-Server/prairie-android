package org.siloserver.silo.tv.ui.shell

private const val TopMenuFocusMaxAttempts = 6

internal fun isTopMenuFocusTargetAvailable(
    target: TvTopMenuPanel?,
    destinations: List<TvRootDestination>,
): Boolean = when (target) {
    is TvTopMenuPanel.Root -> target.dest in destinations
    TvTopMenuPanel.Profile, null -> true
}

internal suspend fun requestTopMenuFocusUntilApplied(
    awaitFrame: suspend () -> Unit,
    isTargetCurrent: () -> Boolean = { true },
    requestFocus: () -> Boolean,
) {
    repeat(TopMenuFocusMaxAttempts) {
        if (!isTargetCurrent()) return
        awaitFrame()
        if (!isTargetCurrent()) return
        if (requestFocus()) return
    }
}
