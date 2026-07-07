package org.siloserver.silo.model.admin

// The admin STATS dashboard (Apple-parity surface) is exposed to acting
// admins. The richer hub/users/sessions/logs/scans screens stay unlinked —
// Apple has no counterpart, so no menu should route to them.
const val CLIENT_ADMIN_SURFACE_ENABLED: Boolean = true

fun shouldShowClientAdminSurface(isActingAdmin: Boolean): Boolean =
    CLIENT_ADMIN_SURFACE_ENABLED && isActingAdmin
