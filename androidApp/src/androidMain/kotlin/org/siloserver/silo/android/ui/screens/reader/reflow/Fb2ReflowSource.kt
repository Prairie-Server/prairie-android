package org.siloserver.silo.android.ui.screens.reader.reflow

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Adapts a FictionBook (FB2) document into reflow sections. The parser consumes
 * the raw XML stream so the XML prolog controls character decoding.
 */
class Fb2ReflowSource private constructor(
    private val sectionSources: List<SectionSource>,
) : ReflowableSource {

    override val sections: List<ReflowSection> =
        sectionSources.mapIndexed { i, s ->
            ReflowSection(index = i, title = s.title, approxChars = s.approxChars)
        }

    override val tableOfContents: List<ReflowTocEntry> =
        sections.mapNotNull { s -> s.title?.let { ReflowTocEntry(it, s.index) } }

    override suspend fun html(index: Int): String? =
        sectionSources.getOrNull(index)?.bodyHtml

    override fun baseUrl(index: Int): String = ""

    internal data class SectionSource(val title: String?, val approxChars: Int, val bodyHtml: String)

    companion object {
        fun fromInputStream(input: InputStream): Fb2ReflowSource {
            val document = secureDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(InputSource(input))
            val body = document.documentElement?.firstElementByTagName("body")
                ?: return Fb2ReflowSource(emptyList())
            val sectionElements = body.childElements("section")
                .ifEmpty { listOf(body) }
            val sources = sectionElements.mapNotNull { section ->
                val titleElement = section.childElements("title").firstOrNull()
                val title = titleElement?.textContent?.normalizeWhitespace()?.takeIf { it.isNotEmpty() }
                val bodyHtml = section.childNodesHtml(skip = titleElement).trim()
                val approxChars = section.textContent.orEmpty().normalizeWhitespace().length
                bodyHtml.takeIf { it.isNotBlank() }?.let {
                    SectionSource(title = title, approxChars = approxChars, bodyHtml = it)
                }
            }
            return Fb2ReflowSource(sources)
        }

        fun fromXml(xml: String): Fb2ReflowSource =
            fromInputStream(xml.byteInputStream())

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
    }
}

private fun Element.firstElementByTagName(name: String): Element? {
    val nodes = getElementsByTagName(name)
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element) return node
    }
    return null
}

private fun Element.childElements(name: String): List<Element> {
    val result = mutableListOf<Element>()
    for (i in 0 until childNodes.length) {
        val child = childNodes.item(i)
        if (child is Element && child.tagName.equals(name, ignoreCase = true)) {
            result += child
        }
    }
    return result
}

private fun Node.childNodesHtml(skip: Node? = null): String = buildString {
    val children = childNodes
    for (i in 0 until children.length) {
        val child = children.item(i)
        if (child == skip) continue
        append(child.toHtml())
    }
}

private fun Node.toHtml(): String = when (nodeType) {
    Node.TEXT_NODE -> htmlEscape(nodeValue.orEmpty())
    Node.ELEMENT_NODE -> (this as Element).elementToHtml()
    else -> ""
}

private fun Element.elementToHtml(): String {
    val inner = childNodesHtml()
    return when (tagName.lowercase()) {
        "p" -> "<p>$inner</p>"
        "empty-line" -> "<br/>"
        "emphasis" -> "<em>$inner</em>"
        "strong" -> "<strong>$inner</strong>"
        "section", "body", "title" -> inner
        else -> inner
    }
}

private fun String.normalizeWhitespace(): String =
    replace(Regex("\\s+"), " ").trim()
