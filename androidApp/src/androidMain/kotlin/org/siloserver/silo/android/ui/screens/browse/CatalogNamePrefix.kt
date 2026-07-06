package org.siloserver.silo.android.ui.screens.browse

internal fun normalizeCatalogNamePrefix(prefix: String?): String? =
    prefix
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase()
        ?.take(1)
