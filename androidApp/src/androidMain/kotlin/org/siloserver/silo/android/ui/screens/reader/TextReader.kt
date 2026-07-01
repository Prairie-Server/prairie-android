package org.siloserver.silo.android.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.siloserver.silo.common.ebook.ReaderDisplaySettings
import org.siloserver.silo.common.ebook.ReaderTheme
import org.siloserver.silo.network.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

@Composable
fun TextReader(
    fileUrl: String,
    title: String,
    settings: ReaderDisplaySettings,
    onPageChanged: (Int) -> Unit,
    onPageCountKnown: (Int) -> Unit,
) {
    val context = LocalContext.current
    val okHttp = koinInject<OkHttpClient>()
    val tokenManager = koinInject<TokenManager>()
    val textResult by produceState<Result<String>?>(initialValue = null, fileUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                resolveReaderFile(context, okHttp, fileUrl, tokenManager.getServerUrl(), "txt").readText()
            }
        }
    }

    LaunchedEffect(Unit) {
        onPageCountKnown(1)
        onPageChanged(0)
    }

    val result = textResult
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    result.exceptionOrNull()?.let { throwable ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(readerLoadErrorMessage(throwable), modifier = Modifier.padding(32.dp))
        }
        return
    }

    TextDocumentContent(text = result.getOrThrow(), settings = settings)
}

@Composable
internal fun TextDocumentContent(
    text: String,
    settings: ReaderDisplaySettings,
    modifier: Modifier = Modifier,
) {
    val colors = textReaderColors(settings.theme)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = (24.dp * settings.marginScale), vertical = 24.dp),
    ) {
        Text(
            text = text,
            color = colors.foreground,
            fontSize = (18.sp * settings.textScale),
            lineHeight = (28.sp * settings.textScale),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private data class TextReaderColors(
    val foreground: Color,
    val background: Color,
)

private fun textReaderColors(theme: ReaderTheme): TextReaderColors =
    when (theme) {
        ReaderTheme.System, ReaderTheme.Light -> TextReaderColors(
            foreground = Color(0xFF1C1B1F),
            background = Color(0xFFFFFBFE),
        )
        ReaderTheme.Sepia -> TextReaderColors(
            foreground = Color(0xFF2B2118),
            background = Color(0xFFF4ECD8),
        )
        ReaderTheme.Dark -> TextReaderColors(
            foreground = Color(0xFFE6E1E5),
            background = Color(0xFF1C1B1F),
        )
    }

