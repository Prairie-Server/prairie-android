package org.prairieserver.prairie.viewmodel

import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.model.personal.Collection
import org.prairieserver.prairie.model.personal.CollectionGroup
import org.prairieserver.prairie.model.personal.CollectionsResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.CollectionApi
import org.prairieserver.prairie.repository.CollectionRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    // Cancel viewModelScope coroutines BEFORE resetting Main: a coroutine
    // still parked on Dispatchers.Main when a later test calls setMain/resetMain
    // throws IllegalStateException from TestMainDispatcher.
    private val createdViewModels = mutableListOf<androidx.lifecycle.ViewModel>()

    private fun <T : androidx.lifecycle.ViewModel> track(viewModel: T): T {
        createdViewModels += viewModel
        return viewModel
    }


    private fun vm(handler: (io.ktor.client.request.HttpRequestData) -> Pair<HttpStatusCode, String>): CollectionsViewModel {
        val client = HttpClient(MockEngine { req ->
            val (status, body) = handler(req)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(PrairieJson) } }
        return track(CollectionsViewModel(CollectionRepository(CollectionApi(client))))
    }

    @Test
    fun buildCollectionSectionsOrdersGroupsAndUngrouped() {
        val collections = listOf(
            Collection(id = "c2", name = "B", groupId = "g1", sortOrder = 2),
            Collection(id = "c1", name = "A", groupId = "g1", sortOrder = 1),
            Collection(id = "c3", name = "U", groupId = null, sortOrder = 0),
        )
        val groups = listOf(CollectionGroup(id = "g1", name = "G", sortOrder = 0))
        val sections = buildCollectionSections(collections, groups)
        assertEquals(2, sections.size)
        assertEquals(listOf("c1", "c2"), sections[0].collections.map { it.id })
        assertEquals("Ungrouped", sections[1].name)
    }

    @Test
    fun buildCollectionSectionsHidesEmptyUngroupedWhenGroupsExist() {
        val sections = buildCollectionSections(
            listOf(Collection(id = "c1", name = "A", groupId = "g1")),
            listOf(CollectionGroup(id = "g1", name = "G")),
        )
        assertEquals(1, sections.size)
        assertEquals("g1", sections.single().groupId)
    }

    @Test
    fun loadsCreatesDeletesAndGroupActions() = runTest(dispatcher) {
        val listBody = """{"collections":[{"id":"c1","name":"Favs"}],"groups":[{"id":"g1","name":"G"}]}"""
        val model = vm { req ->
            when {
                req.url.encodedPath == "/api/v1/collections" && req.method == HttpMethod.Get ->
                    HttpStatusCode.OK to listBody
                req.url.encodedPath == "/api/v1/collections" && req.method == HttpMethod.Post ->
                    HttpStatusCode.OK to """{"id":"c2","name":"New"}"""
                req.url.encodedPath.startsWith("/api/v1/collections/groups") && req.method == HttpMethod.Post ->
                    HttpStatusCode.OK to """{"id":"g2","name":"G2"}"""
                req.url.encodedPath.startsWith("/api/v1/collections/groups/") && req.method == HttpMethod.Put ->
                    HttpStatusCode.OK to """{"id":"g1","name":"Renamed"}"""
                req.url.encodedPath.startsWith("/api/v1/collections/groups/") && req.method == HttpMethod.Delete ->
                    HttpStatusCode.NoContent to ""
                req.url.encodedPath.startsWith("/api/v1/collections/c1") && req.method == HttpMethod.Put ->
                    HttpStatusCode.OK to """{"id":"c1","name":"Favs","group_id":null}"""
                req.url.encodedPath.startsWith("/api/v1/collections/c1") && req.method == HttpMethod.Delete ->
                    HttpStatusCode.NoContent to ""
                else -> HttpStatusCode.OK to listBody
            }
        }
        val loaded = model.uiState.first { !it.isLoading }
        assertFalse(loaded.isLoading)
        assertEquals(1, loaded.collections.size)
        model.showCreateSheet()
        model.onCreateNameChanged("New")
        model.onCreateTypeChanged("manual")
        model.createCollection()
        model.uiState.first { it.collections.any { c -> c.id == "c2" } }
        model.hideCreateSheet()
        model.openGroupAction(GroupAction.Create)
        model.createGroup("G2")
        model.uiState.first { it.groups.any { g -> g.id == "g2" } }
        model.renameGroup("g1", "Renamed")
        model.uiState.first { it.groups.any { g -> g.name == "Renamed" } }
        model.moveCollection("c1", null)
        model.deleteGroup("g1")
        model.uiState.first { it.groups.none { g -> g.id == "g1" } }
        model.deleteCollection("c1")
        model.uiState.first { it.collections.none { c -> c.id == "c1" } }
        model.refresh()
        model.uiState.first { !it.isRefreshing }
        model.dismissGroupAction()
        model.createCollection("")
        assertEquals("Name is required", model.uiState.value.createError)
        model.createGroup(" ")
        assertEquals("Name is required", model.uiState.value.groupError)
    }
}
