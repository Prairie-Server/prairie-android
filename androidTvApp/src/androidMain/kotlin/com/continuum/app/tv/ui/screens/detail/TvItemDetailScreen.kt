package com.continuum.app.tv.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.model.catalog.EpisodeListItem
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.catalog.isAudiobookItemType
import com.continuum.app.model.watchtogether.RoomSnapshot
import com.continuum.app.tv.ui.components.TvDialogOption
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvHeroActionPill
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.components.TvMediaRow
import com.continuum.app.tv.ui.components.TvOptionDialog
import com.continuum.app.tv.ui.components.TvPillVariant
import com.continuum.app.tv.ui.components.TvRowStyle
import com.continuum.app.tv.ui.screens.watchtogether.TvJoinCodeDialog
import com.continuum.app.tv.ui.screens.watchtogether.TvWatchTogetherEntryDialog
import com.continuum.app.tv.ui.screens.watchtogether.TvWatchTogetherViewModel
import com.continuum.app.tv.ui.theme.Spacing
import com.continuum.app.tv.ui.theme.TvSmoothBringIntoViewSpec
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TvItemDetailScreen(
    contentId: String,
    seasonNumber: Int? = null,
    onPlay: (contentId: String, fileId: Int?, itemType: String?, resumePositionSeconds: Double?) -> Unit,
    onItemDetail: (contentId: String) -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onSeasonClick: (seriesId: String, seasonNumber: Int) -> Unit,
    onWatchTogether: (RoomSnapshot) -> Unit,
    onOpenPerson: (personId: Int) -> Unit,
    onBack: () -> Unit,
    viewModel: TvItemDetailViewModel = koinViewModel(
        key = "item-detail-$contentId-${seasonNumber ?: "default"}",
        parameters = { parametersOf(contentId) },
    ),
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(enabled = true) { onBack() }

    LaunchedEffect(state.detail?.contentId, seasonNumber, state.seasons, state.selectedSeason) {
        val detail = state.detail ?: return@LaunchedEffect
        if (detail.type != "series" || seasonNumber == null) return@LaunchedEffect
        if (state.selectedSeason == seasonNumber) return@LaunchedEffect
        if (state.seasons.any { it.seasonNumber == seasonNumber }) {
            viewModel.onSeasonSelected(seasonNumber)
        }
    }

    when {
        state.isLoading -> TvLoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        state.error != null -> TvErrorScreen(
            message = state.error!!,
            onRetry = viewModel::loadAll,
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
        state.detail != null -> TvDetailContent(
            detail = state.detail!!,
            state = state,
            viewModel = viewModel,
            onPlay = onPlay,
            onItemDetail = onItemDetail,
            onSeriesClick = onSeriesClick,
            onSeasonClick = onSeasonClick,
            onWatchTogether = onWatchTogether,
            onOpenPerson = onOpenPerson,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvDetailContent(
    detail: ItemDetail,
    state: TvItemDetailUiState,
    viewModel: TvItemDetailViewModel,
    onPlay: (contentId: String, fileId: Int?, itemType: String?, resumePositionSeconds: Double?) -> Unit,
    onItemDetail: (contentId: String) -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onSeasonClick: (seriesId: String, seasonNumber: Int) -> Unit,
    onWatchTogether: (RoomSnapshot) -> Unit,
    onOpenPerson: (personId: Int) -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    val firstEpisodeFocus = remember { FocusRequester() }
    val firstCastFocus = remember { FocusRequester() }
    val firstSimilarFocus = remember { FocusRequester() }
    val detailsFocus = remember { FocusRequester() }
    val aboutFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isAudiobook = isAudiobookItemType(detail.type)

    LaunchedEffect(detail.contentId) {
        runCatching { playFocus.requestFocus() }
    }

    val showsEpisodeRail = detail.type in setOf("series", "season", "episode") &&
        state.episodes.isNotEmpty()
    val showsSeasonChips = detail.type in setOf("series", "season", "episode") && state.seasons.size > 1
    val showsSimilarRail = detail.type != "episode" && state.moreLikeThis.isNotEmpty()
    val showsDetailsSection = remember(detail) { detail.hasTvDetailFacts() }
    val showsAboutSection = !detail.overview.isNullOrBlank() || !detail.tagline.isNullOrBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvSmoothBringIntoViewSpec) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(72.dp),
            ) {
                item(key = "hero") {
                    TvDetailHero(
                        title = detail.title,
                        seriesTitle = if (detail.type == "episode") detail.seriesTitle else null,
                        logoUrl = detail.logoUrl,
                        backdropUrl = detail.backdropUrl,
                        backdropThumbhash = detail.backdropThumbhash,
                        eyebrow = if (detail.type == "episode") null else TvDetailMetadata.eyebrow(detail),
                        sourceTokens = TvDetailMetadata.sourceTokens(detail),
                        ratingChip = TvDetailMetadata.ratingChip(detail),
                        overview = detail.overview,
                        factsLine = TvDetailMetadata.factsLine(detail),
                        starringText = TvDetailMetadata.starringText(detail),
                        actions = {
                            HeroActionRow(
                                detail = detail,
                                state = state,
                                viewModel = viewModel,
                                playFocus = playFocus,
                                onPlay = onPlay,
                                onSeriesClick = onSeriesClick,
                                onSeasonClick = onSeasonClick,
                                onWatchTogether = onWatchTogether,
                            )
                        },
                    )
                }

                if (showsEpisodeRail) {
                    item(key = "episodes") {
                        EpisodesSection(
                            detail = detail,
                            state = state,
                            showsSeasonChips = showsSeasonChips,
                            firstEpisodeFocus = firstEpisodeFocus,
                            onReturnToHero = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                    runCatching { playFocus.requestFocus() }
                                }
                                true
                            },
                            onSeasonSelected = { season ->
                                if (detail.type == "series") {
                                    viewModel.onSeasonSelected(season.seasonNumber)
                                } else if (season.contentId != detail.contentId) {
                                    onItemDetail(season.contentId)
                                }
                            },
                            onEpisodeSelected = { onItemDetail(it.contentId) },
                        )
                    }
                }

                if (!isAudiobook && detail.cast.isNotEmpty()) {
                    item(key = "cast") {
                        TvCastCrewSection(
                            cast = detail.cast,
                            modifier = Modifier.padding(horizontal = Spacing.safeArea),
                            firstItemFocusRequester = firstCastFocus,
                            onDirectionDown = {
                                when {
                                    showsDetailsSection -> runCatching { detailsFocus.requestFocus() }.isSuccess
                                    showsAboutSection -> runCatching { aboutFocus.requestFocus() }.isSuccess
                                    else -> false
                                }
                            },
                            onCastMemberClick = { member ->
                                member.personId?.toIntOrNull()?.let(onOpenPerson)
                            },
                        )
                    }
                }

                if (showsDetailsSection) {
                    item(key = "details") {
                        DetailsSection(
                            detail = detail,
                            modifier = Modifier
                                .padding(horizontal = Spacing.safeArea)
                                .focusRequester(detailsFocus),
                        )
                    }
                }

                if (showsAboutSection) {
                    item(key = "about") {
                        AboutSection(
                            detail = detail,
                            modifier = Modifier
                                .padding(horizontal = Spacing.safeArea)
                                .focusRequester(aboutFocus),
                        )
                    }
                }

                if (showsSimilarRail) {
                    item(key = "more-like-this") {
                        TvMediaRow(
                            title = "More Like This",
                            eyebrow = "Recommended",
                            items = state.moreLikeThis,
                            onItemClick = onItemDetail,
                            style = TvRowStyle.Poster,
                            horizontalPadding = Spacing.safeArea,
                            rowTopPadding = 16.dp,
                            firstItemFocusRequester = firstSimilarFocus,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroActionRow(
    detail: ItemDetail,
    state: TvItemDetailUiState,
    viewModel: TvItemDetailViewModel,
    playFocus: FocusRequester,
    onPlay: (contentId: String, fileId: Int?, itemType: String?, resumePositionSeconds: Double?) -> Unit,
    onSeriesClick: (seriesId: String) -> Unit,
    onSeasonClick: (seriesId: String, seasonNumber: Int) -> Unit,
    onWatchTogether: (RoomSnapshot) -> Unit,
) {
    var moreOpen by remember(detail.contentId) { mutableStateOf(false) }
    var ratingOpen by remember(detail.contentId) { mutableStateOf(false) }
    // Watch Together is video-only (synced playback); hide it for series/audiobook.
    val showsWatchTogether = detail.type in setOf("movie", "episode")
    var wtEntryOpen by remember(detail.contentId) { mutableStateOf(false) }
    var wtJoinOpen by remember(detail.contentId) { mutableStateOf(false) }
    val wtViewModel: TvWatchTogetherViewModel = koinViewModel()
    val wtState by wtViewModel.uiState.collectAsState()
    val resumePosition = remember(detail.userData) { detail.resumePositionSeconds() }
    val hasResume = resumePosition != null
    val hasVersionPicker = remember(detail.versions) { detail.versions.hasTvVersionChoices() }
    val qualitySummary = remember(detail.versions) { detail.versionSummaryLabel() }
    var mediaInfoOpen by remember(detail.contentId) { mutableStateOf(false) }
    val hasOverflowMenu = (detail.type == "episode" && detail.seriesId != null) ||
        detail.versions.isNotEmpty()
    val selectedFileId = state.selectedFileId ?: detail.versions.firstOrNull()?.fileId

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TvHeroActionPill(
                label = if (hasResume) {
                    "Resume ${resumePosition!!.formatHms()}"
                } else {
                    "Play"
                },
                icon = Icons.Filled.PlayArrow,
                variant = TvPillVariant.Filled,
                onClick = { onPlay(detail.contentId, selectedFileId, detail.type, resumePosition) },
                focusRequester = playFocus,
                modifier = Modifier.widthIn(min = 185.dp),
                heightOverride = 52.dp,
                horizontalPaddingOverride = 26.dp,
            )

            if (hasResume) {
                TvHeroActionPill(
                    label = "Start Over",
                    icon = Icons.Filled.Replay,
                    variant = TvPillVariant.Hollow,
                    onClick = { onPlay(detail.contentId, selectedFileId, detail.type, 0.0) },
                )
            }

            CircleAction(
                icon = if (state.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                onClick = viewModel::onToggleFavorite,
                contentDescription = if (state.isFavorite) "Remove from favorites" else "Add to favorites",
                isActive = state.isFavorite,
            )

            CircleAction(
                icon = if (state.inWatchlist) Icons.Filled.BookmarkAdded else Icons.Outlined.BookmarkBorder,
                onClick = viewModel::onToggleWatchlist,
                contentDescription = if (state.inWatchlist) "Remove from watchlist" else "Add to watchlist",
                isActive = state.inWatchlist,
            )

            CircleAction(
                icon = Icons.Filled.CheckCircle,
                onClick = {},
                contentDescription = if (detail.userData?.played == true) "Watched" else "Mark as watched",
                isActive = detail.userData?.played == true,
            )

            CircleAction(
                icon = if (state.userRating != null) Icons.Filled.Star else Icons.Outlined.StarBorder,
                onClick = { ratingOpen = true },
                contentDescription = state.userRating?.let { "Rated $it of 5" } ?: "Rate",
                isActive = state.userRating != null,
            )

            if (showsWatchTogether) {
                CircleAction(
                    icon = Icons.Filled.Groups,
                    onClick = {
                        wtViewModel.clearError()
                        wtEntryOpen = true
                    },
                    contentDescription = "Watch Together",
                    isActive = false,
                )
            }

            if (hasOverflowMenu) {
                CircleAction(
                    icon = Icons.Filled.MoreHoriz,
                    onClick = { moreOpen = true },
                    contentDescription = "More options",
                    isActive = false,
                )
            }
        }

        // Audiobooks have no meaningful video "version"/quality (the "720p" was
        // the cover-art mjpeg stream); hide the version/quality picker for them.
        if (!isAudiobookItemType(detail.type) && (hasVersionPicker || qualitySummary != null)) {
            TvVersionPicker(
                versions = detail.versions,
                fallbackLabel = qualitySummary,
                selectedFileId = state.selectedFileId,
                lastFileId = detail.userData?.lastFileId,
                onSelectedFileId = viewModel::onVersionSelected,
                modifier = Modifier
                    .padding(start = 18.dp)
                    .widthIn(min = 120.dp),
            )
        }
    }

    if (moreOpen && hasOverflowMenu) {
        val options = buildList {
            detail.seriesId?.let { seriesId ->
                add(
                    TvDialogOption(
                        key = "series",
                        title = "View Series",
                        subtitle = detail.seriesTitle,
                        onClick = {
                            moreOpen = false
                            onSeriesClick(seriesId)
                        },
                    ),
                )
                detail.seasonNumber?.takeIf { it > 0 }?.let { season ->
                    add(
                        TvDialogOption(
                            key = "season-$season",
                            title = "Go to Season $season",
                            subtitle = detail.seriesTitle,
                            onClick = {
                                moreOpen = false
                                onSeasonClick(seriesId, season)
                            },
                        ),
                    )
                }
            }
            if (detail.versions.isNotEmpty()) {
                add(
                    TvDialogOption(
                        key = "media-info",
                        title = "Media info",
                        subtitle = null,
                        onClick = {
                            moreOpen = false
                            mediaInfoOpen = true
                        },
                    ),
                )
            }
        }
        TvOptionDialog(
            title = "More Actions",
            options = options,
            onDismiss = { moreOpen = false },
        )
    }

    if (mediaInfoOpen) {
        TvMediaInfoDialog(
            versions = detail.versions,
            onDismiss = { mediaInfoOpen = false },
        )
    }

    if (ratingOpen) {
        TvRatingDialog(
            currentRating = state.userRating,
            onSetRating = { stars ->
                ratingOpen = false
                viewModel.onSetRating(stars)
            },
            onClearRating = {
                ratingOpen = false
                viewModel.onClearRating()
            },
            onDismiss = { ratingOpen = false },
        )
    }

    // A create/join resolved — close the dialogs and route on the snapshot.
    LaunchedEffect(wtState.result) {
        wtState.result?.let { snapshot ->
            wtEntryOpen = false
            wtJoinOpen = false
            onWatchTogether(snapshot)
            wtViewModel.consumeResult()
        }
    }

    if (wtEntryOpen) {
        TvWatchTogetherEntryDialog(
            isBusy = wtState.isBusy,
            error = wtState.error,
            onHost = {
                val fileId = state.selectedFileId ?: detail.versions.firstOrNull()?.fileId
                wtViewModel.createRoom(detail.contentId, fileId)
            },
            onJoin = {
                wtViewModel.clearError()
                wtEntryOpen = false
                wtJoinOpen = true
            },
            onDismiss = { wtEntryOpen = false },
        )
    }

    if (wtJoinOpen) {
        TvJoinCodeDialog(
            isBusy = wtState.isBusy,
            error = wtState.error,
            onJoin = { code -> wtViewModel.joinRoom(code) },
            onDismiss = { wtJoinOpen = false },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CircleAction(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    isActive: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = CircleShape

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.34f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            pressedContainerColor = Color.White,
            pressedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.32f)),
                shape = shape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.0.dp, Color.Black.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.16f),
                elevation = 14.dp,
            ),
        ),
        modifier = Modifier
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.98f),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .size(38.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EpisodesSection(
    detail: ItemDetail,
    state: TvItemDetailUiState,
    showsSeasonChips: Boolean,
    firstEpisodeFocus: FocusRequester,
    onReturnToHero: () -> Boolean,
    onSeasonSelected: (com.continuum.app.model.catalog.Season) -> Unit,
    onEpisodeSelected: (EpisodeListItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.safeArea),
            verticalAlignment = Alignment.Bottom,
        ) {
            TvDetailSectionHeader(
                eyebrow = episodeEyebrowLabel(detail, state),
                title = "Episodes",
            )
            Spacer(modifier = Modifier.weight(1f))
            val count = state.episodes.size
            if (count > 0) {
                Text(
                    text = "$count episode${if (count == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                    ),
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }

        if (showsSeasonChips) {
            TvSeasonPicker(
                seasons = state.seasons,
                selectedSeason = state.selectedSeason,
                onSeasonSelected = onSeasonSelected,
                onDirectionUp = onReturnToHero,
                modifier = Modifier.padding(horizontal = Spacing.safeArea),
            )
        }

        TvDetailEpisodeRail(
            episodes = state.episodes,
            currentContentId = detail.contentId.takeIf { detail.type == "episode" },
            onEpisodeSelected = onEpisodeSelected,
            firstItemFocusRequester = firstEpisodeFocus,
        )
    }
}

private fun episodeEyebrowLabel(detail: ItemDetail, state: TvItemDetailUiState): String {
    state.selectedSeason?.takeIf { it > 0 }?.let { return "Season $it" }
    if (detail.type == "episode") {
        detail.seasonNumber?.takeIf { it > 0 }?.let { return "Season $it" }
    }
    return "This Season"
}

@Composable
private fun DetailsSection(
    detail: ItemDetail,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.focusableDetailSection(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        TvDetailSectionHeader(eyebrow = "Info", title = "Details")
        TvDetailFactsTable(detail = detail)
    }
}

/**
 * Makes an otherwise-inert detail text section focusable WITH a visible focused
 * state (a subtle tinted background + hairline border). D-pad users land on
 * Details/About to scroll them into view; without a visible state the focus
 * reads as a dead end. A constant inner padding keeps layout stable so the
 * content doesn't shift when the highlight appears.
 */
@Composable
private fun Modifier.focusableDetailSection(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    return this
        .then(
            if (isFocused) {
                Modifier
                    .background(Color.White.copy(alpha = 0.06f), shape)
                    .border(1.dp, Color.White.copy(alpha = 0.30f), shape)
            } else {
                Modifier
            },
        )
        .focusable(interactionSource = interactionSource)
        .padding(16.dp)
}

@Composable
private fun AboutSection(
    detail: ItemDetail,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 1400.dp)
            .focusableDetailSection(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        TvDetailSectionHeader(
            eyebrow = "About",
            title = aboutTitle(detail),
        )

        if (!detail.tagline.isNullOrBlank()) {
            Text(
                text = detail.tagline!!,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    fontSize = 30.sp,
                ),
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        if (!detail.overview.isNullOrBlank()) {
            Text(
                text = detail.overview!!,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.82f),
                lineHeight = 35.sp,
            )
        }
    }
}

private fun aboutTitle(detail: ItemDetail): String = when (detail.type.lowercase()) {
    "movie" -> "The Movie"
    "series" -> "The Series"
    "episode" -> "Episode"
    else -> detail.type.replaceFirstChar { it.titlecase() }
}

// MARK: - Helpers

private fun ItemDetail.resumePositionSeconds(): Double? {
    val user = userData ?: return null
    val pos = user.positionSeconds ?: return null
    val dur = user.durationSeconds ?: return null
    if (pos <= 30 || dur <= 0 || pos >= dur - 5) return null
    return pos
}

private fun ItemDetail.versionSummaryLabel(): String? {
    val tokens = TvDetailMetadata.factsLine(this)
        .mapNotNull { (it as? TvHeroFactToken.Chip)?.value }
        .filterNot { it.equals("CC", ignoreCase = true) }
    return tokens.joinToString(" · ").ifBlank { null }
}

private fun Double.formatHms(): String {
    val totalSeconds = roundToInt().coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
