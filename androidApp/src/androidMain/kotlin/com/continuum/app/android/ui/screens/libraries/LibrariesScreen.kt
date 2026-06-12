package com.continuum.app.android.ui.screens.libraries

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.android.ui.components.EmptyStateView
import com.continuum.app.android.ui.components.ErrorView
import com.continuum.app.android.ui.components.HeroBackdropImage
import com.continuum.app.android.ui.components.HeroTintBackground
import com.continuum.app.android.ui.screens.browse.CatalogGrid
import com.continuum.app.android.ui.screens.home.FeaturedCarousel
import com.continuum.app.android.ui.screens.home.HomeSectionRow
import com.continuum.app.android.ui.screens.profiles.ProfileAvatar
import com.continuum.app.android.ui.util.rememberDominantColor
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.navigation.MediaMode
import com.continuum.app.model.navigation.mobileMediaModeForLibraryType
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.model.profile.Profile
import com.continuum.app.model.section.LibraryCollection
import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.model.section.splitFeatured
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.repository.SectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibrariesSubtab {
    Recommended,
    Browse,
    Collections,
}

enum class LibraryBrowseSort(
    val label: String,
    val sortField: String,
    val sortOrder: String,
) {
    RecentlyAdded("Recently Added", "added_at", "desc"),
    Title("Title", "title", "asc"),
    ReleaseDate("Release Date", "release_date", "desc"),
}

data class LibrariesUiState(
    val isLoadingLibraries: Boolean = true,
    val libraries: List<UserLibrary> = emptyList(),
    val selectedLibraryId: Int? = null,
    val selectedTab: LibrariesSubtab = LibrariesSubtab.Recommended,
    val isLoadingSections: Boolean = false,
    val sections: List<ResolvedSection> = emptyList(),
    val sectionsError: String? = null,
    val isLoadingCatalog: Boolean = false,
    val isLoadingMoreCatalog: Boolean = false,
    val catalogItems: List<BrowseItem> = emptyList(),
    val catalogTotal: Int = 0,
    val catalogHasMore: Boolean = false,
    val browseGenres: List<String> = emptyList(),
    val selectedBrowseGenre: String? = null,
    val browseSort: LibraryBrowseSort = LibraryBrowseSort.RecentlyAdded,
    val catalogError: String? = null,
    val isLoadingCollections: Boolean = false,
    val collections: List<LibraryCollection> = emptyList(),
    val collectionsError: String? = null,
    val librariesError: String? = null,
)

class LibrariesViewModel(
    private val personalDataRepository: PersonalDataRepository,
    private val sectionRepository: SectionRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibrariesUiState())
    val uiState: StateFlow<LibrariesUiState> = _uiState.asStateFlow()
    private var recommendedLoadedLibraryId: Int? = null
    private var browseLoadedLibraryId: Int? = null
    private var collectionsLoadedLibraryId: Int? = null
    private val pageSize = 42

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingLibraries = true,
                    librariesError = null,
                )
            }

            when (val result = personalDataRepository.listUserLibraries()) {
                is ApiResult.Success -> {
                    // The Audio tab is the sole owner of this VM (MainScreen
                    // gates it on Tab.Audio), so restrict to audio-type
                    // libraries — otherwise the hub defaults to the first
                    // library overall (a video library by sort order) and its
                    // selector lists movies/TV the audio tab can't browse.
                    val libraries = result.data
                        .filter { mobileMediaModeForLibraryType(it.type) == MediaMode.Audio }
                        .sortedBy { library -> library.sortOrder }
                    val selectedLibraryId = _uiState.value.selectedLibraryId
                        ?.takeIf { currentId -> libraries.any { it.id == currentId } }
                        ?: libraries.firstOrNull()?.id

                    _uiState.update {
                        it.copy(
                            isLoadingLibraries = false,
                            libraries = libraries,
                            selectedLibraryId = selectedLibraryId,
                            librariesError = null,
                        )
                    }

                    if (selectedLibraryId != null) {
                        loadCurrentTab(selectedLibraryId, force = true)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingLibraries = false,
                            libraries = emptyList(),
                            sections = emptyList(),
                            librariesError = result.message.ifBlank { "Failed to load libraries" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoadingLibraries = false,
                            libraries = emptyList(),
                            sections = emptyList(),
                            librariesError = "Network error: ${result.exception.message ?: "unknown"}",
                        )
                    }
                }
            }
        }
    }

    fun selectLibrary(libraryId: Int) {
        if (_uiState.value.selectedLibraryId == libraryId) return
        recommendedLoadedLibraryId = null
        browseLoadedLibraryId = null
        collectionsLoadedLibraryId = null
        _uiState.update {
            it.copy(
                selectedLibraryId = libraryId,
                sections = emptyList(),
                sectionsError = null,
                catalogItems = emptyList(),
                catalogTotal = 0,
                catalogHasMore = false,
                browseGenres = emptyList(),
                selectedBrowseGenre = null,
                catalogError = null,
                collections = emptyList(),
                collectionsError = null,
            )
        }
        loadCurrentTab(libraryId, force = true)
    }

    fun selectTab(tab: LibrariesSubtab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update { it.copy(selectedTab = tab) }
        _uiState.value.selectedLibraryId?.let { loadCurrentTab(it, force = false) }
    }

    fun selectBrowseGenre(genre: String?) {
        _uiState.update {
            it.copy(
                selectedBrowseGenre = genre,
                catalogItems = emptyList(),
                catalogTotal = 0,
                catalogHasMore = false,
            )
        }
        _uiState.value.selectedLibraryId?.let { loadCatalog(it, reset = true, force = true) }
    }

    fun selectBrowseSort(sort: LibraryBrowseSort) {
        _uiState.update {
            it.copy(
                browseSort = sort,
                catalogItems = emptyList(),
                catalogTotal = 0,
                catalogHasMore = false,
            )
        }
        _uiState.value.selectedLibraryId?.let { loadCatalog(it, reset = true, force = true) }
    }

    fun loadMoreCatalog() {
        val state = _uiState.value
        val libraryId = state.selectedLibraryId ?: return
        if (state.isLoadingCatalog || state.isLoadingMoreCatalog || !state.catalogHasMore) return
        loadCatalog(libraryId, reset = false, force = true)
    }

    private fun loadCurrentTab(libraryId: Int, force: Boolean) {
        when (_uiState.value.selectedTab) {
            LibrariesSubtab.Recommended -> loadRecommended(libraryId, force)
            LibrariesSubtab.Browse -> loadCatalog(libraryId, reset = true, force = force)
            LibrariesSubtab.Collections -> loadCollections(libraryId, force)
        }
    }

    fun showBrowseFromRecommended() {
        _uiState.update { it.copy(selectedTab = LibrariesSubtab.Browse) }
        _uiState.value.selectedLibraryId?.let { loadCatalog(it, reset = true, force = false) }
    }

    fun retryCurrentTab() {
        _uiState.value.selectedLibraryId?.let { loadCurrentTab(it, force = true) }
    }

    private fun loadRecommended(libraryId: Int, force: Boolean) {
        if (!force && recommendedLoadedLibraryId == libraryId) return
        recommendedLoadedLibraryId = libraryId
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingSections = true,
                    sectionsError = null,
                    sections = emptyList(),
                )
            }

            when (val result = sectionRepository.getLibrarySections(libraryId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingSections = false,
                            sections = result.data.sections.filter { section -> section.items.isNotEmpty() },
                            sectionsError = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingSections = false,
                            sections = emptyList(),
                            sectionsError = result.message.ifBlank { "Failed to load recommendations" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoadingSections = false,
                            sections = emptyList(),
                            sectionsError = "Network error: ${result.exception.message ?: "unknown"}",
                        )
                    }
                }
            }
        }
    }

    private fun loadCatalog(libraryId: Int, reset: Boolean, force: Boolean) {
        if (reset && !force && browseLoadedLibraryId == libraryId && _uiState.value.catalogItems.isNotEmpty()) {
            return
        }
        browseLoadedLibraryId = libraryId
        viewModelScope.launch {
            val state = _uiState.value
            val offset = if (reset) 0 else state.catalogItems.size

            if (reset && state.browseGenres.isEmpty()) {
                launch {
                    when (val filters = catalogRepository.getFilters(libraryId)) {
                        is ApiResult.Success -> {
                            _uiState.update { it.copy(browseGenres = filters.data.genres) }
                        }
                        else -> Unit
                    }
                }
            }

            _uiState.update {
                if (reset) {
                    it.copy(
                        isLoadingCatalog = true,
                        isLoadingMoreCatalog = false,
                        catalogError = null,
                    )
                } else {
                    it.copy(
                        isLoadingMoreCatalog = true,
                        catalogError = null,
                    )
                }
            }

            when (
                val result = catalogRepository.browse(
                    libraryId = libraryId,
                    genre = state.selectedBrowseGenre,
                    sort = state.browseSort.sortField,
                    order = state.browseSort.sortOrder,
                    offset = offset,
                    limit = pageSize,
                )
            ) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            isLoadingMoreCatalog = false,
                            catalogItems = if (reset) result.data.items else it.catalogItems + result.data.items,
                            catalogTotal = result.data.total,
                            catalogHasMore = result.data.hasMore,
                            catalogError = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            isLoadingMoreCatalog = false,
                            catalogError = result.message.ifBlank { "Failed to load catalog" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            isLoadingMoreCatalog = false,
                            catalogError = "Network error: ${result.exception.message ?: "unknown"}",
                        )
                    }
                }
            }
        }
    }

    private fun loadCollections(libraryId: Int, force: Boolean) {
        if (!force && collectionsLoadedLibraryId == libraryId && _uiState.value.collections.isNotEmpty()) {
            return
        }
        collectionsLoadedLibraryId = libraryId
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingCollections = true,
                    collectionsError = null,
                    collections = emptyList(),
                )
            }
            when (val result = sectionRepository.getLibraryCollections(libraryId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCollections = false,
                            collections = result.data,
                            collectionsError = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCollections = false,
                            collectionsError = result.message.ifBlank { "Failed to load collections" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCollections = false,
                            collectionsError = "Network error: ${result.exception.message ?: "unknown"}",
                        )
                    }
                }
            }
        }
    }
}

// Chrome metrics: status bar + this constant ≈ visible chrome height (header
// row + tab selector). Used to push tab content below the floating chrome and
// to drive the carousel's [extraTopInset] on the Recommended tab.
private val LibrariesChromeContentHeight: Dp = 110.dp

// Distance the Recommended tab must scroll for the chrome scrim to fully
// fade in. Mirrors `chromeScrimFadeDistance` on iOS.
private const val ChromeFadeDistanceDp = 80f

@Composable
fun LibrariesScreen(
    onItemClick: (String) -> Unit,
    onPlayClick: (String) -> Unit,
    onCollectionClick: (String, Int) -> Unit,
    viewModel: LibrariesViewModel,
    activeProfile: Profile?,
    onLibrarySelectorClick: () -> Unit,
    onSearchClick: () -> Unit,
    onPersonalListsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onSwitchServerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val selectedLibrary = state.libraries.firstOrNull { it.id == state.selectedLibraryId }

    // Hero backdrop sampled from the active featured carousel page. Mirrors
    // iOS `LibraryRecommendedView` — the parent owns the URL so the page-level
    // tint + blur extend past the carousel.
    var heroBackdropUrl by rememberSaveable(state.selectedLibraryId) {
        mutableStateOf<String?>(null)
    }
    var heroBackdropThumbhash by rememberSaveable(state.selectedLibraryId) {
        mutableStateOf<String?>(null)
    }

    val heroTint by rememberDominantColor(
        imageUrl = heroBackdropUrl,
        fallback = MaterialTheme.colorScheme.background,
    )

    // Recommended tab scroll state — drives the chrome scrim opacity so the
    // header reads as part of the artwork while the hero is at rest, then
    // resolves to a solid scrim once the user scrolls past.
    val recommendedListState = rememberLazyListState()
    val density = LocalDensity.current
    val chromeFadePx = remember(density) {
        with(density) { ChromeFadeDistanceDp.dp.toPx() }
    }
    val recommendedScrollProgress by remember(chromeFadePx) {
        derivedStateOf {
            if (recommendedListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (recommendedListState.firstVisibleItemScrollOffset / chromeFadePx).coerceIn(0f, 1f)
            }
        }
    }
    val chromeScrimProgress = if (state.selectedTab == LibrariesSubtab.Recommended) {
        recommendedScrollProgress
    } else {
        1f
    }

    val showHero = state.selectedTab == LibrariesSubtab.Recommended &&
        state.sections.any { it.featured } &&
        heroBackdropUrl != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (showHero) {
            HeroTintBackground(tint = heroTint)
            HeroBackdropImage(
                url = heroBackdropUrl,
                thumbhash = heroBackdropThumbhash,
            )
        }

        when {
            state.isLoadingLibraries && state.libraries.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.librariesError != null && state.libraries.isEmpty() -> {
                ErrorView(
                    message = state.librariesError ?: "Failed to load libraries",
                    onRetry = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            selectedLibrary == null -> {
                EmptyStateView(
                    title = "No libraries available",
                    subtitle = "Libraries visible to this profile will show up here",
                    icon = Icons.Default.VideoLibrary,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                when (state.selectedTab) {
                    LibrariesSubtab.Recommended -> RecommendedTabContent(
                        state = state,
                        listState = recommendedListState,
                        onItemClick = onItemClick,
                        onPlayClick = onPlayClick,
                        onRetry = viewModel::retryCurrentTab,
                        onSeeAllClick = viewModel::showBrowseFromRecommended,
                        onActiveBackdropChange = { url, thumbhash ->
                            heroBackdropUrl = url
                            heroBackdropThumbhash = thumbhash
                        },
                    )
                    LibrariesSubtab.Browse -> BrowseTabContent(
                        state = state,
                        onItemClick = onItemClick,
                        onRetry = viewModel::retryCurrentTab,
                        onLoadMore = viewModel::loadMoreCatalog,
                        onGenreChanged = viewModel::selectBrowseGenre,
                        onSortChanged = viewModel::selectBrowseSort,
                    )
                    LibrariesSubtab.Collections -> CollectionsTabContent(
                        state = state,
                        onCollectionClick = { collectionId ->
                            state.selectedLibraryId?.let { libraryId ->
                                onCollectionClick(collectionId, libraryId)
                            }
                        },
                        onRetry = viewModel::retryCurrentTab,
                    )
                }
            }
        }

        LibrariesFloatingChrome(
            scrimProgress = chromeScrimProgress,
            selectedLibrary = selectedLibrary,
            canSwitch = state.libraries.size > 1,
            activeProfile = activeProfile,
            selectedTab = state.selectedTab,
            onLibrarySelectorClick = onLibrarySelectorClick,
            onTabSelected = viewModel::selectTab,
            onSearchClick = onSearchClick,
            onPersonalListsClick = onPersonalListsClick,
            onSettingsClick = onSettingsClick,
            onSwitchProfileClick = onSwitchProfileClick,
            onSwitchServerClick = onSwitchServerClick,
        )
    }
}

@Composable
private fun RecommendedTabContent(
    state: LibrariesUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onItemClick: (String) -> Unit,
    onPlayClick: (String) -> Unit,
    onRetry: () -> Unit,
    onSeeAllClick: () -> Unit,
    onActiveBackdropChange: (url: String?, thumbhash: String?) -> Unit,
) {
    when {
        state.isLoadingSections && state.sections.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.sectionsError != null && state.sections.isEmpty() -> {
            ErrorView(
                message = state.sectionsError ?: "Failed to load recommendations",
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = LibrariesChromeContentHeight)
                    .windowInsetsPadding(WindowInsets.statusBars),
            )
        }
        state.sections.isEmpty() -> {
            EmptyStateView(
                title = "No recommendations yet",
                subtitle = "Try switching libraries or browsing the full catalog",
                icon = libraryIcon(state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.type.orEmpty()),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = LibrariesChromeContentHeight)
                    .windowInsetsPadding(WindowInsets.statusBars),
            )
        }
        else -> {
            val (featuredSection, regularSections) = remember(state.sections) {
                state.sections.splitFeatured().let { it.featured to it.rest }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (featuredSection != null && featuredSection.items.isNotEmpty()) {
                    item(key = "library-featured") {
                        FeaturedCarousel(
                            items = featuredSection.items,
                            onPlayClick = onPlayClick,
                            onInfoClick = onItemClick,
                            onActiveBackdropChange = onActiveBackdropChange,
                            // Push the deck below the taller Libraries chrome
                            // (header + tab selector). Carousel already adds
                            // `statusBar + 64dp`; this covers the tab row.
                            extraTopInset = 50.dp,
                        )
                    }
                } else {
                    // No featured → reserve runway under the chrome so the
                    // first row doesn't slide under the floating header.
                    item(key = "no-featured") {
                        Spacer(
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .height(LibrariesChromeContentHeight + 8.dp),
                        )
                    }
                }

                items(
                    items = regularSections,
                    key = { section -> section.id },
                ) { section ->
                    HomeSectionRow(
                        section = section,
                        onItemClick = onItemClick,
                        onSeeAllClick = { onSeeAllClick() },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }
    }
}

@Composable
private fun BrowseTabContent(
    state: LibrariesUiState,
    onItemClick: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onGenreChanged: (String?) -> Unit,
    onSortChanged: (LibraryBrowseSort) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = LibrariesChromeContentHeight),
    ) {
        if (state.browseGenres.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.selectedBrowseGenre == null,
                    onClick = { onGenreChanged(null) },
                    label = { Text("All") },
                    colors = libraryChipColors(state.selectedBrowseGenre == null),
                )
                state.browseGenres.forEach { genre ->
                    FilterChip(
                        selected = state.selectedBrowseGenre == genre,
                        onClick = { onGenreChanged(genre) },
                        label = { Text(genre) },
                        colors = libraryChipColors(state.selectedBrowseGenre == genre),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryBrowseSort.entries.forEach { sort ->
                FilterChip(
                    selected = state.browseSort == sort,
                    onClick = { onSortChanged(sort) },
                    label = { Text(sort.label) },
                    colors = libraryChipColors(state.browseSort == sort),
                )
            }
        }

        when {
            state.isLoadingCatalog && state.catalogItems.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.catalogError != null && state.catalogItems.isEmpty() -> {
                ErrorView(
                    message = state.catalogError ?: "Failed to load catalog",
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            state.catalogItems.isEmpty() -> {
                EmptyStateView(
                    title = "No items found",
                    subtitle = "Try adjusting the sort or switching libraries",
                    icon = libraryIcon(state.libraries.firstOrNull { it.id == state.selectedLibraryId }?.type.orEmpty()),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Text(
                    text = "${state.catalogTotal} items",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                CatalogGrid(
                    items = state.catalogItems,
                    isLoadingMore = state.isLoadingMoreCatalog,
                    hasMore = state.catalogHasMore,
                    onItemClick = onItemClick,
                    onLoadMore = onLoadMore,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CollectionsTabContent(
    state: LibrariesUiState,
    onCollectionClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val contentTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        LibrariesChromeContentHeight
    when {
        state.isLoadingCollections && state.collections.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.collectionsError != null && state.collections.isEmpty() -> {
            ErrorView(
                message = state.collectionsError ?: "Failed to load collections",
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentTopPadding),
            )
        }
        state.collections.isEmpty() -> {
            EmptyStateView(
                title = "No collections found",
                subtitle = "This library does not have any collections yet",
                icon = Icons.Default.VideoLibrary,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentTopPadding),
            )
        }
        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentTopPadding,
                    bottom = 16.dp,
                ),
            ) {
                items(
                    items = state.collections,
                    key = { it.id },
                ) { collection ->
                    InlineLibraryCollectionCard(
                        collection = collection,
                        onClick = { onCollectionClick(collection.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineLibraryCollectionCard(
    collection: LibraryCollection,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
                .aspectRatio(2f / 3f),
        ) {
            ThumbhashImage(
                url = collection.posterUrl,
                thumbhash = collection.posterThumbhash,
                contentDescription = collection.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = collection.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = collection.itemCount?.let { "$it items" } ?: "Collection",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibrariesFloatingChrome(
    scrimProgress: Float,
    selectedLibrary: UserLibrary?,
    canSwitch: Boolean,
    activeProfile: Profile?,
    selectedTab: LibrariesSubtab,
    onLibrarySelectorClick: () -> Unit,
    onTabSelected: (LibrariesSubtab) -> Unit,
    onSearchClick: () -> Unit,
    onPersonalListsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onSwitchServerClick: () -> Unit,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val animatedFill by animateFloatAsState(
        targetValue = scrimProgress,
        label = "librariesChromeFill",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.55f * animatedFill),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.30f * animatedFill),
                        MaterialTheme.colorScheme.background.copy(alpha = 0f),
                    ),
                ),
            )
            .padding(top = statusBarPadding.calculateTopPadding() + 6.dp),
    ) {
        // Top row: library selector on the left, action icons on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibrarySelectorButton(
                library = selectedLibrary,
                canSwitch = canSwitch,
                onClick = onLibrarySelectorClick,
                modifier = Modifier.weight(1f),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeIconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                    )
                }
                ChromeProfileMenu(
                    activeProfile = activeProfile,
                    onPersonalListsClick = onPersonalListsClick,
                    onSettingsClick = onSettingsClick,
                    onSwitchProfileClick = onSwitchProfileClick,
                    onSwitchServerClick = onSwitchServerClick,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LibrarySubtabRow(
            selectedTab = selectedTab,
            onRecommendedClick = { onTabSelected(LibrariesSubtab.Recommended) },
            onBrowseClick = { onTabSelected(LibrariesSubtab.Browse) },
            onCollectionsClick = { onTabSelected(LibrariesSubtab.Collections) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Compact text-based library selector that mirrors iOS
 * `LibrarySelectorButton` — library name with a small chevron stacked above
 * a secondary type label. Tapping opens the picker sheet.
 */
@Composable
private fun LibrarySelectorButton(
    library: UserLibrary?,
    canSwitch: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(enabled = canSwitch && library != null, onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = library?.name ?: "Libraries",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (canSwitch && library != null) {
                Spacer(modifier = Modifier.size(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = library?.typeLabel() ?: "Choose a library",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChromeIconButton(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun ChromeProfileMenu(
    activeProfile: Profile?,
    onPersonalListsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSwitchProfileClick: () -> Unit,
    onSwitchServerClick: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    Box {
        ChromeIconButton(onClick = { menuExpanded = true }) {
            if (activeProfile != null) {
                ProfileAvatar(
                    avatar = activeProfile.avatar,
                    name = activeProfile.name,
                    size = 32.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Favorites & Watchlist") },
                onClick = {
                    menuExpanded = false
                    onPersonalListsClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    menuExpanded = false
                    onSettingsClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Switch Profile") },
                onClick = {
                    menuExpanded = false
                    onSwitchProfileClick()
                },
            )
            DropdownMenuItem(
                text = { Text("Switch Server") },
                onClick = {
                    menuExpanded = false
                    onSwitchServerClick()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesSelectorSheet(
    libraries: List<UserLibrary>,
    selectedLibraryId: Int?,
    onSelectLibrary: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = selectorSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Libraries",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Switch the active library for recommendations and browsing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            libraries.forEach { library ->
                LibrarySelectorRow(
                    library = library,
                    isSelected = library.id == selectedLibraryId,
                    onClick = { onSelectLibrary(library.id) },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LibrarySubtabRow(
    selectedTab: LibrariesSubtab,
    onRecommendedClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LibrarySubtabChip(
            label = "Recommended",
            selected = selectedTab == LibrariesSubtab.Recommended,
            onClick = onRecommendedClick,
        )
        // Browse chip hidden per UX direction (2026-05-24). The Browse content
        // is still reachable via the per-section "See all" button on the
        // Recommended tab, and BrowseTabContent / loadCatalog stay in the
        // tree so that path keeps working. Re-add the chip here to expose
        // Browse as a top-level destination again.
        LibrarySubtabChip(
            label = "Collections",
            selected = selectedTab == LibrariesSubtab.Collections,
            onClick = onCollectionsClick,
        )
    }
}

@Composable
private fun LibrarySubtabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) {
            Color.White
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        },
        contentColor = if (selected) {
            Color.Black
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun LibrarySelectorRow(
    library: UserLibrary,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            Color.White.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = libraryIcon(library.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = library.typeLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun libraryChipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Color.White,
    selectedLabelColor = Color.Black,
    selectedLeadingIconColor = Color.Black,
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    labelColor = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

private fun libraryIcon(type: String): ImageVector = when (type.lowercase()) {
    "movies", "movie" -> Icons.Default.LocalMovies
    "series", "tv", "shows" -> Icons.Default.Tv
    "audiobook", "audiobooks" -> Icons.Default.Headphones
    "book", "books", "ebook", "ebooks" -> Icons.AutoMirrored.Filled.MenuBook
    "comic", "comics", "manga" -> Icons.Default.AutoStories
    else -> Icons.Default.VideoLibrary
}

private fun UserLibrary.typeLabel(): String = when (type.lowercase()) {
    "movies", "movie" -> "Movies library"
    "series", "tv", "shows" -> "TV library"
    "audiobook", "audiobooks" -> "Audiobooks library"
    "book", "books", "ebook", "ebooks" -> "Books library"
    "comic", "comics", "manga" -> "Comics / manga library"
    else -> "Library"
}
