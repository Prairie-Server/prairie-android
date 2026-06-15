package com.continuum.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.request.CreateMediaRequest
import com.continuum.app.model.request.MediaRequest
import com.continuum.app.model.request.RequestDiscoverySection
import com.continuum.app.model.request.RequestMediaDetail
import com.continuum.app.model.request.RequestMediaResult
import com.continuum.app.model.request.RequestMediaType
import com.continuum.app.network.ApiResult
import com.continuum.app.network.errorMessage
import com.continuum.app.repository.RequestsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RequestsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isEnabled: Boolean = false,
    val sections: List<RequestDiscoverySection> = emptyList(),
    val error: String? = null,
)

class RequestsViewModel(
    private val repository: RequestsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchRequestsHome()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            fetchRequestsHome()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun fetchRequestsHome() {
        when (val status = repository.status()) {
            is ApiResult.Success -> {
                if (!status.data.requestsEnabled) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEnabled = false,
                            sections = emptyList(),
                            error = "Requests are not enabled on this server.",
                        )
                    }
                    return
                }
                fetchDiscover()
            }
            is ApiResult.Error, is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEnabled = false,
                        error = status.errorMessage("Failed to load request status"),
                    )
                }
            }
        }
    }

    private suspend fun fetchDiscover() {
        when (val discover = repository.discover()) {
            is ApiResult.Success -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEnabled = true,
                        sections = discover.data.sections,
                        error = null,
                    )
                }
            }
            is ApiResult.Error, is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEnabled = true,
                        error = discover.errorMessage("Failed to load requests"),
                    )
                }
            }
        }
    }
}

data class RequestSearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val mediaType: String? = RequestMediaType.All,
    val isLoading: Boolean = false,
    val results: List<RequestMediaResult> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val error: String? = null,
) {
    val hasSubmittedQuery: Boolean
        get() = submittedQuery.isNotBlank() && query.trim() == submittedQuery
}

class RequestSearchViewModel(
    private val repository: RequestsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestSearchUiState())
    val uiState: StateFlow<RequestSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(value: String) {
        _uiState.update { current ->
            val trimmed = value.trim()
            current.copy(
                query = value,
                submittedQuery = current.submittedQuery.takeIf { it == trimmed }.orEmpty(),
                results = if (current.submittedQuery == trimmed) current.results else emptyList(),
                totalResults = if (current.submittedQuery == trimmed) current.totalResults else 0,
                totalPages = if (current.submittedQuery == trimmed) current.totalPages else 1,
                page = if (current.submittedQuery == trimmed) current.page else 1,
                error = null,
            )
        }
    }

    fun onMediaTypeChanged(value: String?) {
        _uiState.update { it.copy(mediaType = value, error = null) }
    }

    fun search(page: Int = 1) {
        val submittedState = _uiState.value
        val query = submittedState.query.trim()
        val mediaType = submittedState.mediaType?.takeUnless { it == RequestMediaType.All }
        if (query.isBlank()) {
            searchJob?.cancel()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    submittedQuery = "",
                    results = emptyList(),
                    page = 1,
                    totalPages = 1,
                    totalResults = 0,
                    error = "Enter a search term.",
                )
            }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    submittedQuery = query,
                    results = emptyList(),
                    page = page,
                    totalPages = 1,
                    totalResults = 0,
                    error = null,
                )
            }
            when (val result = repository.search(query = query, mediaType = mediaType, page = page)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            submittedQuery = query,
                            results = result.data.results,
                            page = result.data.page,
                            totalPages = result.data.totalPages,
                            totalResults = result.data.totalResults,
                            error = null,
                        )
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            submittedQuery = query,
                            error = result.errorMessage("Failed to search requests"),
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        super.onCleared()
    }
}

data class RequestDetailUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val detail: RequestMediaDetail? = null,
    val error: String? = null,
    val notice: String? = null,
)

class RequestDetailViewModel(
    private val repository: RequestsRepository,
    private val mediaType: String,
    private val tmdbId: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestDetailUiState())
    val uiState: StateFlow<RequestDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, notice = null) }
            when (val result = repository.detail(mediaType, tmdbId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = result.data,
                            error = null,
                        )
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = null,
                            error = result.errorMessage("Failed to load request details"),
                        )
                    }
                }
            }
        }
    }

    fun submitRequest() {
        val detail = _uiState.value.detail
        if (detail == null) {
            _uiState.update { it.copy(error = "Request details are not loaded.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, notice = null) }
            when (val result = repository.create(detail.toCreateMediaRequest())) {
                is ApiResult.Success -> {
                    val refreshedDetail = when (val detailResult = repository.detail(mediaType, tmdbId)) {
                        is ApiResult.Success -> detailResult.data
                        else -> detail.copy(
                            request = detail.request.copy(
                                status = result.data.status,
                                requestable = false,
                                requestId = result.data.id,
                            ),
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            detail = refreshedDetail,
                            error = null,
                            notice = "Request submitted.",
                        )
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = result.errorMessage("Failed to submit request"),
                        )
                    }
                }
            }
        }
    }

    private fun RequestMediaDetail.toCreateMediaRequest(): CreateMediaRequest = CreateMediaRequest(
        mediaType = mediaType,
        tmdbId = tmdbId,
        tvdbId = tvdbId,
        imdbId = imdbId,
        title = title,
        year = year,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
    )
}

data class MyRequestsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val requests: List<MediaRequest> = emptyList(),
    val actionInFlightId: String? = null,
    val error: String? = null,
)

class MyRequestsViewModel(
    private val repository: RequestsRepository,
) : ViewModel() {

    private var loadGeneration = 0
    private val _uiState = MutableStateFlow(MyRequestsUiState())
    val uiState: StateFlow<MyRequestsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            refreshMine(generation)
        }
    }

    fun refresh() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            refreshMine(generation)
            if (generation == loadGeneration) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun cancel(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInFlightId = id, error = null) }
            when (val result = repository.cancel(id)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            requests = repository.mine.value,
                            actionInFlightId = null,
                            error = null,
                        )
                    }
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            actionInFlightId = null,
                            error = result.errorMessage("Failed to cancel request"),
                        )
                    }
                }
            }
        }
    }

    private suspend fun refreshMine(generation: Int) {
        val result = repository.refreshMine()
        if (generation != loadGeneration) return
        when (result) {
            is ApiResult.Success -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        requests = repository.mine.value,
                        error = null,
                    )
                }
            }
            is ApiResult.Error, is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.errorMessage("Failed to load your requests"),
                    )
                }
            }
        }
    }
}
