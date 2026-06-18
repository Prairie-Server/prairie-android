package com.continuum.app.tv.ui.screens.requests

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.model.request.RequestMediaDetail
import com.continuum.app.tv.ui.components.TvErrorScreen
import com.continuum.app.tv.ui.components.TvLoadingScreen
import com.continuum.app.tv.ui.theme.ContinuumBlue
import com.continuum.app.tv.ui.theme.sectionEyebrow
import com.continuum.app.viewmodel.RequestDetailViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * TV request detail — the 10-foot counterpart to the phone RequestDetailScreen.
 * Reuses the shared [RequestDetailViewModel] (load + submitRequest) keyed by
 * (mediaType, tmdbId). Shows title/metadata/genres/overview and a Request
 * action when the title is requestable, plus the current request status.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvRequestDetailScreen(
    mediaType: String,
    tmdbId: Int,
    onBack: () -> Unit,
    viewModel: RequestDetailViewModel = koinViewModel { parametersOf(mediaType, tmdbId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.isLoading && state.detail == null -> TvLoadingScreen()
            state.error != null && state.detail == null -> TvErrorScreen(
                message = state.error ?: "Failed to load this title.",
                onRetry = viewModel::load,
            )
            state.detail != null -> RequestDetailContent(
                detail = state.detail!!,
                isSubmitting = state.isSubmitting,
                notice = state.notice,
                error = state.error,
                onRequest = viewModel::submitRequest,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RequestDetailContent(
    detail: RequestMediaDetail,
    isSubmitting: Boolean,
    notice: String?,
    error: String?,
    onRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "REQUEST",
            style = sectionEyebrow,
            color = ContinuumBlue.copy(alpha = 0.92f),
        )
        Text(
            text = detail.title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        val meta = buildList {
            detail.year?.takeIf { it > 0 }?.let { add(it.toString()) }
            detail.runtime?.takeIf { it > 0 }?.let { add("${it} min") }
            detail.contentRating.takeIf { it.isNotBlank() }?.let { add(it) }
            detail.voteAverage?.takeIf { it > 0 }?.let { add("★ ${"%.1f".format(it)}") }
        }.joinToString("  ·  ")
        if (meta.isNotBlank()) {
            Text(meta, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (detail.genres.isNotEmpty()) {
            Text(
                text = detail.genres.joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (detail.tagline.isNotBlank()) {
            Text(
                text = detail.tagline,
                style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            )
        }

        if (detail.overview.isNotBlank()) {
            Text(
                text = detail.overview,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 1100.dp),
            )
        }

        val request = detail.request
        when {
            request.requestable -> {
                TvRequestActionPill(
                    label = if (isSubmitting) "Requesting…" else "Request",
                    icon = Icons.Filled.Add,
                    onClick = onRequest,
                    enabled = !isSubmitting,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            !request.status.isNullOrBlank() -> {
                Text(
                    text = "Request status: ${request.status}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            request.reason.isNotBlank() -> {
                Text(
                    text = request.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        notice?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}
