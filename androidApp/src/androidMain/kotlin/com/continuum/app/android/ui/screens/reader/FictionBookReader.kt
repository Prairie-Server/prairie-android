package com.continuum.app.android.ui.screens.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.network.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

@Composable
fun FictionBookReader(
    fileUrl: String,
    title: String,
    settings: ReaderDisplaySettings,
    onPageChanged: (Int) -> Unit,
    onPageCountKnown: (Int) -> Unit,
) {
    val context = LocalContext.current
    val okHttp = koinInject<OkHttpClient>()
    val tokenManager = koinInject<TokenManager>()

    val documentResult by produceState<FictionBookLoadResult?>(initialValue = null, fileUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveReaderFile(context, okHttp, fileUrl, tokenManager.getServerUrl(), "fb")
                loadFictionBookText(file)
            }.getOrElse { throwable ->
                FictionBookLoadResult.Error(
                    throwable.message?.takeIf { it.isNotBlank() } ?: "Could not open this FictionBook file.",
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        onPageCountKnown(1)
        onPageChanged(0)
    }

    when (val result = documentResult) {
        null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is FictionBookLoadResult.Loaded -> TextDocumentContent(
            text = result.text,
            settings = settings,
        )
        FictionBookLoadResult.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No readable text found in this FictionBook file.", modifier = Modifier.padding(32.dp))
        }
        is FictionBookLoadResult.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Could not open this FictionBook file.", modifier = Modifier.padding(32.dp))
        }
    }
}

internal sealed interface FictionBookLoadResult {
    data class Loaded(val text: String) : FictionBookLoadResult
    data object Empty : FictionBookLoadResult
    data class Error(val message: String) : FictionBookLoadResult
}

internal fun loadFictionBookText(file: File): FictionBookLoadResult =
    runCatching {
        if (file.looksLikeZip()) {
            ZipFile(file).use { zip ->
                val entry = zip.entries().toList()
                    .filter { !it.isDirectory && it.name.endsWith(".fb2", ignoreCase = true) }
                    .minByOrNull { it.name }
                    ?: return FictionBookLoadResult.Empty
                zip.getInputStream(entry).use(::parseFictionBookText)
            }
        } else {
            FileInputStream(file).use(::parseFictionBookText)
        }
    }.getOrElse { throwable ->
        FictionBookLoadResult.Error(
            throwable.message?.takeIf { it.isNotBlank() } ?: "Could not parse FictionBook file.",
        )
    }

internal fun parseFictionBookText(input: InputStream): FictionBookLoadResult =
    runCatching {
        // Hand the parser the raw byte stream so it sniffs and honors
        // the XML prolog's declared encoding (windows-1251 FB2s are
        // common); pre-decoding via bufferedReader() forced UTF-8.
        val document = secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(InputSource(input))
        val root = document.documentElement ?: return FictionBookLoadResult.Empty
        val description = root.firstElementByTagName("description")
        val titleInfo = description?.firstElementByTagName("title-info")
        val bookTitle = titleInfo?.firstElementByTagName("book-title")?.cleanText()
        val author = titleInfo?.firstElementByTagName("author")?.let(::authorName).orEmpty()
        val body = root.firstElementByTagName("body")
        val bodyText = buildString {
            if (body != null) appendReadableChildren(body)
        }.normalizedReaderText()

        val text = listOfNotNull(
            bookTitle?.takeIf { it.isNotBlank() },
            author.takeIf { it.isNotBlank() },
            bodyText.takeIf { it.isNotBlank() },
        ).joinToString("\n")

        if (text.isBlank()) FictionBookLoadResult.Empty else FictionBookLoadResult.Loaded(text)
    }.getOrElse { throwable ->
        FictionBookLoadResult.Error(
            throwable.message?.takeIf { it.isNotBlank() } ?: "Could not parse FictionBook file.",
        )
    }

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        ).forEach { (feature, enabled) ->
            runCatching { setFeature(feature, enabled) }
        }
    }

private fun StringBuilder.appendReadableChildren(element: Element) {
    for (index in 0 until element.childNodes.length) {
        val child = element.childNodes.item(index)
        if (child.nodeType != Node.ELEMENT_NODE) continue
        val childElement = child as Element
        when (childElement.tagName.lowercase()) {
            "title", "subtitle", "p" -> appendParagraph(childElement.cleanText())
            "v" -> append(childElement.cleanText()).append('\n')
            "empty-line" -> append('\n')
            "section", "poem", "stanza", "body" -> appendReadableChildren(childElement)
            else -> appendReadableChildren(childElement)
        }
    }
}

private fun StringBuilder.appendParagraph(text: String) {
    if (text.isBlank()) return
    append(text).append("\n\n")
}

private fun Element.cleanText(): String =
    textContent
        .replace(Regex("\\s+"), " ")
        .trim()

private fun Element.firstElementByTagName(name: String): Element? =
    getElementsByTagName(name).item(0) as? Element

private fun authorName(author: Element): String {
    val first = author.firstElementByTagName("first-name")?.cleanText().orEmpty()
    val middle = author.firstElementByTagName("middle-name")?.cleanText().orEmpty()
    val last = author.firstElementByTagName("last-name")?.cleanText().orEmpty()
    val nickname = author.firstElementByTagName("nickname")?.cleanText().orEmpty()
    return listOf(first, middle, last).filter { it.isNotBlank() }.joinToString(" ")
        .ifBlank { nickname }
}

private fun String.normalizedReaderText(): String =
    lines()
        .map { it.trimEnd() }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

private fun File.looksLikeZip(): Boolean =
    inputStream().use { input ->
        input.read() == 'P'.code && input.read() == 'K'.code
    }

