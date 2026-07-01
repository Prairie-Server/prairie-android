package org.siloserver.silo.tv.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Generic single-field text-input dialog for TV. Android TV/Shield needs a
 * platform EditText here: Compose TextField can leave the IME attached to the
 * AndroidComposeView with inputType=0, which makes profile name/PIN entry fail.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTextInputDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onConfirm: (text: String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
    isBusy: Boolean = false,
    errorMessage: String? = null,
    allowBlank: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var text by remember { mutableStateOf(initialValue) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TvPlatformEditText(
                        value = text,
                        hint = label,
                        enabled = !isBusy,
                        keyboardType = keyboardType,
                        onValueChange = { text = it },
                        onDone = { if (!isBusy && (allowBlank || text.isNotBlank())) onConfirm(text) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(text = "Cancel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { if (!isBusy && (allowBlank || text.isNotBlank())) onConfirm(text) },
                            enabled = !isBusy,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = if (isBusy) "..." else confirmLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvPlatformEditText(
    value: String,
    hint: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnDone by rememberUpdatedState(onDone)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val editText = EditText(context)
            editText.setSingleLine(true)
            editText.setText(value)
            editText.hint = hint
            editText.isEnabled = enabled
            editText.inputType = tvInputTypeFor(keyboardType)
            editText.imeOptions = EditorInfo.IME_ACTION_DONE
            editText.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            editText.setTextColor(AndroidColor.WHITE)
            editText.setHintTextColor(AndroidColor.argb(115, 255, 255, 255))
            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            editText.includeFontPadding = false
            editText.setPadding(16.dpToPx(context), 0, 16.dpToPx(context), 0)
            editText.background = tvEditTextBackground()
            editText.setSelectAllOnFocus(false)
            editText.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        currentOnValueChange(s?.toString().orEmpty())
                    }

                    override fun afterTextChanged(s: Editable?) = Unit
                },
            )
            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    currentOnDone()
                    true
                } else {
                    false
                }
            }
            editText.post {
                editText.requestFocus()
                val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
            editText
        },
        update = { editText ->
            editText.isEnabled = enabled
            editText.inputType = tvInputTypeFor(keyboardType)
            if (editText.text.toString() != value) {
                editText.setText(value)
                editText.setSelection(value.length)
            }
        },
    )
}

private fun tvInputTypeFor(keyboardType: KeyboardType): Int =
    if (keyboardType == KeyboardType.Number || keyboardType == KeyboardType.NumberPassword) {
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    } else {
        InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_WORDS or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

private fun tvEditTextBackground(): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 6f
        setColor(AndroidColor.BLACK)
        setStroke(1, AndroidColor.argb(120, 255, 255, 255))
    }

private fun Int.dpToPx(context: Context): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        toFloat(),
        context.resources.displayMetrics,
    ).toInt()
