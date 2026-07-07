package org.siloserver.silo.android.ui.screens.admin

import org.siloserver.silo.model.auth.User
import org.siloserver.silo.model.profile.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The admin stats dashboard is visible to acting admins (Apple parity);
 * everything still folds through the gateProvider seam.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminEntryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun user(role: String) = User(id = 1, username = "u", email = "e@x.io", role = role)
    private fun profile(primary: Boolean) =
        Profile(id = "p1", name = "Primary", isPrimary = primary)

    private fun vm(@Suppress("UNUSED_PARAMETER") user: User?, @Suppress("UNUSED_PARAMETER") profile: Profile?) =
        AdminEntryViewModel(gateProvider = { true })

    @Test fun `acting admin gate makes the surface visible`() = runTest(dispatcher) {
        assertTrue(AdminEntryViewModel(gateProvider = { true }).uiState.value.isAdminVisible)
    }

    @Test fun `non-admin gate keeps the surface hidden`() = runTest(dispatcher) {
        assertFalse(AdminEntryViewModel(gateProvider = { false }).uiState.value.isAdminVisible)
    }

    @Test fun `not loading after refresh`() = runTest(dispatcher) {
        assertFalse(vm(user("admin"), profile(true)).uiState.value.isLoading)
    }
}
