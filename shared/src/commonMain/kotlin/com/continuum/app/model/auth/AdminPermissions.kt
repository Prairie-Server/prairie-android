package com.continuum.app.model.auth

import com.continuum.app.model.profile.Profile

/** Admin role wire value (server `user.role`). */
const val ADMIN_ROLE = "admin"

/**
 * Client mirror of the server's `RequireActingAdmin` gate (web
 * `isActingAdmin(user, profile)`): the account role must be admin AND the
 * active household profile must be the primary (owner) profile.
 *
 * A null [profile] is treated as "not yet resolved" and does NOT block an
 * admin user — the active profile may not be loaded when the gate is first
 * evaluated, and every admin route is still gated server-side (defense in
 * depth). A null [user] is never acting-admin.
 */
fun isActingAdmin(user: User?, profile: Profile?): Boolean =
    user?.role == ADMIN_ROLE && (profile == null || profile.isPrimary)
