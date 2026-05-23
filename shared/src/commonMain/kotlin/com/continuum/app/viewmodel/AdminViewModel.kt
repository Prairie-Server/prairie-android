package com.continuum.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.admin.AdminStats
import com.continuum.app.model.admin.AdminUser
import com.continuum.app.model.admin.Library
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val stats: AdminStats? = null,
    val users: List<AdminUser> = emptyList(),
    val libraries: List<Library> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val scanningLibraryId: Int? = null,
)

class AdminViewModel(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadAdminData()
    }

    fun loadAdminData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val statsResult = adminRepository.getStats()
            val usersResult = adminRepository.getUsers()
            val librariesResult = adminRepository.getLibraries()

            val stats = (statsResult as? ApiResult.Success)?.data
            val users = (usersResult as? ApiResult.Success)?.data ?: emptyList()
            val libraries = (librariesResult as? ApiResult.Success)?.data ?: emptyList()

            val error = when {
                statsResult is ApiResult.Error -> statsResult.message
                statsResult is ApiResult.NetworkError -> "Network error"
                else -> null
            }

            _uiState.update {
                it.copy(
                    stats = stats,
                    users = users,
                    libraries = libraries,
                    isLoading = false,
                    error = error,
                )
            }
        }
    }

    fun triggerScan(libraryId: Int? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(scanningLibraryId = libraryId ?: -1) }
            adminRepository.triggerScan(libraryId)
            _uiState.update { it.copy(scanningLibraryId = null) }
        }
    }
}
