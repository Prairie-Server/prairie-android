package org.siloserver.silo.model.admin

const val CLIENT_ADMIN_SURFACE_ENABLED: Boolean = false

fun shouldShowClientAdminSurface(isActingAdmin: Boolean): Boolean =
    CLIENT_ADMIN_SURFACE_ENABLED && isActingAdmin
