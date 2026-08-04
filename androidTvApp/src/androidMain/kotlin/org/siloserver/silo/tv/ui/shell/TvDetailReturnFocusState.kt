package org.siloserver.silo.tv.ui.shell

internal data class HomeDetailReturnFocusState(
    val requestId: Int = 0,
    val needsRetry: Boolean = false,
    val fallbackPending: Boolean = false,
)

internal fun beginHomeDetailReturnRetry(
    previousRequestId: Int,
    needsRetry: Boolean,
): HomeDetailReturnFocusState = HomeDetailReturnFocusState(
    requestId = previousRequestId + 1,
    needsRetry = needsRetry,
    fallbackPending = needsRetry,
)

internal fun completeHomeDetailReturnRetry(
    state: HomeDetailReturnFocusState,
): HomeDetailReturnFocusState = state.copy(
    needsRetry = false,
    fallbackPending = false,
)

internal fun resetHomeDetailReturnFocus(): HomeDetailReturnFocusState =
    HomeDetailReturnFocusState()
