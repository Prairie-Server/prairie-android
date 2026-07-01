package org.siloserver.silo.tv.ui.screens.profiles

/**
 * Form option lists for TV profile create/edit. These mirror the phone app's
 * constants in `CreateProfileViewModel.kt` (androidApp); duplicated here because
 * the androidApp package is not on the TV classpath.
 */

/** Available content ratings, ordered from least to most restrictive. */
val TV_CONTENT_RATINGS = listOf("G", "PG", "PG-13", "R", "NC-17")

/** Available quality preferences. "Auto" maps to a null preference. */
val TV_QUALITY_OPTIONS = listOf("Auto", "4K", "1080p", "720p", "480p")

/** Available subtitle modes. "Off" maps to a null mode. */
val TV_SUBTITLE_MODES = listOf("Off", "Default", "Always", "Forced Only")

/**
 * Pre-defined emoji avatars. Mirrors `AvatarOptions.emojis` on the phone so the
 * picker offers the same set; the server stores the chosen string verbatim.
 */
val TV_AVATAR_EMOJIS = listOf(
    "😀", // grinning face
    "😎", // smiling face with sunglasses
    "🤓", // nerd face
    "🥸", // disguised face
    "👾", // alien monster
    "🐱", // cat face
    "🐶", // dog face
    "🦊", // fox
    "🦁", // lion
    "🐻", // bear
    "🐧", // penguin
    "🦉", // owl
    "🌟", // glowing star
    "🌈", // rainbow
    "🎨", // artist palette
    "🎬", // clapper board
    "🎵", // musical note
    "🚀", // rocket
    "🌍", // globe
    "🍓", // strawberry
)

/** Display label for a stored subtitle-mode value (e.g. "forced_only" -> "Forced Only"). */
fun subtitleModeLabel(stored: String?): String =
    stored?.replace("_", " ")?.split(" ")
        ?.joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
        ?: "Off"
