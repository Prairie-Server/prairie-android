package com.continuum.app.android.ui.screens.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.continuum.app.android.ui.screens.auth.AuthColors

private const val PIN_LENGTH = 4

/**
 * Modal dialog for entering a 4-digit profile PIN.
 *
 * @param profileName Name displayed in the title.
 * @param isLoading Whether the PIN is being verified.
 * @param error Error message to display (e.g. "Incorrect PIN").
 * @param onPinComplete Called with the full 4-digit PIN when the user finishes entering.
 * @param onDismiss Called when the user cancels.
 */
@Composable
fun PINEntryDialog(
    profileName: String,
    isLoading: Boolean,
    error: String?,
    onPinComplete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }

    // Clear pin on new error so the user can re-enter.
    LaunchedEffect(error) {
        if (error != null) {
            pin = ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AuthColors.Surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Enter PIN for $profileName",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AuthColors.OnSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN dots row
                PinDots(filledCount = pin.length)

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        color = AuthColors.Error,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Number pad
                NumberPad(
                    enabled = !isLoading,
                    onDigit = { digit ->
                        if (pin.length < PIN_LENGTH) {
                            pin += digit
                            if (pin.length == PIN_LENGTH) {
                                onPinComplete(pin)
                            }
                        }
                    },
                    onBackspace = {
                        if (pin.isNotEmpty()) {
                            pin = pin.dropLast(1)
                        }
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        color = AuthColors.OnSurfaceVariant,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinDots(filledCount: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PIN_LENGTH) { index ->
            val filled = index < filledCount
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .then(
                        if (filled) {
                            Modifier.background(AuthColors.Primary)
                        } else {
                            Modifier.border(2.dp, AuthColors.FieldBorder, CircleShape)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun NumberPad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "back"),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (key in row) {
                    when (key) {
                        "" -> {
                            // Empty spacer
                            Spacer(modifier = Modifier.size(56.dp))
                        }

                        "back" -> {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (enabled) {
                                            Modifier.clickable(onClick = onBackspace)
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    tint = if (enabled) AuthColors.OnSurface else AuthColors.OnSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        else -> {
                            NumberPadKey(
                                digit = key,
                                enabled = enabled,
                                onClick = { onDigit(key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberPadKey(
    digit: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(AuthColors.Background)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) AuthColors.OnSurface else AuthColors.OnSurfaceVariant,
        )
    }
}
