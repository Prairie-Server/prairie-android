package org.prairieserver.prairie.android.ui.screens.settings.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.prairieserver.prairie.android.ui.components.PrairieConfirmDialog
import org.prairieserver.prairie.android.ui.components.PrairieDialog
import org.prairieserver.prairie.android.ui.components.PrairieDialogAction
import org.prairieserver.prairie.android.ui.components.PrairieDialogActionStyle
import org.prairieserver.prairie.common.diagnostics.DiagnosticsPrompt
import org.prairieserver.prairie.common.diagnostics.diagnosticsReportsReadyText
import org.prairieserver.prairie.common.R as CommonR

@Composable
fun DiagnosticsPromptDialog(
    prompt: DiagnosticsPrompt,
    onReview: () -> Unit,
    onSend: () -> Unit,
    onAlwaysSend: () -> Unit,
    onDontSend: () -> Unit,
    allowAlwaysSend: Boolean = true,
) {
    var confirmAlways by remember { mutableStateOf(false) }
    if (confirmAlways && allowAlwaysSend) {
        PrairieConfirmDialog(
            title = stringResource(CommonR.string.diagnostics_alwaysSend_title),
            body = stringResource(CommonR.string.diagnostics_alwaysSend_promptBody),
            confirmLabel = stringResource(CommonR.string.diagnostics_alwaysSend_action),
            destructive = false,
            onConfirm = onAlwaysSend,
            onDismiss = { confirmAlways = false },
        )
        return
    }
    val reportDescription = diagnosticsReportsReadyText(prompt)
    PrairieDialog(
        title = stringResource(CommonR.string.diagnostics_prompt_title),
        message = if (allowAlwaysSend) {
            reportDescription
        } else {
            reportDescription + "\n\n" + stringResource(CommonR.string.diagnostics_prompt_privacyNotice)
        },
        onDismissRequest = onDontSend,
        actions = run {
            val review = stringResource(CommonR.string.diagnostics_prompt_review)
            val send = stringResource(CommonR.string.diagnostics_consent_confirm)
            val alwaysSend = stringResource(CommonR.string.diagnostics_alwaysSend_action)
            val dontSend = stringResource(CommonR.string.diagnostics_prompt_dontSend)
            buildList {
                add(PrairieDialogAction(review, onReview, PrairieDialogActionStyle.Primary))
                add(PrairieDialogAction(send, onSend))
                if (allowAlwaysSend) add(PrairieDialogAction(alwaysSend, { confirmAlways = true }))
                add(PrairieDialogAction(dontSend, onDontSend))
            }
        },
    )
}
