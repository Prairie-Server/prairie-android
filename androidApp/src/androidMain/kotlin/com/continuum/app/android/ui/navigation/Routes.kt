package com.continuum.app.android.ui.navigation

/**
 * All navigation routes for the Continuum app.
 *
 * Screens that take parameters use companion objects with a ROUTE constant
 * containing the placeholder (e.g. "item/{contentId}") for use with NavHost,
 * while the data-class constructor builds the resolved route for navigate().
 */
sealed class Route(val route: String) {

    // --- Auth flow (no bottom nav) ---
    data object ServerSetup : Route("server_setup")
    data object ServerList : Route("server_list")
    data object Login : Route("login")
    data object Setup : Route("setup")
    data object Signup : Route("signup")

    // --- Profile selection (no bottom nav) ---
    data object ProfileSelection : Route("profiles")

    // --- Main tabs (inside bottom nav scaffold) ---
    data object Home : Route("home")
    data object Libraries : Route("libraries")
    data object Recommendations : Route("recommendations")
    data object Search : Route("search")
    data object Settings : Route("settings")

    // --- Detail screens (back navigation, no bottom nav) ---
    data class ItemDetail(
        val contentId: String,
        val seasonNumber: Int? = null,
    ) : Route(
        if (seasonNumber != null) "item/$contentId?seasonNumber=$seasonNumber" else "item/$contentId"
    ) {
        companion object {
            const val ROUTE = "item/{contentId}?seasonNumber={seasonNumber}"
        }
    }

    data class PersonDetail(val personId: Int) : Route("person/$personId") {
        companion object {
            const val ROUTE = "person/{personId}"
        }
    }

    // --- Catalog / Browse ---
    data class Browse(val libraryId: Int? = null) : Route(
        if (libraryId != null) "browse?libraryId=$libraryId" else "browse"
    ) {
        companion object {
            const val ROUTE = "browse?libraryId={libraryId}"
        }
    }

    data class CollectionDetail(
        val collectionId: String,
        val libraryId: Int? = null,
    ) : Route(
        if (libraryId != null) "collection/$collectionId?libraryId=$libraryId" else "collection/$collectionId"
    ) {
        companion object {
            const val ROUTE = "collection/{collectionId}?libraryId={libraryId}"
        }
    }

    // --- Player (fullscreen, no system bars) ---
    data class Player(
        val contentId: String,
        val fileId: Int? = null,
        val audioTrackIndex: Int? = null,
        val subtitleTrackIndex: Int? = null,
    ) : Route(
        buildString {
            append("player/$contentId")
            val queryParams = listOfNotNull(
                fileId?.let { "fileId=$it" },
                audioTrackIndex?.let { "audioTrackIndex=$it" },
                subtitleTrackIndex?.let { "subtitleTrackIndex=$it" },
            )
            if (queryParams.isNotEmpty()) {
                append("?")
                append(queryParams.joinToString("&"))
            }
        },
    ) {
        companion object {
            const val ROUTE =
                "player/{contentId}?fileId={fileId}&audioTrackIndex={audioTrackIndex}&subtitleTrackIndex={subtitleTrackIndex}"
        }
    }

    // --- Personal data ---
    data object Favorites : Route("favorites")
    data object Watchlist : Route("watchlist")
    data object History : Route("history")
    data object PersonalLists : Route("personal_lists")
    data class Collections(val libraryId: Int? = null) : Route(
        if (libraryId != null) "collections?libraryId=$libraryId" else "collections"
    ) {
        companion object {
            const val ROUTE = "collections?libraryId={libraryId}"
        }
    }

    // --- Admin ---
    data object Admin : Route("admin")
}
