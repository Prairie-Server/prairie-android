package com.continuum.app.tv.ui.screens.profiles

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvProfileFormSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/com/continuum/app/tv/ui/screens/profiles/TvProfileForm.kt",
    ).readText()

    @Test
    fun profileCreatorUsesTvosDiceBearAvatarFlowInsteadOfEmojiStrip() {
        assertTrue(source.contains("TvProfileAvatarPresets.styles"))
        assertTrue(source.contains("TvProfileAvatarPresets.batch"))
        assertTrue(source.contains("GridCells.Fixed(6)"))
        assertTrue(source.contains("\"Shuffle\""))
        assertTrue(source.contains("TvPresetAvatarCell"))
        assertFalse(
            source.contains("TV_AVATAR_EMOJIS"),
            "tvOS profile creation uses DiceBear styles and presets, not the old Android emoji strip.",
        )
    }

    @Test
    fun profileCreatorUsesTvosTwoColumnLayoutWithPreview() {
        assertTrue(source.contains("TvProfileTilePreview"))
        assertTrue(source.contains("private val ProfileEditorPreviewWidth = 180.dp"))
        assertTrue(source.contains("private val ProfileEditorColumnWidth = 584.dp"))
        assertTrue(source.contains("\"Pick a look and give it a name.\""))
    }

    @Test
    fun avatarCellsUseTvosWideFramesWithCenteredArt() {
        assertTrue(source.contains("private val ProfileAvatarCellHeight = 46.dp"))
        assertTrue(source.contains("private val ProfileAvatarImageSize = 42.dp"))
        assertTrue(source.contains("modifier = modifier.height(ProfileAvatarCellHeight)"))
        assertTrue(source.contains(".size(ProfileAvatarImageSize)"))
        assertFalse(
            source.contains("aspectRatio(1f)"),
            "tvOS preset cells are wide rounded frames with square avatar art centered inside, not square cells.",
        )
    }

    @Test
    fun profileCreatorDoesNotUseLazyParentAroundAvatarPicker() {
        assertFalse(
            source.contains("LazyColumn"),
            "A lazy parent auto-scrolls when avatar cells receive focus, which hides the picker during profile creation.",
        )
        assertFalse(source.contains("\"Quality Preference\""))
        assertFalse(source.contains("\"Subtitles\""))
        assertFalse(source.contains("\"Max Content Rating\""))
        assertFalse(
            source.contains("horizontalScroll"),
            "The five tvOS avatar style chips must fit; clipping a horizontal scroller cuts labels like Fun Emoji.",
        )
    }

    @Test
    fun lowerProfileControlsCanScrollPastPinWithoutMovingAvatarPicker() {
        assertTrue(source.contains("val formScrollState = rememberScrollState()"))
        assertTrue(source.contains(".verticalScroll(formScrollState)"))
        assertTrue(source.contains(".weight(1f)"))
        assertTrue(
            source.indexOf(".verticalScroll(formScrollState)") > source.indexOf("TvProfileFormSection(\n                            title = \"Avatar\""),
            "Only the lower form controls should scroll; the style/avatar picker must stay fixed.",
        )
        assertTrue(
            source.contains("event.key == Key.DirectionDown") && source.contains("childFocusRequester.requestFocus()"),
            "PIN field must actively hand D-pad down to Child Profile so the text field cannot trap focus.",
        )
    }

    @Test
    fun tvTextEntryOpensDialogOnSelectNotOnFocus() {
        assertTrue(source.contains("TvProfileTextEntryField"))
        assertTrue(source.contains("TvTextInputDialog"))
        assertTrue(source.contains("editingTextField = ProfileTextField.Name"))
        assertTrue(source.contains("editingTextField = ProfileTextField.Pin"))
        assertTrue(
            source.contains("keyboardType = when (field)") &&
                source.contains("ProfileTextField.Pin -> KeyboardType.NumberPassword"),
            "The PIN dialog must open a numeric keyboard; the name dialog can keep the default text keyboard.",
        )
        assertFalse(
            source.contains("OutlinedTextField"),
            "Inline profile fields must not be material TextFields because focusing them opens the TV keyboard and traps D-pad navigation.",
        )
    }

    @Test
    fun avatarGridHasExplicitDpadExitIntoFormFields() {
        assertTrue(source.contains("val nameFocusRequester = remember { FocusRequester() }"))
        assertTrue(source.contains("itemsIndexed(presets"))
        assertTrue(
            source.contains("val isBottomRow = index >= 12") && source.contains("down = nameFocusRequester"),
            "The bottom avatar row must move down to Name instead of trapping focus in the grid.",
        )
        assertTrue(
            source.contains("Key.DirectionDown") && source.contains("nameFocusRequester.requestFocus()"),
            "The avatar grid must actively hand D-pad down to the form fields when Compose focus search stays inside the grid.",
        )
        assertTrue(source.contains("down = pinFocusRequester"))
        assertTrue(source.contains("down = childFocusRequester"))
        assertTrue(source.contains("down = submitFocusRequester"))
    }
}
