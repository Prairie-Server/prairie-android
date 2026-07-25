package org.prairieserver.prairie.android.ui.screens.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.prairieserver.prairie.android.ui.screens.auth.AuthColors
import org.prairieserver.prairie.common.ui.components.ThumbhashImage
import org.prairieserver.prairie.common.ui.components.isImageAvatar
import org.prairieserver.prairie.common.ui.components.profileAvatarDisplayText
import org.prairieserver.prairie.common.ui.components.rememberProfileServerUrl
import org.prairieserver.prairie.common.ui.components.resolveAvatarUrl

/**
 * Pre-defined avatar options. Each entry is an emoji displayed inside a coloured circle.
 *
 * The server stores the avatar as a string. It may be an emoji, a DiceBear URL, or
 * an uploaded image path. When no avatar is set, initials derived from the profile
 * name are shown instead.
 */
object AvatarOptions {
    val emojis = listOf(
        "\uD83D\uDE00", // grinning face
        "\uD83D\uDE0E", // smiling face with sunglasses
        "\uD83E\uDD13", // nerd face
        "\uD83E\uDD78", // disguised face
        "\uD83D\uDC7E", // alien monster
        "\uD83D\uDC31", // cat face
        "\uD83D\uDC36", // dog face
        "\uD83E\uDD8A", // fox
        "\uD83E\uDD81", // lion
        "\uD83D\uDC3B", // bear
        "\uD83D\uDC27", // penguin
        "\uD83E\uDD89", // owl
        "\uD83C\uDF1F", // glowing star
        "\uD83C\uDF08", // rainbow
        "\uD83C\uDFA8", // artist palette
        "\uD83C\uDFAC", // clapper board
        "\uD83C\uDFB5", // musical note
        "\uD83D\uDE80", // rocket
        "\uD83C\uDF0D", // globe
        "\uD83C\uDF53", // strawberry
    )

    /** Deterministic colour for a given avatar string so the same profile always gets the same colour. */
    fun colorForAvatar(avatar: String): Color {
        val palette = listOf(
            Color(0xFF544F47),
            Color(0xFF6C6257),
            Color(0xFF7D7062),
            Color(0xFF4A504C),
            Color(0xFF655B50),
            Color(0xFF505862),
            Color(0xFF776A5C),
            Color(0xFF4B4C55),
        )
        val index = avatar.hashCode().let { if (it < 0) -it else it } % palette.size
        return palette[index]
    }
}

/**
 * Displays a profile avatar as an image, emoji, or initials inside a coloured circle.
 *
 * @param avatar Avatar string stored on the profile (nullable).
 * @param name Profile name, used for initials fallback.
 * @param size Circle diameter.
 * @param selected Whether to show a highlight border.
 * @param onClick Optional click handler.
 */
@Composable
fun ProfileAvatar(
    avatar: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val displayText = profileAvatarDisplayText(avatar = avatar, name = name)
    // iOS ProfileAvatarView uses a single flat prairieSurfaceVariant (#0E0F12).
    val bgColor = Color(0xFF0E0F12)
    val serverUrl = rememberProfileServerUrl()
    val resolvedAvatarUrl = remember(avatar, serverUrl) {
        avatar
            ?.takeIf(::isImageAvatar)
            ?.let { resolveAvatarUrl(serverUrl, it) }
    }

    val borderModifier = if (selected) {
        Modifier.border(3.dp, AuthColors.Primary, CircleShape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(borderModifier)
            .clip(CircleShape)
            .background(bgColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (resolvedAvatarUrl != null) {
            ThumbhashImage(
                url = resolvedAvatarUrl,
                thumbhash = null,
                contentDescription = "$name avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                transparent = true,
            )
        } else {
            // iOS: emoji at size*0.45; initials at size*0.34 semibold, onSurface.
            val isEmoji = !avatar.isNullOrBlank() && !isImageAvatar(avatar)
            if (isEmoji) {
                Text(
                    text = displayText,
                    fontSize = (size.value * 0.45).sp,
                )
            } else {
                Text(
                    text = displayText,
                    fontSize = (size.value * 0.34).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AuthColors.OnSurface,
                )
            }
        }
    }
}

/**
 * Smaller selectable emoji avatar used in the avatar picker grid on
 * create/edit screens. Mirrors iOS phone `EditProfileView`'s emoji grid:
 * 40dp rounded-rect cell (radius 8), emoji at 28pt, selection shown by a
 * tinted background (prairiePrimary at 30%).
 */
@Composable
fun AvatarPickerItem(
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) AuthColors.Primary.copy(alpha = 0.3f) else Color.Transparent,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = 28.sp,
        )
    }
}
