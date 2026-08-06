package org.siloserver.silo.android.ui.screens.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel
import org.siloserver.silo.android.ui.components.LoadingIndicator

/**
 * Re-evaluates the acting-admin gate at the DESTINATION, not just at the entry
 * that offered it.
 *
 * Gating only the menu row is not enough: the route stays registered, so
 * restored navigation, a back-stack replay, or any future deep link reaches the
 * screen directly — and an admin screen calls its API as soon as it composes.
 * A gate that can be walked around is a gate in name only.
 *
 * This does NOT make the client a security boundary; only the server can be
 * that, and the client cannot prove what the server enforces. It closes the
 * client-side hole so that being refused is the default rather than a
 * consequence of having arrived by the expected path.
 */
@Composable
fun AdminRouteGate(
    viewModel: AdminEntryViewModel = koinViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    when {
        // Nothing is shown while the gate is still resolving. Rendering the
        // screen first and revoking it after would have already fired the
        // admin API call this exists to prevent.
        state.isLoading -> LoadingIndicator()
        state.isAdminVisible -> content()
        else -> NotAuthorized()
    }
}
