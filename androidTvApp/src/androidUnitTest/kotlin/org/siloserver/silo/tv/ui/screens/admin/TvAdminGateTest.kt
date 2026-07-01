package org.siloserver.silo.tv.ui.screens.admin

import org.siloserver.silo.model.auth.User
import org.siloserver.silo.model.profile.Profile
import org.siloserver.silo.model.auth.isActingAdmin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The TV Settings "Admin" row reachability is the acting-admin gate, identical
 * to mobile. This pins the gate the TvSettingsViewModel folds into UiState.
 */
class TvAdminGateTest {
    private fun user(role: String) = User(id = 1, username = "u", email = "e@x.io", role = role)
    private fun profile(primary: Boolean) = Profile(id = "p", name = "p", isPrimary = primary)

    @Test fun `admin on primary profile sees admin`() = assertTrue(isActingAdmin(user("admin"), profile(true)))
    @Test fun `admin on non-primary hidden`() = assertFalse(isActingAdmin(user("admin"), profile(false)))
    @Test fun `non-admin hidden`() = assertFalse(isActingAdmin(user("user"), profile(true)))
    @Test fun `admin without profile visible`() = assertTrue(isActingAdmin(user("admin"), null))
}
