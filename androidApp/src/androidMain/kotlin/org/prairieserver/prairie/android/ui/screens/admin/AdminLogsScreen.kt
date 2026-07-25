package org.prairieserver.prairie.android.ui.screens.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.android.ui.components.PrairieTopBar
import org.prairieserver.prairie.android.ui.components.EmptyStateView
import org.prairieserver.prairie.android.ui.components.ErrorView
import org.prairieserver.prairie.android.ui.components.LoadingIndicator
import org.prairieserver.prairie.model.admin.AdminAuditEntry
import org.prairieserver.prairie.model.admin.AdminLogEntry
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.errorMessage
import org.prairieserver.prairie.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

// ---------------------------------------------------------------------------
// ViewModel (co-located, LibrariesViewModel idiom; registered in AndroidModule)
// ---------------------------------------------------------------------------

enum class AdminLogTab { App, Audit }

data class AdminLogsUiState(
    val tab: AdminLogTab = AdminLogTab.App,
    val level: String = LOG_LEVEL_ALL,
    val query: String = "",
    val component: String = "",
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val appEntries: List<AdminLogEntry> = emptyList(),
    val auditEntries: List<AdminAuditEntry> = emptyList(),
    val nextCursor: String? = null,
    val error: String? = null,
)

/**
 * Owns the admin logs list for both the App and Audit tabs. A first page is a
 * replace (cursor = null); near-end scroll appends the next page using the
 * server cursor. Filter inputs (level/query/component) are held as draft state
 * and only applied on [applyFilters] / [selectTab], which resets the list and
 * cursor before refetching. Generation-gated so an applied-filter refetch that
 * overlaps an in-flight load can't clobber newer results.
 *
 * The landed [AdminRepository] takes individual named log parameters rather
 * than a filter map, so [fetch] reads the normalised values back out of
 * [buildLogQuery] — keeping the trim/clamp/sentinel rules in one tested place.
 */
class AdminLogsViewModel(
    private val repository: AdminRepository,
) : ViewModel() {

    private var loadGeneration = 0
    private val _uiState = MutableStateFlow(AdminLogsUiState())
    val uiState: StateFlow<AdminLogsUiState> = _uiState.asStateFlow()

    init { load() }

    fun selectTab(tab: AdminLogTab) {
        if (tab == _uiState.value.tab) return
        _uiState.update {
            it.copy(
                tab = tab,
                appEntries = emptyList(),
                auditEntries = emptyList(),
                nextCursor = null,
            )
        }
        load()
    }

    fun onLevelChange(level: String) = _uiState.update { it.copy(level = level) }
    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }
    fun onComponentChange(component: String) = _uiState.update { it.copy(component = component) }

    fun applyFilters() {
        _uiState.update {
            it.copy(appEntries = emptyList(), auditEntries = emptyList(), nextCursor = null)
        }
        load()
    }

    fun load() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetch(generation, cursor = null)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.isLoading || state.isLoadingMore) return
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            fetch(generation, cursor = cursor)
        }
    }

    private suspend fun fetch(generation: Int, cursor: String?) {
        val s = _uiState.value
        val q = buildLogQuery(level = s.level, query = s.query, component = s.component)
        val level = q["level"]
        val query = q["q"]
        val component = q["component"]
        val limit = q["limit"]?.toIntOrNull() ?: LOG_PAGE_LIMIT

        when (s.tab) {
            AdminLogTab.App -> {
                val result = repository.getAppLogs(
                    level = level,
                    component = component,
                    query = query,
                    cursor = cursor,
                    limit = limit,
                )
                if (generation != loadGeneration) return
                when (result) {
                    is ApiResult.Success -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = null,
                            appEntries = if (cursor == null) {
                                result.data.entries
                            } else {
                                it.appEntries + result.data.entries
                            },
                            nextCursor = result.data.nextCursor,
                        )
                    }
                    is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = result.errorMessage("Failed to load logs"),
                        )
                    }
                }
            }
            AdminLogTab.Audit -> {
                // Audit endpoint has no free-text/level/component filter; only
                // the cursor + limit carry over from the shared query builder.
                val result = repository.getAuditLogs(
                    cursor = cursor,
                    limit = limit,
                )
                if (generation != loadGeneration) return
                when (result) {
                    is ApiResult.Success -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = null,
                            auditEntries = if (cursor == null) {
                                result.data.entries
                            } else {
                                it.auditEntries + result.data.entries
                            },
                            nextCursor = result.data.nextCursor,
                        )
                    }
                    is ApiResult.Error, is ApiResult.NetworkError -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = result.errorMessage("Failed to load audit logs"),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLogsScreen(
    onBackClick: () -> Unit,
    viewModel: AdminLogsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val listState = rememberLazyListState()
    val itemCount = when (state.tab) {
        AdminLogTab.App -> state.appEntries.size
        AdminLogTab.Audit -> state.auditEntries.size
    }
    val shouldLoadMore by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layout.totalItemsCount
            total > 0 && lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.nextCursor, itemCount) {
        if (shouldLoadMore && state.nextCursor != null && !state.isLoadingMore && !state.isLoading) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = { PrairieTopBar(title = "Logs", onBackClick = onBackClick) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == AdminLogTab.App,
                    onClick = { viewModel.selectTab(AdminLogTab.App) },
                    text = { Text("App") },
                )
                Tab(
                    selected = state.tab == AdminLogTab.Audit,
                    onClick = { viewModel.selectTab(AdminLogTab.Audit) },
                    text = { Text("Audit") },
                )
            }

            if (state.tab == AdminLogTab.App) {
                AppLogFilters(
                    level = state.level,
                    query = state.query,
                    component = state.component,
                    onLevelChange = viewModel::onLevelChange,
                    onQueryChange = viewModel::onQueryChange,
                    onComponentChange = viewModel::onComponentChange,
                    onApply = viewModel::applyFilters,
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && itemCount == 0 -> LoadingIndicator()

                    state.error != null && itemCount == 0 ->
                        ErrorView(state.error!!, onRetry = viewModel::load)

                    itemCount == 0 -> EmptyStateView(
                        title = "No log entries",
                        subtitle = "Nothing matches the current filters.",
                        icon = Icons.Outlined.Article,
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (state.tab) {
                            AdminLogTab.App -> items(
                                state.appEntries,
                                key = { it.id },
                            ) { entry -> AppLogRow(entry) }

                            AdminLogTab.Audit -> items(
                                state.auditEntries,
                                key = { it.id },
                            ) { entry -> AuditLogRow(entry) }
                        }
                        if (state.isLoadingMore) {
                            item(key = "loading-more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLogFilters(
    level: String,
    query: String,
    component: String,
    onLevelChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onComponentChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    var levelMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                TextButton(onClick = { levelMenuExpanded = true }) {
                    Text("Level: $level")
                }
                DropdownMenu(
                    expanded = levelMenuExpanded,
                    onDismissRequest = { levelMenuExpanded = false },
                ) {
                    LOG_LEVELS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                levelMenuExpanded = false
                                onLevelChange(option)
                                onApply()
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = component,
                onValueChange = onComponentChange,
                label = { Text("Component") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onApply() },
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { onApply() },
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLogRow(entry: AdminLogEntry) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LevelBadge(entry.level)
                Text(
                    text = entry.component,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entry.requestId?.let { DetailLine("request", it) }
                    entry.clientIp?.let { DetailLine("ip", it) }
                    entry.userId?.let { DetailLine("user", it.toString()) }
                    entry.sessionId?.let { DetailLine("session", it) }
                    entry.nodeId?.let { DetailLine("node", it) }
                    // attrs values are kotlinx JsonElement, whose type isn't on
                    // the androidApp classpath; surface the present keys only.
                    entry.attrs?.takeIf { it.isNotEmpty() }?.let { attrs ->
                        DetailLine("attrs", attrs.keys.joinToString(", "))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditLogRow(entry: AdminAuditEntry) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = auditSummaryLine(entry.method, entry.path, entry.statusCode),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${entry.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entry.requestId?.let { DetailLine("request", it) }
                    DetailLine("ip", entry.clientIp)
                    entry.userId?.let { DetailLine("user", it.toString()) }
                    entry.impersonatorUserId?.let { DetailLine("impersonator", it.toString()) }
                    entry.sessionId?.let { DetailLine("session", it) }
                    entry.userAgent?.let { DetailLine("agent", it) }
                }
            }
        }
    }
}

@Composable
private fun LevelBadge(level: String) {
    val color = when (logLevelRank(level)) {
        4 -> MaterialTheme.colorScheme.error
        3 -> MaterialTheme.colorScheme.tertiary
        2 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.18f),
    ) {
        Text(
            text = level.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}
