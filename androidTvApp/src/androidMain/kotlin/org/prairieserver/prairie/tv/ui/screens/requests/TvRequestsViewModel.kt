package org.prairieserver.prairie.tv.ui.screens.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.model.request.CreateMediaRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.errorMessage
import org.prairieserver.prairie.repository.RequestsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns request-creation for the TV Requests discover/search screen.
 *
 * The confirm dialog used to fire `repository.create` from the composable's
 * [androidx.compose.runtime.rememberCoroutineScope], which is cancelled the
 * moment the screen leaves composition — so tapping "My Requests" (or Back)
 * right after confirming silently cancelled the in-flight POST and dropped the
 * result state with the composition. Running it in [viewModelScope] keeps the
 * create alive across the natural forward navigation to My Requests, and the
 * outcome is published in [actionState] as durable state the screen observes
 * instead of composition-local `remember`s. Mirrors the shared
 * [org.prairieserver.prairie.viewmodel.RequestDetailViewModel.submitRequest] pattern
 * the phone routes creation through.
 */
data class TvRequestsActionState(
    val submittingKey: String? = null,
    val message: String? = null,
    val error: String? = null,
    val successGeneration: Int = 0,
)

class TvRequestsViewModel(
    private val repository: RequestsRepository,
) : ViewModel() {

    private val _actionState = MutableStateFlow(TvRequestsActionState())
    val actionState: StateFlow<TvRequestsActionState> = _actionState.asStateFlow()

    /**
     * Submits [request], guarding against double-submit while any create is in
     * flight. [requestKey] identifies the card so the row can render its
     * submitting overlay; on success [TvRequestsActionState.successGeneration]
     * ticks so the screen can refresh discover/search to pick up the new status.
     */
    fun submit(requestKey: String, request: CreateMediaRequest) {
        if (_actionState.value.submittingKey != null) return
        _actionState.update {
            it.copy(submittingKey = requestKey, message = null, error = null)
        }
        viewModelScope.launch {
            when (val result = repository.create(request)) {
                is ApiResult.Success -> {
                    _actionState.update {
                        it.copy(
                            submittingKey = null,
                            message = "Request submitted.",
                            error = null,
                            successGeneration = it.successGeneration + 1,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _actionState.update {
                        it.copy(
                            submittingKey = null,
                            error = result.errorMessage("Failed to submit request"),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _actionState.update {
                        it.copy(
                            submittingKey = null,
                            error = "Network error. Check your connection.",
                        )
                    }
                }
            }
        }
    }

    /** Clears any surfaced success/error notice (e.g. on manual refresh). */
    fun clearNotices() {
        _actionState.update { it.copy(message = null, error = null) }
    }

    /**
     * Clears only the success notice, keeping any error visible. Called when
     * the screen detects an already-consumed success on re-entry so a stale
     * "Request submitted." doesn't resurface.
     */
    fun consumeSuccessNotice() {
        _actionState.update { it.copy(message = null) }
    }
}
