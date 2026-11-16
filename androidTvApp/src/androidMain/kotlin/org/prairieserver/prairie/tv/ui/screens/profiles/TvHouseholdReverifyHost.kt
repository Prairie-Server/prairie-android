package org.prairieserver.prairie.tv.ui.screens.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import org.koin.compose.koinInject
import org.prairieserver.prairie.common.R as CommonR
import org.prairieserver.prairie.common.i18n.text
import org.prairieserver.prairie.repository.ProfileRepository
import org.prairieserver.prairie.tv.ui.components.TvPinEntryDialog

/**
 * Asks for the primary profile's PIN again when a manage-mode call finds its
 * token stale (see [org.prairieserver.prairie.repository.HouseholdManagementSession]).
 * App-wide because the call can come from the picker, add, or edit screen.
 */
@Composable
fun TvHouseholdReverifyHost(profileRepository: ProfileRepository = koinInject()) {
    val session = profileRepository.householdManagement
    val prompt by session.reverify.collectAsState()
    prompt?.let {
        TvPinEntryDialog(
            profile = it.profile,
            onPinEntered = session::submitPin,
            onDismiss = session::cancelReverify,
            errorMessage = it.error?.text(),
            isVerifying = it.isVerifying,
            prompt = stringResource(CommonR.string.pin_prompt_manage),
        )
    }
}
