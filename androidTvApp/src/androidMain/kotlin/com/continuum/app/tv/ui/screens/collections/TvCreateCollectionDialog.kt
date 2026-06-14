package com.continuum.app.tv.ui.screens.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.continuum.app.tv.ui.components.tvOutlinedTextFieldColors

/**
 * Dialog for creating a new user collection. Contains a [OutlinedTextField]
 * bound to a local name state and a Create button that calls back with the
 * entered name. The parent owns the loading / error state — we just render
 * whatever it tells us.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCreateCollectionDialog(
    isCreating: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onCreate: (name: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                onClick = {},
                shape = CardDefaults.shape(shape = RoundedCornerShape(24.dp)),
            ) {
                Column(
                    modifier = Modifier
                        .width(640.dp)
                        .padding(48.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text(
                        text = "New Collection",
                        style = TvCreateCollectionTextStyles.Title,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = {
                            androidx.compose.material3.Text(
                                text = "Name",
                                style = TvCreateCollectionTextStyles.FieldLabel,
                            )
                        },
                        singleLine = true,
                        enabled = !isCreating,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        textStyle = TvCreateCollectionTextStyles.Field,
                        colors = tvOutlinedTextFieldColors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = Color.White.copy(alpha = 0.86f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp),
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = TvCreateCollectionTextStyles.Error,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(
                                horizontal = 32.dp,
                                vertical = 12.dp,
                            ),
                        ) {
                            Text(text = "Cancel", style = TvCreateCollectionTextStyles.Button)
                        }
                        Button(
                            onClick = { onCreate(name) },
                            contentPadding = PaddingValues(
                                horizontal = 32.dp,
                                vertical = 12.dp,
                            ),
                        ) {
                            Text(
                                text = if (isCreating) "Creating..." else "Create",
                                style = TvCreateCollectionTextStyles.Button,
                            )
                        }
                    }
                }
            }
        }
    }
}

private object TvCreateCollectionTextStyles {
    val Title = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    )

    val FieldLabel = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    )

    val Field = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = Color.White,
    )

    val Error = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    )

    val Button = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    )
}
