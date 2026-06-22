package com.continuum.app.common.startup

import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.AuthRepository
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.repository.ProfileRepository
import com.continuum.app.repository.SectionRepository
import com.continuum.app.repository.port.HomeCachePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/**
 * Best-effort authenticated cold-start warmup. This must never decide routing
 * or block splash dismissal; it just fills the same caches/screens read after
 * the navigation graph enters the authenticated app.
 */
suspend fun warmAuthenticatedStartup(
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    personalDataRepository: PersonalDataRepository,
    sectionRepository: SectionRepository,
    homeCache: HomeCachePort,
) {
    supervisorScope {
        listOf(
            async {
                runCatching { authRepository.getCurrentUser() }
                Unit
            },
            async {
                runCatching { profileRepository.getActiveProfile() }
                Unit
            },
            async {
                runCatching { personalDataRepository.listUserLibraries() }
                Unit
            },
            async {
                runCatching { warmHome(sectionRepository, homeCache) }
                Unit
            },
        ).awaitAll()
    }
}

private suspend fun CoroutineScope.warmHome(
    sectionRepository: SectionRepository,
    homeCache: HomeCachePort,
) {
    when (val result = sectionRepository.getHomeSections()) {
        is ApiResult.Success -> {
            val resolvedPairs: List<Pair<ResolvedSection, Boolean>> =
                result.data.sections.map { section ->
                    async {
                        when (val itemsResult = sectionRepository.getHomeSectionItems(section.id)) {
                            is ApiResult.Success -> (itemsResult.data.section ?: section) to true
                            is ApiResult.Error,
                            is ApiResult.NetworkError -> section to false
                        }
                    }
                }.awaitAll()

            if (resolvedPairs.all { it.second }) {
                val resolved = resolvedPairs.map { it.first }.filter { it.items.isNotEmpty() }
                if (resolved.isNotEmpty()) {
                    homeCache.cacheHome(resolved)
                }
            }
        }
        is ApiResult.Error,
        is ApiResult.NetworkError -> Unit
    }
}
