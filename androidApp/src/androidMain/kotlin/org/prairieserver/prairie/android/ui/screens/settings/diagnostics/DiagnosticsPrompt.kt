package org.prairieserver.prairie.android.ui.screens.settings.diagnostics

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.prairieserver.prairie.common.diagnostics.DiagnosticsPrompt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticsPromptDialog(
    prompt: DiagnosticsPrompt,
    onReview: () -> Unit,
    onSend: () -> Unit,
    onAlwaysSend: () -> Unit,
    onDontSend: () -> Unit,
) {
    var confirmAlways by remember { mutableStateOf(false) }
    if (confirmAlways) {
        AlertDialog(
            onDismissRequest = { confirmAlways = false },
            title = { Text("Always send crash reports?") },
            text = { Text("Future eligible reports may be uploaded automatically until you change this setting.") },
            confirmButton = {
                TextButton(onClick = onAlwaysSend) { Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier = Modifier.width(8.dp))
                    Text("Always send") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAlways = false }) { Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier = Modifier.width(8.dp))
                    Text("Cancel") }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDontSend,
        title = { Text("Prairie encountered a problem") },
        text = {
            Text(
                if (prompt.reportCount == 1) {
                    "A ${prompt.reportType.displayName().lowercase()} report is ready. Review it before deciding whether to send it."
                } else {
                    "${prompt.reportCount} diagnostics reports are ready. Review them before deciding whether to send them."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onReview) { Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier = Modifier.width(8.dp))
                    Text("Review") }
        },
        dismissButton = {
            ColumnButtons(onSend, { confirmAlways = true }, onDontSend)
        },
    )
}

@Composable
private fun ColumnButtons(
    onSend: () -> Unit,
    onAlwaysSend: () -> Unit,
    onDontSend: () -> Unit,
) {
    TextButton(onClick = onSend) { Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier = Modifier.width(8.dp))
                    Text("Send") }
    TextButton(onClick = onAlwaysSend) { Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier = Modifier.width(8.dp))
                    Text("Always send") }
    TextButton(onClick = onDontSend) { Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier = Modifier.width(8.dp))
                    Text("Don't send") }
}
