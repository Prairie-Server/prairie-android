package com.continuum.app.di

import com.continuum.app.network.TokenManager
import com.continuum.app.network.TokenManagerImpl
import com.continuum.app.network.createContinuumClient
import com.continuum.app.network.api.*
import org.koin.dsl.module

val networkModule = module {
    single<TokenManager> { TokenManagerImpl() }
    single { createContinuumClient(get(), getOrNull()) }
    single { AuthApi(get()) }
    single { CatalogApi(get()) }
    single { PlaybackApi(get()) }
    single { PersonalDataApi(get()) }
    single { CollectionApi(get()) }
    single { ProfileApi(get()) }
    single { SectionApi(get()) }
    single { RecommendationApi(get()) }
    single { AdminApi(get()) }
    single { HealthApi(get()) }
    single { SettingsApi(get()) }
    single { LibraryPlaybackPrefsApi(get()) }
}
