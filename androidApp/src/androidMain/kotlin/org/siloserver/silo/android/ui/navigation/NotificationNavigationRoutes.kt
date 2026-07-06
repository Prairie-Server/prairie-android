package org.siloserver.silo.android.ui.navigation

private val NOTIFICATION_ITEM_DETAIL_ROUTE = Regex("""^item/[^/?#]+$""")

internal fun notificationNavigationRouteOrNull(rawRoute: String?): String? {
    val route = rawRoute?.trim().orEmpty()
    return when {
        route == Route.Inbox.route -> route
        NOTIFICATION_ITEM_DETAIL_ROUTE.matches(route) -> route
        else -> null
    }
}
