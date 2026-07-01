package org.siloserver.silo.tv.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.model.admin.ScanCancelRequest
import org.siloserver.silo.model.admin.ScanRequest
import org.siloserver.silo.model.personal.UserLibrary
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.errorMessage
import org.siloserver.silo.repository.AdminRepository
import org.siloserver.silo.repository.PersonalDataRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvAdminScansUiState(
    val isLoading: Boolean = true,
    val libraries: List<UserLibrary> = emptyList(),
    /** Library IDs with an in-flight scan or cancel request. */
    val busyLibraryIds: Set<Int> = emptySet(),
    val scanningAll: Boolean = false,
    val error: String? = null,
)

/**
 * TV Admin "Scans" — mirrors the phone `AdminScansViewModel`. Lists the user's
 * libraries and runs per-library scan/cancel + scan-all via [AdminRepository]
 * (the scan endpoints live on the libraries handler server-side). Busy-set
 * tracking disables row buttons while a request is in flight.
 */
class TvAdminScansViewModel(
    private val adminRepository: AdminRepository,
    private val personalDataRepository: PersonalDataRepository,
) : ViewModel() {

    private var loadGeneration = 0
    private val _uiState = MutableStateFlow(TvAdminScansUiState())
    val uiState: StateFlow<TvAdminScansUiState> = _uiState.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    init { load() }

    fun load() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetch(generation)
        }
    }

    fun scanLibrary(id: Int) {
        if (id in _uiState.value.busyLibraryIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(busyLibraryIds = it.busyLibraryIds + id) }
            when (val result = adminRepository.triggerScan(ScanRequest(libraryId = id))) {
                is ApiResult.Success -> {
                    _toasts.emit("Scan started")
                    refresh()
                }
                is ApiResult.Error, is ApiResult.NetworkError ->
                    _toasts.emit(result.errorMessage("Failed to start scan"))
            }
            _uiState.update { it.copy(busyLibraryIds = it.busyLibraryIds - id) }
        }
    }

    fun cancelLibrary(id: Int) {
        if (id in _uiState.value.busyLibraryIds) return
        viewModelScope.launch {
            _uiState.update { it.copy(busyLibraryIds = it.busyLibraryIds + id) }
            when (val result = adminRepository.cancelScan(ScanCancelRequest(libraryId = id))) {
                is ApiResult.Success -> {
                    _toasts.emit("Scan cancelled")
                    refresh()
                }
                is ApiResult.Error, is ApiResult.NetworkError ->
                    _toasts.emit(result.errorMessage("Failed to cancel scan"))
            }
            _uiState.update { it.copy(busyLibraryIds = it.busyLibraryIds - id) }
        }
    }

    fun scanAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(scanningAll = true) }
            when (val result = adminRepository.triggerScan(ScanRequest())) {
                is ApiResult.Success -> {
                    _toasts.emit("Scanning…")
                    refresh()
                }
                is ApiResult.Error, is ApiResult.NetworkError ->
                    _toasts.emit(result.errorMessage("Failed to start scan"))
            }
            _uiState.update { it.copy(scanningAll = false) }
        }
    }

    private fun refresh() {
        val generation = ++loadGeneration
        viewModelScope.launch { fetch(generation) }
    }

    private suspend fun fetch(generation: Int) {
        val result = personalDataRepository.listUserLibraries()
        if (generation != loadGeneration) return
        when (result) {
            is ApiResult.Success -> _uiState.update {
                it.copy(
                    isLoading = false,
                    libraries = result.data.sortedBy { lib -> lib.sortOrder },
                    error = null,
                )
            }
            is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                it.copy(isLoading = false, error = result.errorMessage("Failed to load libraries"))
            }
        }
    }
}
