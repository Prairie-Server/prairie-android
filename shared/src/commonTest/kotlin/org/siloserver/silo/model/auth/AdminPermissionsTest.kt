package org.siloserver.silo.model.auth

import org.siloserver.silo.model.profile.Profile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminPermissionsTest {

    private fun user(role: String) = User(
        id = 1,
        username = "admin",
        email = "admin@example.com",
        role = role,
    )

    private fun profile(isPrimary: Boolean) = Profile(
        id = "prof-1",
        name = "Owner",
        isPrimary = isPrimary,
    )

    @Test
    fun `admin role on primary profile is acting admin`() {
        assertTrue(isActingAdmin(user("admin"), profile(isPrimary = true)))
    }

    @Test
    fun `admin role on non-primary profile is not acting admin`() {
        assertFalse(isActingAdmin(user("admin"), profile(isPrimary = false)))
    }

    /**
     * The reported bug: a household profile that is not the owner showed the
     * admin surface. The account role is identical on every profile, so the
     * profile is the only thing separating them — and treating "not resolved"
     * as permission handed admin to whoever was signed in whenever the profile
     * lookup had not answered or had failed.
     */
    @Test
    fun `admin role with unresolved profile is not acting admin`() {
        assertFalse(isActingAdmin(user("admin"), null))
    }

    @Test
    fun `non-admin role is never acting admin`() {
        assertFalse(isActingAdmin(user("user"), profile(isPrimary = true)))
        assertFalse(isActingAdmin(user("user"), null))
    }

    @Test
    fun `null user is never acting admin`() {
        assertFalse(isActingAdmin(null, profile(isPrimary = true)))
        assertFalse(isActingAdmin(null, null))
    }

    @Test
    fun `profile defaults is_primary to false when wire omits it`() {
        val p = Profile(id = "p", name = "Kid")
        assertFalse(p.isPrimary)
    }
}
