package org.siloserver.silo.tv.ui.focus

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalTvMaterial3Api::class)
class TvControlSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun transientlyDisabledControlRemainsFocusableAndReportsDisabled() {
        val state = TvControlState.transient(isEnabled = false)
        composeRule.setContent {
            Surface(
                onClick = {},
                enabled = state.focusable,
                modifier = Modifier
                    .testTag("control")
                    .tvControlSemantics(state),
            ) {}
        }

        composeRule.onNodeWithTag("control")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Focused, false))
            .assertIsNotEnabled()
    }

    @Test
    fun actionableControlReportsEnabled() {
        val state = TvControlState.transient(isEnabled = true)
        composeRule.setContent {
            Surface(
                onClick = {},
                enabled = state.focusable,
                modifier = Modifier
                    .testTag("control")
                    .tvControlSemantics(state),
            ) {}
        }

        composeRule.onNodeWithTag("control")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Focused, false))
            .assertIsEnabled()
    }
}
