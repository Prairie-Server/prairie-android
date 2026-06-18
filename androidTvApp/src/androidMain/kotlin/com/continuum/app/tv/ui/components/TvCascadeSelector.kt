package com.continuum.app.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.continuum.app.model.personal.UserLibrary
import com.continuum.app.tv.ui.shell.TvLibraryPill
import com.continuum.app.tv.ui.shell.TvLibraryTabType
import com.continuum.app.tv.ui.theme.ContinuumOnSurface
import com.continuum.app.tv.ui.theme.DarkBackground
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The Skyline cascading library selector — a faithful Compose-for-TV port of
 * tvOS `TVCascadeSelector` (§5.3). One component, two levels:
 *
 * - **Level 1 — libraries.** A row per real library of [type]. The current
 *   scope shows a `✓`; the others a `›` chevron. A single-library tab
 *   collapses this away and shows the sections directly.
 * - **Level 2 — sections flyout.** The pill set ([TvLibraryPill.set]) anchored
 *   to and vertically aligned with the focused/anchor library row. It follows
 *   focus up/down the list after a ~150 ms rest debounce.
 *
 * The component owns no scope state; every outcome is a callback so persistence
 * and the page swap stay with the host. The caller applies
 * [androidx.compose.ui.Modifier] panel chrome ([tvSkylinePanelChrome]); this
 * component is only the panel contents.
 *
 * Focus contract (matches tvOS §5.3/§7):
 * - On entry ([focusEntryToken] bump while [entersPanel]) focus lands on the
 *   current-scope library row (or the first). A single-library tab focuses the
 *   first section instead.
 * - **Right** on a library row enters the flyout; **Left** in the flyout
 *   returns to the anchored library row.
 * - **Select/Enter** on a library row commits that scope ([onCommitLibrary]);
 *   on a section row commits scope + section ([onCommitSection]).
 * - **Back/Escape** closes ([onClose]).
 */
@Composable
fun TvCascadeSelector(
    type: TvLibraryTabType,
    libraries: List<UserLibrary>,
    currentScopeId: Int?,
    selectedPill: TvLibraryPill,
    entersPanel: Boolean,
    focusEntryToken: Int,
    onCommitLibrary: (UserLibrary) -> Unit,
    onCommitSection: (UserLibrary, TvLibraryPill) -> Unit,
    onPanelFocusChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pills = remember(type) { TvLibraryPill.set(type) }
    val isSingleLibrary = libraries.size <= 1

    // One stable FocusRequester per library id and per pill, surviving recomposition.
    val libraryRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }
    val pillRequesters = remember { mutableStateMapOf<TvLibraryPill, FocusRequester>() }

    // Each library row's top edge in the level-1 column's coordinate space; the
    // flyout offsets down to the anchored row's value to align tops (§5.3).
    val rowTops = remember { mutableStateMapOf<Int, Float>() }

    // The library row whose flyout is currently shown.
    var anchorId by remember(libraries, currentScopeId) {
        mutableStateOf(currentScopeId ?: libraries.firstOrNull()?.id)
    }

    // Scroll state for the lazy (libraries.size > 6) level-1 list, so focus
    // entry can scroll the current-scope row into composition before focusing.
    val lazyListState = rememberLazyListState()

    // Which library row currently holds focus (drives the debounced follow).
    var focusedRowId by remember { mutableStateOf<Int?>(null) }
    // Whether any pill in the flyout currently holds focus.
    var focusedPill by remember { mutableStateOf<TvLibraryPill?>(null) }

    val anchorLibrary = libraries.firstOrNull { it.id == anchorId } ?: libraries.firstOrNull()

    // Drive onPanelFocusChanged from whether ANY row or pill is focused.
    val anyFocused = focusedRowId != null || focusedPill != null
    LaunchedEffect(anyFocused) { onPanelFocusChanged(anyFocused) }

    // Flyout follows the resting focused row after a ~150 ms debounce.
    LaunchedEffect(focusedRowId) {
        val id = focusedRowId ?: return@LaunchedEffect
        delay(150)
        if (focusedRowId == id) anchorId = id
    }

    // Focus entry: land on current-scope row (or first), or first pill when single.
    LaunchedEffect(focusEntryToken) {
        if (entersPanel && focusEntryToken > 0) {
            if (isSingleLibrary) {
                pills.firstOrNull()?.let { pillRequesters[it]?.requestFocus() }
            } else {
                val target = currentScopeId ?: libraries.firstOrNull()?.id
                target?.let { id ->
                    anchorId = id
                    // When the list is lazy (size > 6) the target row may be
                    // offscreen / un-composed, so its FocusRequester is not yet
                    // attached. Scroll it into view first, then request focus.
                    if (libraries.size > 6) {
                        val index = libraries.indexOfFirst { it.id == id }
                        lazyListState.scrollToItem(index.coerceAtLeast(0))
                    }
                    libraryRequesters[id]?.requestFocus()
                }
            }
        }
    }

    // Back/Escape is intentionally NOT handled here. It is centralized at the
    // shell-level Back handler in TvMainShell, which closes any open panel
    // first. Handling it here too would double-consume (close + nav-pop).
    Row(
        modifier = modifier
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // LEVEL 1 — library rows (skipped entirely for a single-library tab).
        if (!isSingleLibrary) {
            val rowsContent: @Composable () -> Unit = {
                libraries.forEach { library ->
                    val requester = libraryRequesters.getOrPut(library.id) { FocusRequester() }
                    CascadeLibraryRow(
                        library = library,
                        type = type,
                        isCurrent = library.id == currentScopeId,
                        entersPanel = entersPanel,
                        focusRequester = requester,
                        onFocusChanged = { focused ->
                            focusedRowId = if (focused) {
                                library.id
                            } else {
                                focusedRowId.takeUnless { it == library.id }
                            }
                        },
                        onTopChanged = { top -> rowTops[library.id] = top },
                        onMoveRight = {
                            anchorId = library.id
                            val firstPill = pills.firstOrNull()
                            if (firstPill != null) {
                                pillRequesters[firstPill]?.requestFocus()
                                true
                            } else {
                                false
                            }
                        },
                        onSelect = {
                            onCommitLibrary(library)
                            true
                        },
                    )
                }
            }

            if (libraries.size > 6) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .widthIn(min = 280.dp)
                        .heightIn(max = 360.dp),
                ) {
                    items(libraries) { library ->
                        val requester = libraryRequesters.getOrPut(library.id) { FocusRequester() }
                        CascadeLibraryRow(
                            library = library,
                            type = type,
                            isCurrent = library.id == currentScopeId,
                            entersPanel = entersPanel,
                            focusRequester = requester,
                            onFocusChanged = { focused ->
                                focusedRowId = if (focused) {
                                    library.id
                                } else {
                                    focusedRowId.takeUnless { it == library.id }
                                }
                            },
                            onTopChanged = { top -> rowTops[library.id] = top },
                            onMoveRight = {
                                anchorId = library.id
                                val firstPill = pills.firstOrNull()
                                if (firstPill != null) {
                                    pillRequesters[firstPill]?.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            },
                            onSelect = {
                                onCommitLibrary(library)
                                true
                            },
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.widthIn(min = 280.dp)) {
                    rowsContent()
                }
            }
        }

        // LEVEL 2 — sections flyout. For a single-library tab it is the only
        // column; otherwise it is offset down to align with the anchor row.
        val flyoutOffset = if (isSingleLibrary) 0f else (rowTops[anchorId] ?: 0f)
        if (anchorLibrary != null) {
            Column(
                modifier = Modifier
                    .offset { IntOffset(0, flyoutOffset.roundToInt()) }
                    .width(260.dp),
            ) {
                pills.forEach { pill ->
                    val requester = pillRequesters.getOrPut(pill) { FocusRequester() }
                    CascadeSectionRow(
                        pill = pill,
                        entersPanel = entersPanel,
                        focusRequester = requester,
                        onFocusChanged = { focused ->
                            focusedPill = if (focused) pill else focusedPill.takeUnless { it == pill }
                        },
                        onMoveLeft = {
                            if (isSingleLibrary) {
                                false
                            } else {
                                val target = anchorId ?: libraries.firstOrNull()?.id
                                if (target != null) {
                                    libraryRequesters[target]?.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            }
                        },
                        onSelect = {
                            onCommitSection(anchorLibrary, pill)
                            true
                        },
                    )
                }
            }
        }
    }
}

/**
 * Level-1 library row (§5.3): type icon — name — trailing `✓`/`›`. Inverts to
 * a solid [ContinuumOnSurface] capsule with [DarkBackground] content on focus,
 * matching the bar's focused tab; bare row at rest.
 */
@Composable
private fun CascadeLibraryRow(
    library: UserLibrary,
    type: TvLibraryTabType,
    isCurrent: Boolean,
    entersPanel: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onTopChanged: (Float) -> Unit,
    onMoveRight: () -> Boolean,
    onSelect: () -> Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { onFocusChanged(isFocused) }

    CascadeRowChrome(
        icon = type.icon,
        title = library.name,
        trailingIcon = if (isCurrent) Icons.Filled.Check else Icons.Filled.ChevronRight,
        isFocused = isFocused,
        interactionSource = interactionSource,
        focusRequester = focusRequester,
        focusable = entersPanel,
        onTopChanged = onTopChanged,
        onKey = { event ->
            if (event.type != KeyEventType.KeyDown) return@CascadeRowChrome false
            when (event.key) {
                Key.DirectionRight -> onMoveRight()
                Key.DirectionCenter, Key.Enter -> onSelect()
                else -> false
            }
        },
    )
}

/**
 * Flyout / single-level section row (§5.3): pill icon — title, inverting to a
 * solid [ContinuumOnSurface] capsule on focus.
 */
@Composable
private fun CascadeSectionRow(
    pill: TvLibraryPill,
    entersPanel: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onMoveLeft: () -> Boolean,
    onSelect: () -> Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { onFocusChanged(isFocused) }

    CascadeRowChrome(
        icon = pill.icon,
        title = pill.title,
        trailingIcon = null,
        isFocused = isFocused,
        interactionSource = interactionSource,
        focusRequester = focusRequester,
        focusable = entersPanel,
        onTopChanged = null,
        onKey = { event ->
            if (event.type != KeyEventType.KeyDown) return@CascadeRowChrome false
            when (event.key) {
                Key.DirectionLeft -> onMoveLeft()
                Key.DirectionCenter, Key.Enter -> onSelect()
                else -> false
            }
        },
    )
}

/**
 * Shared inverted-capsule row chrome for both levels: solid white fill +
 * background-colored content on focus, bare at rest. The [androidx.compose.ui.input.key.Key]
 * contract is handled by the caller via [onKey]; this row is focusable only
 * while [focusable] (the host has handed focus into the panel).
 */
@Composable
private fun CascadeRowChrome(
    icon: ImageVector,
    title: String,
    trailingIcon: ImageVector?,
    isFocused: Boolean,
    interactionSource: MutableInteractionSource,
    focusRequester: FocusRequester,
    focusable: Boolean,
    onTopChanged: ((Float) -> Unit)?,
    onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean,
) {
    val shape = RoundedCornerShape(12.dp)
    val contentColor = if (isFocused) DarkBackground else ContinuumOnSurface.copy(alpha = 0.9f)

    var rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)
        .clip(shape)
        .background(if (isFocused) ContinuumOnSurface else Color.Transparent)

    if (onTopChanged != null) {
        rowModifier = rowModifier.onGloballyPositioned { onTopChanged(it.positionInParent().y) }
    }

    rowModifier = rowModifier.focusRequester(focusRequester)

    if (focusable) {
        rowModifier = rowModifier
            .androidxFocusableRow(interactionSource)
            .onPreviewKeyEvent(onKey)
    }

    Row(
        modifier = rowModifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = title,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = contentColor.copy(alpha = if (isFocused) 1f else 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Makes a row focusable and routes its focus state into [interactionSource]. */
private fun Modifier.androidxFocusableRow(
    interactionSource: MutableInteractionSource,
): Modifier = this.focusable(interactionSource = interactionSource)
