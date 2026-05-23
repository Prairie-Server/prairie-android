package com.continuum.app.di

import com.continuum.app.domain.GetHomeDataUseCase
import com.continuum.app.domain.ManagePlaybackUseCase
import com.continuum.app.domain.MediaActionsCoordinator
import com.continuum.app.repository.AdminRepository
import com.continuum.app.repository.AuthRepository
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.CollectionRepository
import com.continuum.app.repository.LibraryPlaybackPrefsRepository
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.repository.PlaybackRepository
import com.continuum.app.repository.ProfileRepository
import com.continuum.app.repository.RecommendationRepository
import com.continuum.app.repository.SectionRepository
import com.continuum.app.repository.SettingsRepository
import org.koin.dsl.module

/**
 * Koin module providing all repository and domain use case instances.
 *
 * Dependencies:
 * - API classes (AuthApi, CatalogApi, etc.) from networkModule (Agent 2)
 * - TokenManager from networkModule (Agent 2)
 *
 * All repositories are singletons; they are stateless wrappers around API classes
 * and TokenManager, so sharing instances is safe and efficient.
 */
val repositoryModule = module {
    // Repositories — `getOrNull()` for ServerRegistry / HealthApi keeps these
    // working when the multi-server platform binding isn't installed
    // (commonMain tests, hypothetical iOS reuse). Both repos no-op the
    // multi-server side effects when the registry is null.
    single { AuthRepository(get(), get(), getOrNull(), getOrNull()) }
    single { CatalogRepository(get()) }
    single { PlaybackRepository(get()) }
    single { PersonalDataRepository(get()) }
    single { ProfileRepository(get(), get(), getOrNull()) }
    single { CollectionRepository(get()) }
    single { SectionRepository(get()) }
    single { RecommendationRepository(get()) }
    single { AdminRepository(get()) }
    single { SettingsRepository(get()) }
    single { LibraryPlaybackPrefsRepository(get()) }

    // Domain use cases
    single { GetHomeDataUseCase(get(), get()) }
    single { ManagePlaybackUseCase(get(), get()) }
    single { MediaActionsCoordinator(get()) }
}
