package org.prairieserver.prairie.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AdminUserFormTest {

    @Test fun `role display capitalizes`() {
        assertEquals("Admin", roleDisplayName("admin"))
        assertEquals("User", roleDisplayName("user"))
        assertEquals("Unknown", roleDisplayName(""))
    }

    @Test fun `create validation requires username email and password`() {
        assertNotNull(validateCreateUser(username = "", email = "a@b.io", password = "secret1"))
        assertNotNull(validateCreateUser(username = "u", email = "bad", password = "secret1"))
        assertNotNull(validateCreateUser(username = "u", email = "a@b.io", password = "123"))
        assertNull(validateCreateUser(username = "u", email = "a@b.io", password = "secret1"))
    }

    @Test fun `password reset validation allows blank but rejects short`() {
        assertNull(validatePasswordReset(""))
        assertNotNull(validatePasswordReset("123"))
        assertNull(validatePasswordReset("secret1"))
    }

    @Test fun `quota parsing rejects negatives and non-numbers`() {
        assertNull(parseQuota(""))            // blank -> unlimited / unchanged
        assertNull(parseQuota("abc"))         // non-numeric -> null
        assertNull(parseQuota("-1"))          // negative -> null
        assertEquals(3, parseQuota("3"))
        assertEquals(0, parseQuota("0"))
    }

    @Test fun `library ids parsing tolerates whitespace and ignores junk`() {
        assertEquals(emptyList(), parseLibraryIds(""))
        assertEquals(listOf(1, 2, 3), parseLibraryIds("1, 2 , 3"))
        assertEquals(listOf(5), parseLibraryIds("5, x, -2"))
    }
}
