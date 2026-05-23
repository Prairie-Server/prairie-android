package com.continuum.app.android.ui.screens.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.android.ui.components.ContinuumTopBar
import com.continuum.app.android.ui.components.EmptyStateView
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.components.LoadingIndicator
import com.continuum.app.android.ui.components.MediaCard
import com.continuum.app.model.section.LibraryCollection
import com.continuum.app.model.section.LibraryCollectionsResponse
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.SectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * One named or anonymous section as it should appear on screen, in the
 * order computed from `sort_order`. Mirrors `buildRenderOrder` on the web.
 */
data class LibraryCollectionSection(
    /** Display name. Empty for the anonymous Ungrouped bucket. */
    val name: String,
    val collections: List<LibraryCollection>,
)

data class LibraryCollectionsUiState(
    val sections: List<LibraryCollectionSection> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = sections.all { it.collections.isEmpty() }
}

class LibraryCollectionsViewModel(
    private val sectionRepository: SectionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val libraryId: Int? = savedStateHandle.get<String>("libraryId")?.toIntOrNull()
    private val _uiState = MutableStateFlow(LibraryCollectionsUiState())
    val uiState: StateFlow<LibraryCollectionsUiState> = _uiState.asStateFlow()

    init {
        loadCollections()
    }

    fun loadCollections() {
        val currentLibraryId = libraryId ?: run {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = "Missing library selection",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = false,
                    error = null,
                )
            }
            applyResult(sectionRepository.getLibraryCollectionsGrouped(currentLibraryId))
        }
    }

    fun refresh() {
        val currentLibraryId = libraryId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            applyResult(sectionRepository.getLibraryCollectionsGrouped(currentLibraryId))
        }
    }

    private fun applyResult(result: ApiResult<LibraryCollectionsResponse>) {
        when (result) {
            is ApiResult.Success -> _uiState.update {
                it.copy(
                    sections = buildSections(result.data),
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                )
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = result.message.ifBlank { "Failed to load collections" },
                )
            }
            is ApiResult.NetworkError -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = "Network error: ${result.exception.message ?: "unknown"}",
                )
            }
        }
    }

    /**
     * Builds the ordered render list: each group becomes a section in
     * `sort_order` order, and the ungrouped bucket is woven in at its own
     * `sort_order` slot. When the response is flat (no groups), a single
     * anonymous section is produced.
     */
    private fun buildSections(response: LibraryCollectionsResponse): List<LibraryCollectionSection> {
        val groups = response.groups
        val ungrouped = response.ungrouped

        if (groups.isEmpty() && ungrouped == null) {
            return if (response.collections.isEmpty()) {
                emptyList()
            } else {
                listOf(LibraryCollectionSection(name = "", collections = response.collections))
            }
        }

        data class Slot(val order: Int, val section: LibraryCollectionSection)
        val slots = mutableListOf<Slot>()
        for (group in groups) {
            if (group.collections.isEmpty()) continue
            slots += Slot(
                order = group.sortOrder,
                section = LibraryCollectionSection(
                    name = group.name,
                    collections = group.collections,
                ),
            )
        }
        if (ungrouped != null && ungrouped.collections.isNotEmpty()) {
            slots += Slot(
                order = ungrouped.sortOrder,
                section = LibraryCollectionSection(
                    name = "",
                    collections = ungrouped.collections,
                ),
            )
        }
        return slots.sortedBy { it.order }.map { it.section }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCollectionsScreen(
    onBackClick: () -> Unit,
    onCollectionClick: (String) -> Unit,
    viewModel: LibraryCollectionsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    androidx.compose.material3.Scaffold(
        topBar = {
            ContinuumTopBar(
                title = "Collections",
                onBackClick = onBackClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> {
                LoadingIndicator(modifier = Modifier.padding(padding))
            }
            state.error != null && state.isEmpty -> {
                ErrorView(
                    message = state.error ?: "Unknown error",
                    onRetry = viewModel::loadCollections,
                    modifier = Modifier.padding(padding),
                )
            }
            state.isEmpty -> {
                EmptyStateView(
                    title = "No collections found",
                    subtitle = "This library does not have any collections yet",
                    icon = Icons.Outlined.CollectionsBookmark,
                    modifier = Modifier.padding(padding),
                )
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 132.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        state.sections.forEachIndexed { index, section ->
                            if (section.name.isNotEmpty()) {
                                item(
                                    key = "header:$index",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    Text(
                                        text = section.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp),
                                    )
                                }
                            }
                            items(
                                items = section.collections,
                                key = { c -> "${section.name}:${c.id}" },
                            ) { collection ->
                                LibraryCollectionCard(
                                    collection = collection,
                                    onClick = { onCollectionClick(collection.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCollectionCard(
    collection: LibraryCollection,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                MediaCard(
                    title = collection.name,
                    posterUrl = collection.posterUrl,
                    posterThumbhash = collection.posterThumbhash,
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    width = 160.dp,
                )
            }
            val footerLabel = when {
                collection.kind == "user_collections" -> "User collection"
                collection.itemCount != null -> "${collection.itemCount} items"
                else -> "Collection"
            }
            Text(
                text = footerLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}
