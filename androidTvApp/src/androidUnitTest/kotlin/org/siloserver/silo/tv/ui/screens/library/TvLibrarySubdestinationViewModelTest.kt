package org.siloserver.silo.tv.ui.screens.library

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.siloserver.silo.network.SiloJson
import org.siloserver.silo.network.api.CatalogApi
import org.siloserver.silo.network.api.SectionApi
import org.siloserver.silo.repository.CatalogRepository
import org.siloserver.silo.repository.SectionRepository
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TvLibrarySubdestinationViewModelTest {
    @Test
    fun alphabetDestinationUsesTitleSortAndServerNamePrefix() = runLibraryTest {
        val requests = mutableListOf<RequestRecord>()
        val viewModel = viewModelFor(requests, libraryType = "movies")

        viewModel.onTabSelected(TvLibraryTab.Alphabet)
        awaitState { requests.catalogRequestCount() >= 1 }
        viewModel.onNamePrefixChanged("K")
        awaitState { requests.lastCatalogRequest().query["name_prefix"] == "K" }

        val request = requests.lastCatalogRequest()
        assertEquals("title", request.query["sort"])
        assertEquals("asc", request.query["order"])
        assertEquals("K", request.query["name_prefix"])
        assertEquals("0", request.query["offset"])
    }

    @Test
    fun recentlyAddedDestinationUsesNewestFirstWithoutAlphabetPrefix() = runLibraryTest {
        val requests = mutableListOf<RequestRecord>()
        val viewModel = viewModelFor(requests, libraryType = "series")

        viewModel.onTabSelected(TvLibraryTab.Alphabet)
        awaitState { requests.catalogRequestCount() >= 1 }
        viewModel.onNamePrefixChanged("S")
        awaitState { requests.lastCatalogRequest().query["name_prefix"] == "S" }

        viewModel.onTabSelected(TvLibraryTab.RecentlyAdded)
        awaitState {
            requests.lastCatalogRequest().query["sort"] == "added_at" &&
                requests.lastCatalogRequest().query["name_prefix"] == null
        }

        val request = requests.lastCatalogRequest()
        assertEquals("added_at", request.query["sort"])
        assertEquals("desc", request.query["order"])
        assertEquals(null, request.query["name_prefix"])
        assertEquals("0", request.query["offset"])
    }

    @Test
    fun audiobookAuthorsAndSeriesDestinationsUseBookNativeSorts() = runLibraryTest {
        val requests = mutableListOf<RequestRecord>()
        val viewModel = viewModelFor(requests, libraryType = "audiobooks")

        viewModel.onTabSelected(TvLibraryTab.Authors)
        awaitState { requests.lastCatalogRequest().query["sort"] == "author" }
        assertEquals("asc", requests.lastCatalogRequest().query["order"])

        viewModel.onTabSelected(TvLibraryTab.Series)
        awaitState { requests.lastCatalogRequest().query["sort"] == "series" }
        assertEquals("asc", requests.lastCatalogRequest().query["order"])
    }

    private fun runLibraryTest(block: suspend () -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun awaitState(predicate: () -> Boolean) {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                while (!predicate()) {
                    delay(10)
                }
            }
        }
    }

    private data class RequestRecord(
        val path: String,
        val query: Map<String, String?>,
    )

    private fun List<RequestRecord>.catalogRequestCount(): Int =
        count { it.path == "/api/v1/catalog" }

    private fun MutableList<RequestRecord>.lastCatalogRequest(): RequestRecord =
        lastOrNull { it.path == "/api/v1/catalog" }
            ?: error("Expected a catalog request, got $this")

    private fun viewModelFor(
        requests: MutableList<RequestRecord>,
        libraryType: String,
    ): TvLibraryDetailViewModel {
        val client = HttpClient(
            MockEngine { request ->
                requests += RequestRecord(
                    path = request.url.encodedPath,
                    query = request.url.parameters.names().associateWith { request.url.parameters[it] },
                )
                when (request.url.encodedPath) {
                    "/api/v1/library/7/sections" -> respondJson("""{"sections":[]}""")
                    "/api/v1/library/7/collections" -> respondJson("""{"collections":[]}""")
                    "/api/v1/catalog/filters" -> respondJson(
                        """{"genres":["Drama"],"studios":[],"networks":[],"countries":[],"content_ratings":[]}""",
                    )
                    "/api/v1/catalog" -> respondJson(
                        """
                            {
                              "total": 1,
                              "has_more": false,
                              "title": "Library",
                              "items": [
                                {"content_id":"item-1","title":"Item One","type":"movie"}
                              ]
                            }
                        """.trimIndent(),
                    )
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
            },
        ) {
            install(ContentNegotiation) { json(SiloJson) }
        }

        return TvLibraryDetailViewModel(
            sectionRepository = SectionRepository(SectionApi(client)),
            catalogRepository = CatalogRepository(CatalogApi(client)),
            libraryId = 7,
            libraryTitle = "Library",
            libraryType = libraryType,
        )
    }

    private fun MockRequestHandleScope.respondJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )
}
