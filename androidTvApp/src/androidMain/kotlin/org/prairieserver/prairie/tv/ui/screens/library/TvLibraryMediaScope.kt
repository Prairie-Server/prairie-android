package org.prairieserver.prairie.tv.ui.screens.library

import org.prairieserver.prairie.catalog.scopeLibrarySection
import org.prairieserver.prairie.model.catalog.CatalogResponse
import org.prairieserver.prairie.model.section.ResolvedSection
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.apiv2.CatalogContinuationV2

internal suspend fun scopeTvLibrarySection(
    section: ResolvedSection,
    mediaScope: String?,
    loadPage: suspend (CatalogContinuationV2?) -> ApiResult<CatalogResponse>,
) = scopeLibrarySection(section, mediaScope, loadPage)
