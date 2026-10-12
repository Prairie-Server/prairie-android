package org.prairieserver.prairie.tv.ui.screens.profiles

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.prairieserver.prairie.model.profile.Profile
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.network.DefaultIdentityTransitionBarrier
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.TokenManagerImpl
import org.prairieserver.prairie.network.api.ProfileApi
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import org.prairieserver.prairie.repository.ProfileRepository
import org.prairieserver.prairie.common.profiles.ProfileLaunchBehavior
import org.prairieserver.prairie.common.profiles.ProfileLaunchPreferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TvProfileRememberOfferTest {
    private val profile = Profile(id = "profile-a", name = "Ana")

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an offer for a profile cleared while it was open neither saves nor opens Home`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val barrier = DefaultIdentityTransitionBarrier()
        val repository = OneProfileRepository(barrier, profile)
        val preferences = ProfileLaunchPreferences(InMemoryPrefs()).apply {
            settleFirstLaunch(hasRememberedProfile = false)
        }
        val viewModel = TvProfileSelectionViewModel(
            repository,
            consumeSkipsSingleProfile = { false },
            launchPreferences = preferences,
        )
        advanceUntilIdle()

        viewModel.onProfileSelected(profile)
        advanceUntilIdle()
        assertEquals(profile, viewModel.uiState.value.rememberOffer)

        // What profile-verification recovery does while the dialog is up.
        repository.clearProfile()
        viewModel.answerRememberOffer(remember = true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedProfileId)
        assertNull(viewModel.uiState.value.rememberOffer)
        assertTrue(preferences.isRememberOfferPending)
        assertEquals(ProfileLaunchBehavior.EveryTime, preferences.state.value.behavior)
    }

    @Test
    fun `answering the offer for the active profile saves it and opens Home`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val barrier = DefaultIdentityTransitionBarrier()
        val repository = OneProfileRepository(barrier, profile)
        val preferences = ProfileLaunchPreferences(InMemoryPrefs()).apply {
            settleFirstLaunch(hasRememberedProfile = false)
        }
        val viewModel = TvProfileSelectionViewModel(
            repository,
            consumeSkipsSingleProfile = { false },
            launchPreferences = preferences,
        )
        advanceUntilIdle()

        viewModel.onProfileSelected(profile)
        viewModel.answerRememberOffer(remember = true)
        advanceUntilIdle()

        assertEquals(profile.id, viewModel.uiState.value.selectedProfileId)
        assertEquals(ProfileLaunchBehavior.Automatic, preferences.state.value.behavior)
    }

    @Test
    fun `a reload outdated by the offer's own commit keeps the offer`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val barrier = DefaultIdentityTransitionBarrier()
        val reloadInFlight = CompletableDeferred<Unit>()
        val repository = OneProfileRepository(barrier, profile) { call ->
            if (call == 2) reloadInFlight.await()
        }
        val preferences = ProfileLaunchPreferences(InMemoryPrefs()).apply {
            settleFirstLaunch(hasRememberedProfile = false)
        }
        val viewModel = TvProfileSelectionViewModel(
            repository,
            consumeSkipsSingleProfile = { false },
            launchPreferences = preferences,
        )
        advanceUntilIdle()

        // The viewer picks while a reload is in flight; the pick's commit
        // moves the identity that reload was fetched under.
        viewModel.loadProfiles()
        viewModel.onProfileSelected(profile)
        advanceUntilIdle()
        assertEquals(profile, viewModel.uiState.value.rememberOffer)
        reloadInFlight.complete(Unit)
        advanceUntilIdle()
        assertEquals(profile, viewModel.uiState.value.rememberOffer)

        viewModel.answerRememberOffer(remember = true)
        advanceUntilIdle()
        assertEquals(profile.id, viewModel.uiState.value.selectedProfileId)
    }
}

private class OneProfileRepository(
    barrier: DefaultIdentityTransitionBarrier,
    private val profile: Profile,
    private val beforeList: suspend (Int) -> Unit = {},
) : ProfileRepository(
    profileApi = ProfileApi(HttpClient(MockEngine { respond("{}") }), ApiV2Gate.Unrestricted),
    tokenManager = ScopedTokenManager(barrier),
    identityTransitions = barrier,
) {
    private var calls = 0

    override suspend fun listProfiles(): ApiResult<List<Profile>> {
        beforeList(++calls)
        return ApiResult.Success(listOf(profile))
    }
}

/** Reports the barrier generation as its scope, so identity changes are visible. */
private class ScopedTokenManager(
    private val barrier: DefaultIdentityTransitionBarrier,
) : TokenManager by TokenManagerImpl(barrier) {
    override suspend fun snapshotCurrentScope(): AuthScopeSnapshot = AuthScopeSnapshot(
        serverId = "server-a",
        profileId = null,
        serverUrl = "https://server-a",
        profileToken = null,
        identityGeneration = barrier.generation.value,
    )
}
