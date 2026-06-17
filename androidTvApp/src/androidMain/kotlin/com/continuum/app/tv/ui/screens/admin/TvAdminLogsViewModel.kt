package com.continuum.app.tv.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.admin.AdminAuditEntry
import com.continuum.app.model.admin.AdminLogEntry
import com.continuum.app.network.ApiResult
import com.continuum.app.network.errorMessage
import com.continuum.app.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TvLogTab(val label: String) { App("App"), Audit("Audit") }

data class TvAdminLogsUiState(
    val tab: TvLogTab = TvLogTab.App,
    /** App-log level filter; null = all levels. */
    val level: String? = null,
    val appEntries: List<AdminLogEntry> = emptyList(),
    val auditEntries: List<AdminAuditEntry> = emptyList(),
    val appCursor: String? = null,
    val auditCursor: String? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

/**
 * TV Admin "Logs" — App + Audit log tabs with cursor pagination, mirroring the
 * phone AdminLogsScreen against the shared [AdminRepository.getAppLogs] /
 * [getAuditLogs]. The App tab supports a level filter (chip). A first page is a
 * replace (cursor=null); near-end scroll appends the next page via the server
 * cursor. Generation-gated so a tab/filter switch can't be clobbered by a
 * slower in-flight page.
 */
class TvAdminLogsViewModel(
    private val repository: AdminRepository,
) : ViewModel() {

    private var generation = 0
    private val _uiState = MutableStateFlow(TvAdminLogsUiState())
    val uiState: StateFlow<TvAdminLogsUiState> = _uiState.asStateFlow()

    init { load() }

    fun selectTab(tab: TvLogTab) {
        if (tab == _uiState.value.tab) return
        _uiState.update { it.copy(tab = tab) }
        load()
    }

    fun setLevel(level: String?) {
        if (level == _uiState.value.level) return
        _uiState.update { it.copy(level = level) }
        if (_uiState.value.tab == TvLogTab.App) load()
    }

    /** Replace the current tab's list (first page). */
    fun load() {
        val gen = ++generation
        _uiState.update { it.copy(isLoading = true, error = null) }
        fetch(gen, cursor = null, append = false)
    }

    fun loadMore() {
        val s = _uiState.value
        if (s.isLoading || s.isLoadingMore) return
        val cursor = if (s.tab == TvLogTab.App) s.appCursor else s.auditCursor
        if (cursor == null) return
        val gen = generation
        _uiState.update { it.copy(isLoadingMore = true) }
        fetch(gen, cursor = cursor, append = true)
    }

    private fun fetch(gen: Int, cursor: String?, append: Boolean) {
        viewModelScope.launch {
            val tab = _uiState.value.tab
            if (tab == TvLogTab.App) {
                when (val result = repository.getAppLogs(level = _uiState.value.level, cursor = cursor)) {
                    is ApiResult.Success -> {
                        if (gen != generation) return@launch
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                appEntries = if (append) it.appEntries + result.data.entries else result.data.entries,
                                appCursor = result.data.nextCursor,
                                error = null,
                            )
                        }
                    }
                    is ApiResult.Error, is ApiResult.NetworkError -> finishError(gen, result.errorMessage("Failed to load logs"))
                }
            } else {
                when (val result = repository.getAuditLogs(cursor = cursor)) {
                    is ApiResult.Success -> {
                        if (gen != generation) return@launch
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                auditEntries = if (append) it.auditEntries + result.data.entries else result.data.entries,
                                auditCursor = result.data.nextCursor,
                                error = null,
                            )
                        }
                    }
                    is ApiResult.Error, is ApiResult.NetworkError -> finishError(gen, result.errorMessage("Failed to load logs"))
                }
            }
        }
    }

    private fun finishError(gen: Int, message: String) {
        if (gen != generation) return
        _uiState.update { it.copy(isLoading = false, isLoadingMore = false, error = message) }
    }
}
