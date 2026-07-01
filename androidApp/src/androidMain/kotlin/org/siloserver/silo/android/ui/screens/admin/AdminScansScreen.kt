package org.siloserver.silo.android.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.siloserver.silo.android.ui.components.SiloTopBar
import org.siloserver.silo.android.ui.components.EmptyStateView
import org.siloserver.silo.android.ui.components.ErrorView
import org.siloserver.silo.android.ui.components.LoadingIndicator
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
import org.koin.compose.viewmodel.koinViewModel

// ---------------------------------------------------------------------------
// ViewModel (co-located, LibrariesViewModel idiom; registered in AndroidModule)
// ---------------------------------------------------------------------------

data class AdminScansUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val libraries: List<UserLibrary> = emptyList(),
    /** Library IDs with an in-flight scan or cancel request. */
    val busyLibraryIds: Set<Int> = emptySet(),
    /** True while a scan-all request is in flight. */
    val scanningAll: Boolean = false,
    val error: String? = null,
)

/**
 * Owns the library list and per-library scan/cancel actions. Busy-set tracking
 * disables individual row buttons while a request is in flight. Scan-all
 * disables the top-bar action while running. One-shot results surface via
 * [toasts] then trigger a refresh.
 */
class AdminScansViewModel(
    private val adminRepository: AdminRepository,
    private val personalDataRepository: PersonalDataRepository,
) : ViewModel() {

    private var loadGeneration = 0
    private val _uiState = MutableStateFlow(AdminScansUiState())
    val uiState: StateFlow<AdminScansUiState> = _uiState.asStateFlow()

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

    fun refresh() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            fetch(generation)
            if (generation == loadGeneration) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun scanLibrary(id: Int) {
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

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScansScreen(
    onBackClick: () -> Unit,
    viewModel: AdminScansViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            SiloTopBar(
                title = "Scans",
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = viewModel::scanAll,
                        enabled = !state.scanningAll,
                    ) {
                        if (state.scanningAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan all libraries",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading && state.libraries.isEmpty() ->
                LoadingIndicator(modifier = Modifier.padding(padding))

            state.error != null && state.libraries.isEmpty() ->
                ErrorView(
                    message = state.error!!,
                    onRetry = viewModel::load,
                    modifier = Modifier.padding(padding),
                )

            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (state.libraries.isEmpty()) {
                    EmptyStateView(
                        title = "No libraries",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.libraries, key = { it.id }) { library ->
                            LibraryScanRow(
                                library = library,
                                isBusy = library.id in state.busyLibraryIds,
                                onScan = { viewModel.scanLibrary(library.id) },
                                onCancel = { viewModel.cancelLibrary(library.id) },
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScanRow(
    library: UserLibrary,
    isBusy: Boolean,
    onScan: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = library.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = library.type.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onScan,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Scan")
                }
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
