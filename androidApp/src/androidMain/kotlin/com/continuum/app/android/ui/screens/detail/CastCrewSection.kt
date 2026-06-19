package com.continuum.app.android.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.continuum.app.common.ui.components.ThumbhashImage
import com.continuum.app.model.catalog.CastMember
import com.continuum.app.model.catalog.CrewMember

/**
 * Horizontal scrolling row of cast members with circular portraits,
 * name, and character. Tappable rows route to person detail when a
 * `person_id` is available.
 */
@Composable
fun CastCrewSection(
    cast: List<CastMember>,
    @Suppress("UNUSED_PARAMETER") crew: List<CrewMember>,
    onPersonClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cast.isEmpty()) return

    LazyRow(
        contentPadding = PaddingValues(horizontal = SafePadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(
            cast,
            key = { "${it.name}_${it.character}_${it.order}" },
            contentType = { "cast-member" },
        ) { member ->
            CastTile(
                photoUrl = member.photoUrl,
                photoThumbhash = member.photoThumbhash,
                name = member.name,
                role = member.character,
                onClick = member.personId?.let { id -> { onPersonClick(id) } },
            )
        }
    }
}

@Composable
private fun CastTile(
    photoUrl: String?,
    photoThumbhash: String?,
    name: String,
    role: String?,
    onClick: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        ThumbhashImage(
            url = photoUrl,
            thumbhash = photoThumbhash,
            contentDescription = name,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = DetailPrimaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (!role.isNullOrBlank()) {
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = DetailTertiaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
