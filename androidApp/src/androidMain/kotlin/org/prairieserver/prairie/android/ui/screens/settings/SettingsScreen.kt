package org.prairieserver.prairie.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ConnectedTv
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.prairieserver.prairie.android.BuildConfig
import org.prairieserver.prairie.android.R
import org.prairieserver.prairie.android.ui.components.SignOutConfirmDialog
import org.prairieserver.prairie.android.ui.components.PrairieTopBar
import org.prairieserver.prairie.android.ui.screens.settings.diagnostics.DiagnosticsViewModel
import org.prairieserver.prairie.android.ui.screens.settings.diagnostics.shouldShowDiagnosticsEntry
import org.prairieserver.prairie.android.ui.theme.SettingsDimens
import org.prairieserver.prairie.android.ui.theme.SettingsTextStyles
import org.prairieserver.prairie.android.ui.theme.PrairieOnSurface
import org.prairieserver.prairie.android.ui.theme.PrairieSecondaryText
import org.prairieserver.prairie.android.ui.theme.PrairieSettingsBackground
import org.prairieserver.prairie.android.ui.theme.PrairieSurfaceContainer
import org.prairieserver.prairie.android.ui.theme.PrairieSurfaceContainerHigh
import org.prairieserver.prairie.android.ui.theme.siloRowTopDivider
import org.prairieserver.prairie.android.ui.util.formatBytes
import org.prairieserver.prairie.model.download.DownloadQuality
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.prairieserver.prairie.model.feature.MetadataAiFeatureStore
import org.prairieserver.prairie.model.metadata.MetadataAiOnView
import org.prairieserver.prairie.common.diagnostics.DiagnosticsAvailabilityUi
import org.prairieserver.prairie.common.network.clientVersionLabel
import org.prairieserver.prairie.common.settings.CardPresentationSupport
import org.prairieserver.prairie.model.settings.LanguageOptions
import org.prairieserver.prairie.model.settings.QualityPresets
import org.prairieserver.prairie.model.settings.SettingKeys

/** Prairie's privacy policy, linked from About and from Diagnostics. */
internal const val PRAIRIE_PRIVACY_POLICY_URL = "https://prairieserver.org/privacy"

/**
 * The Settings overview, laid out like the Apple apps' (prairie-apple
 * `IOSSettingsOverview`): a large title, the account card, a search field,
 * then grouped one-line rows with graphite icon tiles that open the sub-pages.
 *
 * Android adds what the Apple apps keep elsewhere: Notifications, the Library
 * shortcuts (Watchlist, Favorites, History, Collections — reachable only from
 * here on the phone) and Sign in a TV. Each sits in the group it belongs to
 * rather than in a group of its own. The Sign-in group
 * (the server's external sign-in providers) appears only on servers that
 * offer one.
 */
@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    onOpenInterface: () -> Unit = {},
    onOpenPlayback: () -> Unit = {},
    onOpenSubtitles: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onNavigateToServers: () -> Unit = {},
    onPairDevice: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToCollections: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
    diagnosticsViewModel: DiagnosticsViewModel = koinViewModel(),
    signInViewModel: SignInSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val signInState by signInViewModel.uiState.collectAsState()
    val diagnosticsState by diagnosticsViewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    var query by rememberSaveable { mutableStateOf("") }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // The large title scrolls away and the bar takes the title over, the way
    // a large navigation title collapses to an inline one.
    val titleScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val watchPartyExperiment: org.prairieserver.prairie.common.watchparty.WatchPartyExperiment =
        org.koin.compose.koinInject()
    val watchPartyEnabled by watchPartyExperiment.enabled.collectAsState()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            viewModel.onLogoutConsumed()
            onLoggedOut()
        }
    }

    val search = SettingsSearch(query)
    val versionLabel = clientVersionLabel(BuildConfig.VERSION_NAME, BuildConfig.BUILD_NUMBER)
    val serverLabel = state.serverName.ifBlank { serverHost(state.serverUrl) ?: "Not connected" }
    val showDiagnostics = shouldShowDiagnosticsEntry(diagnosticsState) &&
        search.matches("diagnostics", "support", "reports", "debug", "crash")

    val matchesInterface = search.matches(
        "interface", "appearance", "cards", "posters", "captions", "home", "sections",
        "title art", "logos",
    )
    val matchesNotifications = state.notificationsAvailable && search.matches(
        "notifications", "alerts", "favorites", "watchlist", "continue watching", "next up",
    )
    val matchesPlayback = search.matches(
        "playback", "quality", "audio", "dolby vision", "picture-in-picture", "episodes", "next up",
        "skip", "intros", "credits", "rewind", "still watching", "skip interval", "audiobooks",
        "spoilers", "unwatched", "blur", "descriptions",
    )
    val matchesSubtitles = search.matches(
        "subtitles", "captions", "language", "behavior", "appearance", "forced", "metadata",
    )
    val matchesDownloads = search.matches("downloads", "offline", "quality", "wi-fi", "cleanup", "storage")
    val matchesWatchlist = search.matches("library", "watchlist")
    val matchesFavorites = search.matches("library", "favorites")
    val matchesHistory = search.matches("library", "history", "watch history")
    val matchesCollections = search.matches("library", "collections")
    val matchesServer = search.matches("server", "connection", serverLabel)
    val matchesPairDevice = search.matches("connection", "pair", "device", "tv", "link", "sign in")
    // External sign-in (OIDC/LDAP): shown only on servers that have it.
    val matchesSignIn = signInState.visible &&
        search.matches("sign-in", "sign in", "account", "provider", "connect", "disconnect", "sso")
    val matchesExperimental = search.matches(
        "experimental", "beta", "testing", "audiobooks", "navigation", "watch party", "party",
    )
    val matchesAbout = search.matches("about", "version", versionLabel, "privacy", "policy", "information")
    val matchesSignOut = search.matches("sign out", "account")

    val hasResults = matchesInterface || matchesNotifications || matchesPlayback || matchesSubtitles ||
        matchesDownloads || matchesWatchlist || matchesFavorites || matchesHistory || matchesCollections ||
        showDiagnostics || matchesServer || matchesPairDevice || matchesExperimental ||
        matchesAbout || matchesSignOut || matchesSignIn

    Scaffold(
        topBar = {
            PrairieTopBar(
                title = if (titleScrolledAway) "Settings" else "",
                onBackClick = onBackClick,
                containerColor = PrairieSettingsBackground,
                centerTitle = true,
            )
        },
        containerColor = PrairieSettingsBackground,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = SettingsDimens.pageGutter,
                end = SettingsDimens.pageGutter,
                bottom = SettingsDimens.pageBottomSpacer,
            ),
            verticalArrangement = Arrangement.spacedBy(SettingsDimens.sectionGap),
        ) {
            item(key = "title") {
                Column {
                    Text(
                        text = "Settings",
                        style = SettingsTextStyles.largeTitle,
                        color = PrairieOnSurface,
                    )
                    Spacer(modifier = Modifier.height(SettingsDimens.headerBottomGap))
                    SettingsAccountCard(
                        profile = state.activeProfile,
                        user = state.user,
                        serverHost = serverHost(state.serverUrl),
                        onClick = onSwitchProfile,
                    )
                }
            }

            item(key = "search") {
                SettingsSearchField(query = query, onQueryChange = { query = it })
            }

            if (!hasResults) {
                item(key = "no-results") {
                    Text(
                        text = "No Results for “${query.trim()}”",
                        style = SettingsTextStyles.rowLabel,
                        color = PrairieSecondaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                    )
                }
            }

            if (matchesInterface || matchesNotifications) {
                item(key = "preferences") {
                    SettingsSection(title = "Preferences") {
                        if (matchesInterface) {
                            SettingsNavigationRow(
                                label = "Interface",
                                icon = Icons.Filled.Dashboard,
                                value = state.cardPresentation.takeIf {
                                    it.support != CardPresentationSupport.Unsupported
                                }?.let { it.presentation.preset?.displayName ?: "Custom" },
                                onClick = onOpenInterface,
                            )
                        }
                        if (matchesNotifications) {
                            SettingsNavigationRow(
                                label = "Notifications",
                                icon = Icons.Filled.Notifications,
                                value = if (state.notificationsEnabled) "On" else "Off",
                                onClick = onOpenNotifications,
                            )
                        }
                    }
                }
            }

            if (matchesPlayback || matchesSubtitles || matchesDownloads) {
                item(key = "playback") {
                    SettingsSection(title = "Playback") {
                        if (matchesPlayback) {
                            SettingsNavigationRow(
                                label = "Playback",
                                icon = Icons.Filled.PlayArrow,
                                value = QualityPresets.describe(state.qualityResolution, state.maxBitrateKbps),
                                onClick = onOpenPlayback,
                            )
                        }
                        if (matchesSubtitles) {
                            SettingsNavigationRow(
                                label = "Subtitles",
                                icon = Icons.Filled.Subtitles,
                                value = LanguageOptions.label(state.subtitleLanguage, SettingKeys.PLAYBACK_SUBTITLE_LANGUAGE),
                                onClick = onOpenSubtitles,
                            )
                        }
                        if (matchesDownloads) {
                            SettingsNavigationRow(
                                label = "Downloads",
                                icon = Icons.Filled.ArrowCircleDown,
                                onClick = onOpenDownloads,
                            )
                        }
                    }
                }
            }

            if (matchesWatchlist || matchesFavorites || matchesHistory || matchesCollections) {
                item(key = "library") {
                    SettingsSection(title = "Library") {
                        if (matchesWatchlist) {
                            SettingsNavigationRow(
                                label = "Watchlist",
                                icon = Icons.Filled.Bookmark,
                                onClick = onNavigateToWatchlist,
                            )
                        }
                        if (matchesFavorites) {
                            SettingsNavigationRow(
                                label = "Favorites",
                                icon = Icons.Filled.Favorite,
                                onClick = onNavigateToFavorites,
                            )
                        }
                        if (matchesHistory) {
                            SettingsNavigationRow(
                                label = "Watch History",
                                icon = Icons.Filled.History,
                                onClick = onNavigateToHistory,
                            )
                        }
                        if (matchesCollections) {
                            SettingsNavigationRow(
                                label = "Collections",
                                icon = Icons.Filled.CollectionsBookmark,
                                onClick = onNavigateToCollections,
                            )
                        }
                    }
                }
            }

            if (showDiagnostics) {
                item(key = "support") {
                    SettingsSection(title = "Support") {
                        SettingsNavigationRow(
                            label = "Diagnostics",
                            icon = Icons.Filled.MonitorHeart,
                            value = diagnosticsAvailabilityLabel(diagnosticsState.availability),
                            onClick = onNavigateToDiagnostics,
                        )
                    }
                }
            }

            if (matchesServer || matchesPairDevice) {
                item(key = "connection") {
                    SettingsSection(title = "Connection") {
                        if (matchesServer) {
                            SettingsNavigationRow(
                                label = "Server",
                                icon = Icons.Filled.Dns,
                                value = serverLabel,
                                onClick = onNavigateToServers,
                            )
                        }
                        if (matchesPairDevice) {
                            SettingsNavigationRow(
                                label = stringResource(R.string.sign_in_tv_settings_label),
                                icon = Icons.Filled.ConnectedTv,
                                onClick = onPairDevice,
                            )
                        }
                    }
                }
            }

            if (matchesSignIn) {
                item(key = "sign-in") {
                    SignInSection(viewModel = signInViewModel)
                }
            }

            if (matchesExperimental) {
                item(key = "experimental") {
                    SettingsSection(title = "Experimental") {
                        SettingsSwitchRow(
                            label = "Show Audiobooks",
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            checked = state.showAudiobooks,
                            onCheckedChange = viewModel::setShowAudiobooks,
                        )
                        // Device-local; never synced with the server's settings.
                        SettingsSwitchRow(
                            label = "Watch Party",
                            description = "Try Watch Party before it's finished. Turning it off leaves any party you're in.",
                            icon = Icons.Filled.Groups,
                            checked = watchPartyEnabled,
                            onCheckedChange = watchPartyExperiment::setEnabled,
                        )
                    }
                }
            }

            if (matchesAbout) {
                item(key = "about") {
                    SettingsSection(title = "About") {
                        // Includes the build number so a support report and the
                        // server's admin Activity page name the exact same build,
                        // in the "1.0.0 (5)" form Play, TestFlight and the server's
                        // diagnostics page all use.
                        SettingsNavigationRow(
                            label = "Version",
                            icon = Icons.Filled.Info,
                            value = versionLabel,
                        )
                        SettingsNavigationRow(
                            label = "Privacy Policy",
                            icon = Icons.Filled.PanTool,
                            onClick = { uriHandler.openUri(PRAIRIE_PRIVACY_POLICY_URL) },
                        )
                    }
                }
            }

            item {
                ServerInfoSection(
                    serverUrl = state.serverUrl,
                    onManageServersClick = onNavigateToServers,
                )
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(SettingsDimens.pageBottomSpacer)) }
        }
    }
    if (showRemoveAllDownloadsConfirm) {
        PrairieConfirmDialog(
            title = "Remove all downloads?",
            body = "This removes ${formatBytes(downloadsState.totalBytesUsed)} of downloaded files " +
                "from this device. Your library and server media stay intact.",
            confirmLabel = if (downloadsState.isRemovingAllDownloads) "Removing…" else "Remove all",
            confirmEnabled = !downloadsState.isRemovingAllDownloads,
            onConfirm = {
                showRemoveAllDownloadsConfirm = false
                downloadsViewModel.removeAllDownloads()
            },
            onDismiss = { showRemoveAllDownloadsConfirm = false },
        )
    if (showHomeSectionsEditor) {
        HomeSectionsEditor(onDismiss = { showHomeSectionsEditor = false })
}
// --- Shared Settings UI Components ---
//
// The grouped surface these build is the Silo web client's, adapted to Android
// row mechanics: an opaque card on a lifted page ground, a lettered heading
// above rather than inside it, a label over a muted description, and the
// control kept trailing rather than stacked underneath the way the web layout
// stacks it. Metrics live in `ui.theme.SettingsDimens` / `SettingsTextStyles`.
// There is deliberately no leading icon on any row. The web client puts icons
// only in its settings *sidebar*, never on a row; Android has no sidebar, so
// its destination rows sit inline among the value rows and an icon on half of
// them is exactly the "iOS Settings at the ends, unstyled form in the middle"
// split this pass removed. The description line is the scanning aid now, and
// the trailing affordance (chevron / value / switch) is what separates a
// destination from a setting.
/**
 * Per-card row counter backing the "no divider above the first row" rule.
 *
 * Rows claim a slot on first composition and remember it, so the index is
 * stable across recomposition and follows source order within the card. A card
 * whose *first* row is conditional would need [SettingsRow]'s `showDivider`
 * override — no section does that today, since every card's opening row is
 * unconditional.
 */
@Stable
internal class SettingsSectionSlots {
    private var next = 0
    fun claim(): Int = next++
internal val LocalSettingsSectionSlots = staticCompositionLocalOf<SettingsSectionSlots?> { null }
/** True for the first row composed into the enclosing [SettingsSectionCard]. */
@Composable
private fun isFirstSettingsRow(): Boolean {
    val slots = LocalSettingsSectionSlots.current ?: return true
    return remember(slots) { slots.claim() } == 0
 * Claims a row slot for a card child that is not a [SettingsRow] — the account
 * header, a prose pane — and reports whether it should draw a hairline above
 * itself. A custom child that skips this is invisible to the divider rule, and
 * the row after it would wrongly believe it is the card's first.
 * Pair with [settingsRowDivider].
fun settingsRowDividerVisible(): Boolean = !isFirstSettingsRow()
/** Draws the standard inter-row hairline along this element's top edge. */
fun Modifier.settingsRowDivider(show: Boolean): Modifier = settingsRowTopDivider(show)
// The hairline itself lives in `ui.theme` beside the tokens it draws with:
// the popup menus rule their rows the same way, and one line rendered by two
// implementations is how two lines end up different.
private fun Modifier.settingsRowTopDivider(show: Boolean): Modifier = siloRowTopDivider(show)
 * A settings group: a lettered heading sitting above its card.
 * The heading used to render *inside* the card as its first child, which is
 * what made every section start with a stray caps line on the same surface as
 * the rows. Its own KDoc always described the intended placement.
fun SettingsSection(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            SettingsSectionHeader(title)
        SettingsSectionCard(content = content)
 * Card container for a settings section, and the owner of the row dividers.
fun SettingsSectionCard(
    val slots = remember { SettingsSectionSlots() }
    CompositionLocalProvider(LocalSettingsSectionSlots provides slots) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SettingsDimens.cardRadius))
                .background(PrairieSurfaceContainer),
            content = content,
 * Section heading — uppercased, letter-spaced, muted, sitting above the card
 * with a small inset.
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = SettingsTextStyles.sectionHeader,
        color = PrairieMutedText,
        modifier = Modifier.padding(
            start = SettingsDimens.headerStartInset,
            end = SettingsDimens.headerStartInset,
            bottom = SettingsDimens.headerBottomGap,
        ),
    )
 * Disclosure chevron.
fun SettingsRowChevron(enabled: Boolean = true) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = PrairieMutedText.copy(alpha = if (enabled) 1f else SettingsDimens.disabledAlpha),
        modifier = Modifier.size(SettingsDimens.chevronSize),
 * The one settings row.
 * Every other row type in this package is this one with a different trailing
 * slot: label over an optional description, control trailing, a 60dp floor so
 * a described row and a bare row still read as the same list, and a hairline
 * above every row but the card's first.
 * A trailing [value] shares the *label's* line rather than sitting beside the
 * whole text block. That is the structural half of a real defect: with the
 * value beside the block, a long one ("30 seconds before end") squeezed the
 * description into a narrow column whose last line then ended a few dp from
 * the value, and the two read as touching. On the label's line the value can
 * never abut the description — the description runs the full width beneath it
 * — and the wider column costs a line rather than adding one, so the page gets
 * shorter, not taller. Only [trailing] controls (chevron, switch, radio) sit
 * beside the block now, and an icon at [SettingsDimens.rowTrailingGap] does
 * not read as a collision the way text does.
 * @param showDivider Overrides the automatic first-row rule. Only needed in a
 *   card whose opening row is conditional.
fun SettingsRow(
    label: String,
    description: String? = null,
    value: String? = null,
    labelColor: Color = PrairieForeground,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    val contentAlpha = if (enabled) 1f else SettingsDimens.disabledAlpha
    val divider = showDivider ?: !isFirstSettingsRow()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SettingsDimens.rowMinHeight)
            .settingsRowTopDivider(divider)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = SettingsDimens.rowHorizontalPadding,
                vertical = SettingsDimens.rowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SettingsDimens.rowLabelGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = SettingsTextStyles.rowLabel,
                    color = labelColor.copy(alpha = contentAlpha),
                    // Fills the line so the value stays trailing-aligned, and
                    // yields — by wrapping — when a capped value needs room.
                    modifier = Modifier.weight(1f),
                if (value != null) {
                    Spacer(modifier = Modifier.width(SettingsDimens.rowLabelValueGap))
                    SettingsRowValue(value = value, enabled = enabled)
            if (matchesSignOut) {
                item(key = "sign-out") {
                    SettingsSectionCard {
                        SettingsButtonRow(label = "Sign Out", onClick = { confirmSignOut = true })
                    }
                }
            }
        }
    }

    // Same dialog the profile menu raises, so the answer to "are you sure?"
    // does not depend on which of the two routes was taken.
    SignOutConfirmDialog(
        visible = confirmSignOut,
        accountName = state.user?.username,
        onConfirm = {
            confirmSignOut = false
            viewModel.logout()
        },
        onDismiss = { confirmSignOut = false },
    )
}

/**
 * The overview search (Apple `IOSSettingsOverview.matches`): a row shows when
 * any of its keywords contains the trimmed query, case-insensitively. An
 * empty query shows everything.
 */
private class SettingsSearch(query: String) {
    private val needle = query.trim()

    fun matches(vararg terms: String): Boolean =
        needle.isEmpty() || terms.any { it.contains(needle, ignoreCase = true) }
}

/** The host of [serverUrl], or null when there is no server. */
private fun serverHost(serverUrl: String): String? {
    if (serverUrl.isBlank()) return null
    return serverUrl
        .substringAfter("://", serverUrl)
        .substringBefore('/')
        .ifBlank { serverUrl }
}

/** The Diagnostics row's value, in the Apple apps' wording. */
private fun diagnosticsAvailabilityLabel(availability: DiagnosticsAvailabilityUi): String? = when (availability) {
    DiagnosticsAvailabilityUi.AVAILABLE -> "Available"
    DiagnosticsAvailabilityUi.DISABLED -> "Disabled by server"
    DiagnosticsAvailabilityUi.STORAGE_UNAVAILABLE -> "Storage unavailable"
    DiagnosticsAvailabilityUi.OFFLINE -> "Offline"
    DiagnosticsAvailabilityUi.INELIGIBLE -> null
}
