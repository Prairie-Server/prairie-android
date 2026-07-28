package org.siloserver.silo.tv.ui.shell

internal suspend fun requestTopMenuFocusUntilApplied(
    awaitFrame: suspend () -> Unit,
    requestFocus: () -> Boolean,
) {
    do {
        awaitFrame()
    } while (!requestFocus())
}
