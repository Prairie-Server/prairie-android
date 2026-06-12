package com.continuum.app.viewmodel

/**
 * Pure, testable helpers for the admin user create/edit form. Kept free of any
 * Compose/Android types so the validation and parsing rules can be unit-tested
 * in commonTest and reused by both the mobile screen and (later) TV.
 */

/** Display label for a role string (e.g. "admin" -> "Admin"); blank -> "Unknown". */
fun roleDisplayName(role: String): String = when {
    role.isBlank() -> "Unknown"
    else -> role.replaceFirstChar { it.uppercase() }
}

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private const val MIN_PASSWORD_LENGTH = 6

/** Returns an error message, or null when the create form is valid. */
fun validateCreateUser(username: String, email: String, password: String): String? = when {
    username.isBlank() -> "Username is required"
    !EMAIL_REGEX.matches(email.trim()) -> "Enter a valid email"
    password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters"
    else -> null
}

/**
 * Validates an optional password-reset value on the edit form. Blank means
 * "leave the password unchanged" and is always valid; a non-blank value must
 * meet the minimum length. Returns an error message, or null when valid.
 */
fun validatePasswordReset(password: String): String? = when {
    password.isBlank() -> null
    password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters"
    else -> null
}

/**
 * Parses an optional quota field. Blank -> null (unlimited / unchanged);
 * non-numeric or negative -> null. Zero is preserved (server treats 0 as
 * "unlimited"/disabled depending on the field).
 */
fun parseQuota(raw: String): Int? = raw.trim().toIntOrNull()?.takeIf { it >= 0 }

/**
 * Parses a comma-separated list of library ids, tolerating surrounding
 * whitespace and silently dropping non-numeric / negative entries.
 */
fun parseLibraryIds(raw: String): List<Int> =
    raw.split(',')
        .mapNotNull { it.trim().toIntOrNull()?.takeIf { id -> id >= 0 } }
