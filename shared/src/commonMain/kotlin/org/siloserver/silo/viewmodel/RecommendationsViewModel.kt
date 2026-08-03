package org.siloserver.silo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.model.recommendation.DiscoverRow
import org.siloserver.silo.model.recommendation.TasteProfile
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.RecommendationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecommendationsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sections: List<ResolvedSection> = emptyList(),
    val tasteProfile: TasteProfile? = null,
    val error: String? = null,
)

class RecommendationsViewModel(
    private val recommendationRepository: RecommendationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationsUiState())
    val uiState: StateFlow<RecommendationsUiState> = _uiState.asStateFlow()

    init {
        loadRecommendations()
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchRecommendations()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            fetchRecommendations()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun fetchRecommendations() {
        val discoverResultDeferred = viewModelScope.async {
            recommendationRepository.getDiscover()
        }
        val tasteProfileResultDeferred = viewModelScope.async {
            recommendationRepository.getTasteProfile()
        }

        val discoverResult = discoverResultDeferred.await()
        val tasteProfile = when (val result = tasteProfileResultDeferred.await()) {
            is ApiResult.Success -> result.data
            else -> null
        }

        when (discoverResult) {
            is ApiResult.Success -> {
                val sections = discoverResult.data.rows.toResolvedSections()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sections = sections,
                        tasteProfile = tasteProfile,
                        error = null,
                    )
                }
            }

            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        tasteProfile = tasteProfile,
                        error = discoverResult.message.ifBlank {
                            "Failed to load recommendations"
                        },
                    )
                }
            }

            is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        tasteProfile = tasteProfile,
                        error = "Network error. Check your connection.",
                    )
                }
            }
        }
    }

}

internal fun List<DiscoverRow>.toResolvedSections(): List<ResolvedSection> =
    map(DiscoverRow::toResolvedSection)
        .filter { it.items.isNotEmpty() }
        .sortedByDescending { it.title.equals("For You", ignoreCase = true) }

private fun DiscoverRow.toResolvedSection(): ResolvedSection = ResolvedSection(
    id = stableSectionId(),
    sectionType = type,
    title = label,
    itemLimit = items.size,
    totalCount = items.size,
    items = items,
)

private fun DiscoverRow.stableSectionId(): String =
    sectionKind?.takeIf { it.isNotBlank() }?.let { kind ->
        "discover:kind=$kind:key=${sectionKey.orEmpty()}"
    } ?: "discover:type=$type:label=$label"
