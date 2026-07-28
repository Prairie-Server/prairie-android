package org.siloserver.silo.tv.ui.shell

internal suspend fun requestTopMenuFocusUntilApplied(
    awaitFrame: suspend () -> Unit,
    isTargetCurrent: () -> Boolean = { true },
    requestFocus: () -> Boolean,
) {
    while (isTargetCurrent()) {
        awaitFrame()
        if (!isTargetCurrent()) return
        if (requestFocus()) return
    }
}
