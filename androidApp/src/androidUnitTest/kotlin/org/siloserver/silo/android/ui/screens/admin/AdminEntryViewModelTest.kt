package org.siloserver.silo.android.ui.screens.admin

import org.siloserver.silo.model.auth.User
import org.siloserver.silo.model.auth.isActingAdmin
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

    // Folds the REAL gate, not a constant. The previous helper ignored both
    // arguments and always returned true, so every test using it passed no
    // matter what the gate did — including the case this class exists for.
    private fun vm(user: User?, profile: Profile?) =
        AdminEntryViewModel(gateProvider = { isActingAdmin(user, profile) })

    @Test fun `acting admin gate makes the surface visible`() = runTest(dispatcher) {
        assertTrue(AdminEntryViewModel(gateProvider = { true }).uiState.value.isAdminVisible)
    }

    @Test fun `non-admin gate keeps the surface hidden`() = runTest(dispatcher) {
        assertFalse(AdminEntryViewModel(gateProvider = { false }).uiState.value.isAdminVisible)
    }

    @Test fun `not loading after refresh`() = runTest(dispatcher) {
        assertFalse(vm(user("admin"), profile(true)).uiState.value.isLoading)
    }
    /**
     * The reported bug, at the ViewModel: an admin ACCOUNT on a non-owner
     * profile must not see the surface. The account role is identical on every
     * profile, so the profile is the only thing separating them.
     */
    @Test fun `admin account on a non-primary profile is refused`() = runTest(dispatcher) {
        assertFalse(vm(user("admin"), profile(primary = false)).uiState.value.isAdminVisible)
    }

    /** An unresolved profile is not permission — the gate fails closed. */
    @Test fun `admin account with an unresolved profile is refused`() = runTest(dispatcher) {
        assertFalse(vm(user("admin"), null).uiState.value.isAdminVisible)
    }

    /** And the owner still gets in once the profile resolves. */
    @Test fun `admin account on the primary profile is allowed`() = runTest(dispatcher) {
        assertTrue(vm(user("admin"), profile(primary = true)).uiState.value.isAdminVisible)
    }
}
