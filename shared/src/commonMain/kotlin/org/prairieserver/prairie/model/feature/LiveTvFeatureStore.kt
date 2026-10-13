package org.prairieserver.prairie.model.feature

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.LiveTvRepository

/**
 * Server-gated Live TV capability.
 *
 * Entry points start hidden. A successful `/api/v2/livetv/channels` probe with a
 * nonempty channel list enables the surface. Transient failures keep the previous
 * value, and [reset] hides the surface before a server/profile switch can reuse
 * stale capability state — matching [RequestsFeatureStore].
 */
class LiveTvFeatureStore(
    private val repository: LiveTvRepository,
) {
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    // Written on the UI dispatcher (reset) and read after IO resumption
    // (refresh); Volatile gives the stale-response guard a happens-before.
    @kotlin.concurrent.Volatile
    private var generation: Int = 0

    suspend fun refresh() {
        val refreshGeneration = generation
        when (val result = repository.channels()) {
            is ApiResult.Success -> {
                if (refreshGeneration == generation) {
                    _isEnabled.value = result.data.channels.isNotEmpty()
                }
            }
            // Live TV switched off server-side: hide the surface.
            is ApiResult.Error -> if (
                result.code == 409 && result.error == "capability_not_configured" &&
                refreshGeneration == generation
            ) {
                _isEnabled.value = false
            }
            is ApiResult.NetworkError -> Unit
        }
    }

    fun reset() {
        generation += 1
        _isEnabled.value = false
        repository.reset()
    }
}
