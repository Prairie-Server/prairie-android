package com.continuum.app.android.ui.screens.admin

import com.continuum.app.model.auth.User
import com.continuum.app.model.auth.isActingAdmin
import com.continuum.app.model.profile.Profile
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
 * The acting-admin decision itself is exercised in the shared module's tests
 * for [isActingAdmin]; here we verify the view model folds that decision into
 * [AdminEntryViewModel.AdminUiState.isAdminVisible] for the user/profile
 * combinations that drive the Settings "Admin" entry and the hub gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminEntryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun user(role: String) = User(id = 1, username = "u", email = "e@x.io", role = role)
    private fun profile(primary: Boolean) =
        Profile(id = "p1", name = "Primary", isPrimary = primary)

    private fun vm(user: User?, profile: Profile?) = AdminEntryViewModel(
        gateProvider = { isActingAdmin(user, profile) },
    )

    @Test fun `admin role with primary profile is visible`() = runTest(dispatcher) {
        assertTrue(vm(user("admin"), profile(true)).uiState.value.isAdminVisible)
    }

    @Test fun `admin role with non-primary profile is hidden`() = runTest(dispatcher) {
        assertFalse(vm(user("admin"), profile(false)).uiState.value.isAdminVisible)
    }

    @Test fun `non-admin role is hidden even on primary profile`() = runTest(dispatcher) {
        assertFalse(vm(user("user"), profile(true)).uiState.value.isAdminVisible)
    }

    @Test fun `admin with no active profile is visible`() = runTest(dispatcher) {
        assertTrue(vm(user("admin"), null).uiState.value.isAdminVisible)
    }

    @Test fun `null user is hidden`() = runTest(dispatcher) {
        assertFalse(vm(null, profile(true)).uiState.value.isAdminVisible)
    }

    @Test fun `not loading after refresh`() = runTest(dispatcher) {
        assertFalse(vm(user("admin"), profile(true)).uiState.value.isLoading)
    }
}
