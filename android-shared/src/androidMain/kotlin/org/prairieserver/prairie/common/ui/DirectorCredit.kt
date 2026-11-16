package org.prairieserver.prairie.common.ui

import androidx.compose.runtime.Composable
import org.prairieserver.prairie.common.R
import org.prairieserver.prairie.common.i18n.stringResourceWithNames
import org.prairieserver.prairie.model.catalog.ItemDetail

/** "Directed by …" for a movie with a director, else null. */
@Composable
fun movieDirectorCredit(detail: ItemDetail): String? =
    movieDirectorNames(detail)?.let { names ->
        stringResourceWithNames(R.string.detail_directedBy, names.joinToString(", "))
    }

/** Up to three of a movie's directors, in server order; null when there are none. */
internal fun movieDirectorNames(detail: ItemDetail): List<String>? {
    if (!detail.type.equals("movie", ignoreCase = true)) return null
    val names = detail.crew
        .asSequence()
        .filter { it.job?.trim().equals("Director", ignoreCase = true) }
        .map { it.name.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(3)
        .toList()
    return names.takeIf { it.isNotEmpty() }
}
