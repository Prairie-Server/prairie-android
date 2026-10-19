package org.prairieserver.prairie.viewmodel

import org.prairieserver.prairie.network.apiv2.ApiV2Gate

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.prairieserver.prairie.domain.MediaActionsCoordinator
import org.prairieserver.prairie.model.section.ResolvedSection
import org.prairieserver.prairie.network.DefaultIdentityTransitionBarrier
import org.prairieserver.prairie.network.IdentityTransitionKind
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.TokenManagerImpl
import org.prairieserver.prairie.network.apiv2.HomeSectionsV2Api
import org.prairieserver.prairie.network.api.PersonalDataApi
import org.prairieserver.prairie.network.api.SectionApi
import org.prairieserver.prairie.repository.PersonalDataRepository
import org.prairieserver.prairie.repository.SectionRepository
import org.prairieserver.prairie.repository.port.HomeCachePort
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelCacheIdentityTest {
    private var owner = AuthScopeSnapshot("s", "p", "https://example.invalid", "pin", identityGeneration = 1)
    private val tokens = object : TokenManager by TokenManagerImpl() { override suspend fun snapshotCurrentScope() = owner }


    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Serves Home on the test's own dispatcher. MockEngine otherwise answers on
     * a real IO thread, so a response still in flight when a test returns
     * resumes its view model on Dispatchers.Main after tearDown reset it, and
     * kotlinx-coroutines-test pins that exception on whichever test runs next.
     */
    private fun homeClient(handler: MockRequestHandler) = HttpClient(
        MockEngine(MockEngineConfig().apply {
            dispatcher = this@HomeViewModelCacheIdentityTest.dispatcher
            addHandler(handler)
        }),
    ) {
        install(ContentNegotiation) { json(PrairieJson) }
    }

    @Test
    fun homeResponseStartedBeforeProfileSwitchIsNotCachedForNewProfile() = runTest(dispatcher) {
        val requestEntered = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()
        val client = homeClient {
            requestEntered.complete(Unit)
            releaseResponse.await()
            respond(
                """{"sections":[{"id":"old","section_type":"row","title":"Profile A","items":[]}]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val identityTransitions = DefaultIdentityTransitionBarrier()
        val cache = RecordingHomeCache()
        val viewModel = HomeViewModel(
            sectionRepository = SectionRepository(
                sectionApi = SectionApi(client, home = HomeSectionsV2Api(client, tokens, ApiV2Gate.Unrestricted, decodeDispatcher = dispatcher)),
            ),
            mediaActions = mediaActions(),
            homeCache = cache,
            identityTransitions = identityTransitions,
        )

        requestEntered.await()
        identityTransitions.changing(IdentityTransitionKind.PROFILE_SWITCH) { owner = owner.copy(profileToken = "new") }
        releaseResponse.complete(Unit)
        viewModel.uiState.first { !it.isLoading }

        assertEquals(null, cache.sections)
        viewModel.close()
    }

    @Test
    fun homeReportsCacheAndNetworkProvenanceWithoutContentMetadata() = runTest(dispatcher) {
        val client = homeClient {
            respond(
                """{"sections":[{"id":"private-section","section_type":"row","title":"Private Title","items":[]}]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val observations = mutableListOf<HomeLoadObservation>()
        val viewModel = HomeViewModel(
            sectionRepository = SectionRepository(SectionApi(client, home = HomeSectionsV2Api(client, tokens, ApiV2Gate.Unrestricted, decodeDispatcher = dispatcher))),
            mediaActions = mediaActions(),
            diagnostics = HomeDiagnosticsObserver(observations::add),
        )

        viewModel.uiState.first { !it.isLoading }

        assertEquals(
            listOf(HomeLoadOutcome.MISS, HomeLoadOutcome.SUCCESS),
            observations.map(HomeLoadObservation::outcome),
        )
        assertEquals(listOf(HomeLoadSource.CACHE, HomeLoadSource.NETWORK), observations.map { it.source })
        assertTrue(observations.all { it.durationMs >= 0 })
        // The repository removes empty rows during hydration; diagnostics see
        // only the aggregate resolved count, never the private row metadata.
        assertEquals(listOf(0, 0), observations.map { it.sectionCount })
        viewModel.close()
    }

    @Test
    fun realtimeSignalsDuringARefreshRunOneMoreRefreshAfterIt() = runTest(dispatcher) {
        var requests = 0
        val realtimeEntered = CompletableDeferred<Unit>()
        val releaseRealtime = CompletableDeferred<Unit>()
        val followUpEntered = CompletableDeferred<Unit>()
        val client = homeClient {
            when (++requests) {
                2 -> {
                    realtimeEntered.complete(Unit)
                    releaseRealtime.await()
                }
                3 -> followUpEntered.complete(Unit)
            }
            respond(
                """{"sections":[]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val viewModel = HomeViewModel(
            sectionRepository = SectionRepository(SectionApi(client, home = HomeSectionsV2Api(client, tokens, ApiV2Gate.Unrestricted, decodeDispatcher = dispatcher))),
            mediaActions = mediaActions(),
        )
        viewModel.uiState.first { !it.isLoading }

        viewModel.refreshFromRealtime()
        realtimeEntered.await()
        // An access change reported while that fetch is out: its answer may
        // predate the change. Repeated signals collapse into one follow-up.
        viewModel.refreshFromRealtime()
        viewModel.refreshFromRealtime()
        releaseRealtime.complete(Unit)

        followUpEntered.await()
        viewModel.close()
    }

    // Cancel and join before tearDown resets Main: a fetch still out on the
    // MockEngine's IO thread would otherwise resume onto a missing Main
    // dispatcher and fail whichever runTest comes next.
    private suspend fun HomeViewModel.close() {
        viewModelScope.coroutineContext[Job]?.cancelAndJoin()
    }

    private class RecordingHomeCache : HomeCachePort {
        var sections: List<ResolvedSection>? = null

        override suspend fun cacheHomeV2(sections: List<ResolvedSection>, owner: AuthScopeSnapshot, artworkFingerprint: String?, stillCurrent: () -> Boolean) {
            this.sections = sections
        }
    }

    private fun mediaActions(): MediaActionsCoordinator {
        val client = HttpClient(MockEngine { error("Personal data network should not be used") }) {
            install(ContentNegotiation) { json(PrairieJson) }
        }
        return MediaActionsCoordinator(PersonalDataRepository(PersonalDataApi(client)))
    }
}
