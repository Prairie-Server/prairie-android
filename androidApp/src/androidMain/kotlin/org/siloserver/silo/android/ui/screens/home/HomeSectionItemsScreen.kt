package org.siloserver.silo.android.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.siloserver.silo.android.ui.components.LoadingIndicator
import org.siloserver.silo.android.ui.components.MediaCard
import org.siloserver.silo.android.ui.components.SiloTopBar
import org.siloserver.silo.model.section.SectionItem
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.SectionRepository

/**
 * Full contents of one home section — the "See All" target. Previously
 * See All dropped the section on the floor and navigated to a bare Browse,
 * which had nothing to do with the row that was tapped.
 */
data class HomeSectionItemsUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val items: List<SectionItem> = emptyList(),
    val error: String? = null,
)

class HomeSectionItemsViewModel(
    private val sectionRepository: SectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sectionId: String = savedStateHandle.get<String>("sectionId").orEmpty()

    private val _uiState = MutableStateFlow(HomeSectionItemsUiState())
    val uiState: StateFlow<HomeSectionItemsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (sectionId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Section not found") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = sectionRepository.getHomeSectionItems(sectionId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        title = result.data.section?.title ?: "Section",
                        items = result.data.items,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load section")
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = "Network error. Check your connection.")
                }
            }
        }
    }
}

@Composable
fun HomeSectionItemsScreen(
    onItemClick: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: HomeSectionItemsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            SiloTopBar(title = state.title.ifBlank { "Section" }, onBackClick = onBackClick)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(modifier = Modifier.padding(padding))
            state.error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.error ?: "Something went wrong",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(state.items, key = { it.contentId }) { item ->
                    MediaCard(
                        title = item.title,
                        posterUrl = item.posterUrl,
                        posterThumbhash = item.posterThumbhash,
                        year = item.year,
                        type = item.type,
                        onClick = { onItemClick(item.contentId) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
