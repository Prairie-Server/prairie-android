package org.prairieserver.prairie.common.di

import org.prairieserver.prairie.common.player.MediaAuthInterceptor
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.TokenManagerImpl
import okhttp3.OkHttpClient
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlayerModuleTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `provides authenticated player OkHttpClient for readers`() {
        val koin = startKoin {
            modules(
                module {
                    single<TokenManager> { TokenManagerImpl() }
                },
                playerModule,
            )
        }.koin

        val unqualified = koin.get<OkHttpClient>()
        val player = koin.get<OkHttpClient>(PLAYER_OKHTTP_QUALIFIER)

        assertSame(player, unqualified)
        assertTrue(
            unqualified.interceptors.any { it is MediaAuthInterceptor },
            "reader OkHttpClient should include MediaAuthInterceptor",
        )
    }
}
