package org.siloserver.silo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.model.personal.Collection
import org.siloserver.silo.model.personal.CollectionGroup
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.repository.CollectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A flat rendering item — either a named group header or the anonymous
 * "Ungrouped" header — paired with the collections it contains. Mirrors
 * the buildGroupedSections helper used by the web frontend.
 */
data class CollectionSection(
    /** Null when this section is the anonymous "Ungrouped" bucket. */
    val groupId: String?,
    val name: String,
    val collections: List<Collection>,
)

data class CollectionsUiState(
    val collections: List<Collection> = emptyList(),
    val groups: List<CollectionGroup> = emptyList(),
    /** Cached output of [buildCollectionSections]; refreshed whenever
     *  [collections] or [groups] change so Compose reads are constant-time. */
    val sections: List<CollectionSection> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val showCreateSheet: Boolean = false,
    val createName: String = "",
    val createType: String = "manual",
    val isCreating: Boolean = false,
    val createError: String? = null,
    val groupAction: GroupAction? = null,
    val isGroupBusy: Boolean = false,
    val groupError: String? = null,
)

/**
 * Builds the same ordered list of sections that the web frontend
 * renders: named groups sorted by sort_order, followed by a single
 * "Ungrouped" bucket. Hidden sections with no collections are kept so
 * the UI can still render an empty-state target.
 */
internal fun buildCollectionSections(
    collections: List<Collection>,
    groups: List<CollectionGroup>,
): List<CollectionSection> {
    val byGroup = collections.groupBy { it.groupId }
    val sorted = groups.sortedWith(compareBy({ it.sortOrder }, { it.name }))
    val named = sorted.map { g ->
        CollectionSection(
            groupId = g.id,
            name = g.name,
            collections = (byGroup[g.id] ?: emptyList())
                .sortedBy { it.sortOrder },
        )
    }
    val ungrouped = (byGroup[null] ?: emptyList()).sortedBy { it.sortOrder }
    // Hide an empty Ungrouped bucket whenever at least one named group
    // exists — matches both the iOS `rebuildSections` and the web app.
    if (ungrouped.isEmpty() && named.isNotEmpty()) return named
    return named + CollectionSection(
        groupId = null,
        name = "Ungrouped",
        collections = ungrouped,
    )
}

sealed class GroupAction {
    data object Create : GroupAction()
    data class Rename(val group: CollectionGroup) : GroupAction()
    data class Delete(val group: CollectionGroup) : GroupAction()
    data class Move(val collection: Collection) : GroupAction()
}

class CollectionsViewModel(
    private val collectionRepository: CollectionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    init {
        loadCollections()
    }

    fun loadCollections() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            applyResult(collectionRepository.listCollections(), refreshing = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            applyResult(collectionRepository.listCollections(), refreshing = true)
        }
    }

    private fun applyResult(
        result: ApiResult<org.siloserver.silo.model.personal.CollectionsResponse>,
        refreshing: Boolean,
    ) {
        when (result) {
            is ApiResult.Success -> _uiState.update {
                it.copy(
                    collections = result.data.collections,
                    groups = result.data.groups,
                    sections = buildCollectionSections(result.data.collections, result.data.groups),
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                )
            }
            is ApiResult.Error -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = if (!refreshing) result.message.ifBlank { "Failed to load collections" } else it.error,
                )
            }
            is ApiResult.NetworkError -> _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = if (!refreshing) "Network error. Please check your connection." else it.error,
                )
            }
        }
    }

    fun showCreateSheet() {
        _uiState.update {
            it.copy(showCreateSheet = true, createName = "", createType = "manual", createError = null)
        }
    }

    fun hideCreateSheet() {
        _uiState.update { it.copy(showCreateSheet = false, createError = null) }
    }

    fun onCreateNameChanged(name: String) {
        _uiState.update { it.copy(createName = name, createError = null) }
    }

    fun onCreateTypeChanged(type: String) {
        _uiState.update { it.copy(createType = type) }
    }

    fun createCollection(name: String? = null) {
        val current = _uiState.value
        val collectionName = name ?: current.createName
        if (collectionName.isBlank()) {
            _uiState.update { it.copy(createError = "Name is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, createError = null) }
            when (val result = collectionRepository.createCollection(
                name = collectionName.trim(),
                collectionType = current.createType,
            )) {
                is ApiResult.Success -> {
                    _uiState.update {
                        val updated = it.collections + result.data
                        it.copy(
                            collections = updated,
                            sections = buildCollectionSections(updated, it.groups),
                            isCreating = false,
                            showCreateSheet = false,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isCreating = false, createError = result.message.ifBlank { "Failed to create collection" })
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(isCreating = false, createError = "Network error")
                    }
                }
            }
        }
    }

    fun deleteCollection(id: String) {
        viewModelScope.launch {
            when (val result = collectionRepository.deleteCollection(id)) {
                is ApiResult.Success -> _uiState.update { state ->
                    val updated = state.collections.filter { it.id != id }
                    state.copy(
                        collections = updated,
                        sections = buildCollectionSections(updated, state.groups),
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(error = result.message.ifBlank { "Failed to delete collection" })
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(error = "Network error. Please check your connection.")
                }
            }
        }
    }

    // --- Group actions ---

    fun openGroupAction(action: GroupAction) {
        _uiState.update { it.copy(groupAction = action, groupError = null) }
    }

    fun dismissGroupAction() {
        _uiState.update { it.copy(groupAction = null, groupError = null, isGroupBusy = false) }
    }

    fun createGroup(name: String) {
        if (name.isBlank()) {
            _uiState.update { it.copy(groupError = "Name is required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGroupBusy = true, groupError = null) }
            when (val result = collectionRepository.createGroup(name.trim())) {
                is ApiResult.Success -> {
                    _uiState.update {
                        val updated = it.groups + result.data
                        it.copy(
                            groups = updated,
                            sections = buildCollectionSections(it.collections, updated),
                            isGroupBusy = false,
                            groupAction = null,
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isGroupBusy = false, groupError = result.message.ifBlank { "Failed to add group" })
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isGroupBusy = false, groupError = "Network error")
                }
            }
        }
    }

    fun renameGroup(groupId: String, name: String) {
        if (name.isBlank()) {
            _uiState.update { it.copy(groupError = "Name is required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGroupBusy = true, groupError = null) }
            when (val result = collectionRepository.renameGroup(groupId, name.trim())) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        val updated = state.groups.map { if (it.id == groupId) result.data else it }
                        state.copy(
                            groups = updated,
                            sections = buildCollectionSections(state.collections, updated),
                            isGroupBusy = false,
                            groupAction = null,
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isGroupBusy = false, groupError = result.message.ifBlank { "Failed to rename group" })
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isGroupBusy = false, groupError = "Network error")
                }
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGroupBusy = true, groupError = null) }
            when (val result = collectionRepository.deleteGroup(groupId)) {
                is ApiResult.Success -> _uiState.update { state ->
                    // Collections in the deleted group fall back to Ungrouped.
                    val updatedCollections = state.collections.map {
                        if (it.groupId == groupId) it.copy(groupId = null) else it
                    }
                    val updatedGroups = state.groups.filter { it.id != groupId }
                    state.copy(
                        groups = updatedGroups,
                        collections = updatedCollections,
                        sections = buildCollectionSections(updatedCollections, updatedGroups),
                        isGroupBusy = false,
                        groupAction = null,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isGroupBusy = false, groupError = result.message.ifBlank { "Failed to delete group" })
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isGroupBusy = false, groupError = "Network error")
                }
            }
        }
    }

    /** Moves a collection into a different group (or null = Ungrouped). */
    fun moveCollection(collectionId: String, targetGroupId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGroupBusy = true, groupError = null) }
            when (val result = collectionRepository.moveCollectionToGroup(collectionId, targetGroupId)) {
                is ApiResult.Success -> _uiState.update { state ->
                    val updated = state.collections.map {
                        if (it.id == collectionId) result.data else it
                    }
                    state.copy(
                        collections = updated,
                        sections = buildCollectionSections(updated, state.groups),
                        isGroupBusy = false,
                        groupAction = null,
                    )
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isGroupBusy = false, groupError = result.message.ifBlank { "Failed to move collection" })
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isGroupBusy = false, groupError = "Network error")
                }
            }
        }
    }
}
