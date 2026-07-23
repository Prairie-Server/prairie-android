package org.siloserver.silo.android.ui.screens.reader.reflow

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

internal fun sanitizeEpubChapterHtml(html: String): String {
    val parsed = Jsoup.parseBodyFragment(html)
    parsed.outputSettings().prettyPrint(false)

    val cleaned = EPUB_HTML_CLEANER.clean(parsed)
    cleaned.body().getAllElements().forEach { element ->
        element.removeUnsafeResourceAttributes()
        element.removeUnsafeSvgPaintAttributes()
    }
    return cleaned.body().html()
}

private fun Element.removeUnsafeResourceAttributes() {
    RESOURCE_ATTRIBUTES.forEach { attribute ->
        if (hasAttr(attribute) && !isSafeRelativeEpubResourceUrl(attr(attribute))) {
            removeAttr(attribute)
        }
    }
}

private fun Element.removeUnsafeSvgPaintAttributes() {
    SVG_PAINT_ATTRIBUTES.forEach { attribute ->
        if (hasAttr(attribute) && !isSafeSvgPaintValue(attr(attribute))) {
            removeAttr(attribute)
        }
    }
}

private fun isSafeRelativeEpubResourceUrl(value: String): Boolean {
    val decoded = value.decodePercentEncodedAsciiRecursively() ?: return false
    val normalized = decoded
        .trim()
        .filterNot(Char::isIgnoredUrlPolicyCharacter)

    return normalized.isEmpty() ||
        (
            !normalized.startsWith("/") &&
                !normalized.startsWith("\\") &&
                !normalized.contains('\\') &&
                !ABSOLUTE_URL_SCHEME_REGEX.containsMatchIn(normalized)
            )
}

private fun isSafeSvgPaintValue(value: String): Boolean {
    val decoded = value.decodePercentEncodedAsciiRecursively() ?: return false
    if (decoded.any(Char::isUnsafeSvgPaintCharacter)) return false

    val normalized = decoded.trim()
    return SAFE_SVG_PAINT_KEYWORD_REGEX.matches(normalized) ||
        SAFE_SVG_HEX_COLOR_REGEX.matches(normalized) ||
        SAFE_SVG_COLOR_FUNCTION_REGEX.matches(normalized) ||
        SAFE_LOCAL_SVG_PAINT_URL_REGEX.matches(normalized)
}

private fun String.decodePercentEncodedAsciiRecursively(): String? {
    var decoded = this
    repeat(length.coerceAtMost(MAX_PERCENT_DECODING_PASSES)) {
        val next = decoded.decodePercentEncodedAscii()
        if (next == decoded) return decoded
        decoded = next
    }
    return if (decoded.decodePercentEncodedAscii() == decoded) decoded else null
}

private fun String.decodePercentEncodedAscii(): String {
    val decoded = StringBuilder(length)
    var index = 0
    while (index < length) {
        if (this[index] == '%' && index + 2 < length) {
            val high = this[index + 1].digitToIntOrNull(16)
            val low = this[index + 2].digitToIntOrNull(16)
            val byte = if (high != null && low != null) (high shl 4) or low else null
            if (byte != null && byte <= Char.MAX_VALUE.code && byte < 0x80) {
                decoded.append(byte.toChar())
                index += 3
                continue
            }
        }
        decoded.append(this[index])
        index += 1
    }
    return decoded.toString()
}

private fun Char.isIgnoredUrlPolicyCharacter(): Boolean =
    code <= 0x20 || code == 0x7F || isWhitespace()

private fun Char.isUnsafeSvgPaintCharacter(): Boolean =
    this == '\\' || code < 0x20 || code == 0x7F || (isWhitespace() && this != ' ')

private val ABSOLUTE_URL_SCHEME_REGEX = Regex(
    pattern = """^[a-z][a-z0-9+.-]*:""",
    option = RegexOption.IGNORE_CASE,
)

private val SAFE_SVG_PAINT_KEYWORD_REGEX = Regex("""^[a-z]+(?:-[a-z]+)*$""", RegexOption.IGNORE_CASE)
private val SAFE_SVG_HEX_COLOR_REGEX = Regex("""^#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$""", RegexOption.IGNORE_CASE)
private val SAFE_SVG_COLOR_FUNCTION_REGEX = Regex(
    pattern = """^(?:rgb|rgba|hsl|hsla)\([0-9+\-.,%/ ]*(?:(?:deg|grad|rad|turn)[0-9+\-.,%/ ]*)?\)$""",
    option = RegexOption.IGNORE_CASE,
)
private val SAFE_LOCAL_SVG_PAINT_URL_REGEX = Regex(
    pattern = """^url\(\s*(["']?)#[a-z0-9_.:-]+\1\s*\)$""",
    option = RegexOption.IGNORE_CASE,
)

private const val MAX_PERCENT_DECODING_PASSES = 64

private val RESOURCE_ATTRIBUTES = arrayOf("href", "src", "xlink:href")
private val SVG_PAINT_ATTRIBUTES = arrayOf("fill", "stroke")

private val EPUB_HTML_CLEANER = Cleaner(
    Safelist.none()
        .addTags(
            "a",
            "abbr",
            "address",
            "article",
            "aside",
            "b",
            "bdi",
            "bdo",
            "blockquote",
            "br",
            "caption",
            "cite",
            "code",
            "col",
            "colgroup",
            "data",
            "dd",
            "del",
            "details",
            "dfn",
            "div",
            "dl",
            "dt",
            "em",
            "figcaption",
            "figure",
            "footer",
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6",
            "header",
            "hr",
            "i",
            "img",
            "ins",
            "kbd",
            "li",
            "main",
            "mark",
            "nav",
            "ol",
            "p",
            "pre",
            "q",
            "rp",
            "rt",
            "ruby",
            "s",
            "samp",
            "section",
            "small",
            "span",
            "strong",
            "sub",
            "summary",
            "sup",
            "table",
            "tbody",
            "td",
            "tfoot",
            "th",
            "thead",
            "time",
            "tr",
            "u",
            "ul",
            "var",
            "wbr",
            // SVG presentation elements. Active animation and foreign-content
            // elements are intentionally absent.
            "svg",
            "g",
            "defs",
            "symbol",
            "use",
            "path",
            "circle",
            "ellipse",
            "line",
            "polyline",
            "polygon",
            "rect",
            "image",
            "text",
            "tspan",
            "textpath",
            "clippath",
            "mask",
            "pattern",
            "lineargradient",
            "radialgradient",
            "stop",
            "marker",
            "switch",
            "desc",
            "title",
            // MathML presentation elements. Annotation XML is intentionally
            // absent because it can switch back into active HTML/SVG content.
            "math",
            "mrow",
            "mi",
            "mn",
            "mo",
            "ms",
            "mtext",
            "mspace",
            "mfrac",
            "msqrt",
            "mroot",
            "mstyle",
            "merror",
            "mpadded",
            "mphantom",
            "mfenced",
            "menclose",
            "msub",
            "msup",
            "msubsup",
            "munder",
            "mover",
            "munderover",
            "mmultiscripts",
            "mprescripts",
            "none",
            "mtable",
            "mtr",
            "mtd",
            "semantics",
            "annotation",
        )
        .addAttributes(
            ":all",
            "id",
            "class",
            "title",
            "lang",
            "xml:lang",
            "dir",
            "role",
            "aria-label",
            "aria-labelledby",
            "aria-describedby",
            "aria-hidden",
            "epub:type",
            "href",
            "src",
            "xlink:href",
        )
        .addAttributes("a", "name", "rel")
        .addAttributes("blockquote", "cite")
        .addAttributes("col", "span", "width")
        .addAttributes("colgroup", "span", "width")
        .addAttributes("data", "value")
        .addAttributes("del", "cite", "datetime")
        .addAttributes("details", "open")
        .addAttributes("img", "alt", "width", "height", "loading", "decoding")
        .addAttributes("ins", "cite", "datetime")
        .addAttributes("li", "value")
        .addAttributes("ol", "start", "reversed", "type")
        .addAttributes("q", "cite")
        .addAttributes("td", "abbr", "colspan", "headers", "rowspan")
        .addAttributes("th", "abbr", "colspan", "headers", "rowspan", "scope")
        .addAttributes("time", "datetime")
        .addAttributes(
            "svg",
            "viewbox",
            "preserveaspectratio",
            "version",
            "xmlns",
            "xmlns:xlink",
            "width",
            "height",
        )
        .addAttributes(
            "g",
            "transform",
            "fill",
            "fill-opacity",
            "fill-rule",
            "stroke",
            "stroke-width",
            "stroke-linecap",
            "stroke-linejoin",
            "stroke-miterlimit",
            "stroke-dasharray",
            "stroke-dashoffset",
            "stroke-opacity",
            "opacity",
        )
        .addAttributes("defs", "transform")
        .addAttributes("symbol", "viewbox", "preserveaspectratio")
        .addAttributes("use", "x", "y", "width", "height", "transform")
        .addAttributes(
            "path",
            "d",
            "pathlength",
            "transform",
            "fill",
            "fill-opacity",
            "fill-rule",
            "stroke",
            "stroke-width",
            "stroke-linecap",
            "stroke-linejoin",
            "stroke-miterlimit",
            "stroke-dasharray",
            "stroke-dashoffset",
            "stroke-opacity",
            "opacity",
        )
        .addAttributes("circle", "cx", "cy", "r", "transform", "fill", "stroke", "opacity")
        .addAttributes("ellipse", "cx", "cy", "rx", "ry", "transform", "fill", "stroke", "opacity")
        .addAttributes("line", "x1", "y1", "x2", "y2", "transform", "stroke", "opacity")
        .addAttributes("polyline", "points", "transform", "fill", "stroke", "opacity")
        .addAttributes("polygon", "points", "transform", "fill", "stroke", "opacity")
        .addAttributes("rect", "x", "y", "width", "height", "rx", "ry", "transform", "fill", "stroke", "opacity")
        .addAttributes("image", "x", "y", "width", "height", "preserveaspectratio", "transform")
        .addAttributes("text", "x", "y", "dx", "dy", "text-anchor", "transform", "fill", "stroke")
        .addAttributes("tspan", "x", "y", "dx", "dy")
        .addAttributes("textpath", "startoffset", "method", "spacing")
        .addAttributes("clippath", "clippathunits", "transform")
        .addAttributes("mask", "x", "y", "width", "height", "maskunits", "maskcontentunits")
        .addAttributes("pattern", "x", "y", "width", "height", "patternunits", "patterncontentunits", "patterntransform")
        .addAttributes("lineargradient", "x1", "y1", "x2", "y2", "gradientunits", "gradienttransform", "spreadmethod")
        .addAttributes("radialgradient", "cx", "cy", "r", "fx", "fy", "gradientunits", "gradienttransform", "spreadmethod")
        .addAttributes("stop", "offset", "stop-color", "stop-opacity")
        .addAttributes("marker", "refx", "refy", "markerwidth", "markerheight", "orient", "markerunits", "viewbox")
        .addAttributes("math", "display", "mathvariant", "mathsize", "mathcolor", "mathbackground")
        .addAttributes(
            "mstyle",
            "displaystyle",
            "scriptlevel",
            "scriptsizemultiplier",
            "scriptminsize",
            "mathvariant",
            "mathsize",
            "mathcolor",
            "mathbackground",
        )
        .addAttributes(
            "mo",
            "form",
            "fence",
            "separator",
            "stretchy",
            "symmetric",
            "maxsize",
            "minsize",
            "largeop",
            "movablelimits",
            "accent",
            "lspace",
            "rspace",
        )
        .addAttributes("mfrac", "linethickness", "bevelled")
        .addAttributes("menclose", "notation")
        .addAttributes("mtable", "rowalign", "columnalign", "rowspacing", "columnspacing")
        .addAttributes("mtr", "rowalign", "columnalign")
        .addAttributes("mtd", "rowspan", "columnspan", "rowalign", "columnalign"),
)
