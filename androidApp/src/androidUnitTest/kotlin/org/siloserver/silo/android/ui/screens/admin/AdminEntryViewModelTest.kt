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
 * Admin client surfaces are disabled for now. Acting-admin still matters
 * server-side, but this ViewModel must keep the mobile admin hub hidden.
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

    @Test fun `admin role with primary profile is hidden while client admin is disabled`() = runTest(dispatcher) {
        assertFalse(vm(user("admin"), profile(true)).uiState.value.isAdminVisible)
    }

    @Test fun `admin role with non-primary profile is hidden`() = runTest(dispatcher) {
        assertFalse(vm(user("admin"), profile(false)).uiState.value.isAdminVisible)
    }

    @Test fun `non-admin role is hidden even on primary profile`() = runTest(dispatcher) {
        assertFalse(vm(user("user"), profile(true)).uiState.value.isAdminVisible)
    }

    @Test fun `admin with no active profile is hidden while client admin is disabled`() = runTest(dispatcher) {
        assertFalse(vm(user("admin"), null).uiState.value.isAdminVisible)
    }

    @Test fun `null user is hidden`() = runTest(dispatcher) {
        assertFalse(vm(null, profile(true)).uiState.value.isAdminVisible)
    }

    @Test fun `not loading after refresh`() = runTest(dispatcher) {
        assertFalse(vm(user("admin"), profile(true)).uiState.value.isLoading)
    }
}
