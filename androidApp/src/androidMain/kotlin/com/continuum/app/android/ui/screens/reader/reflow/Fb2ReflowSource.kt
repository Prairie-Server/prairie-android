package com.continuum.app.android.ui.screens.reader.reflow

/**
 * Adapts a FictionBook (FB2) document into reflow sections. Each
 * `<section>` under `<body>` becomes one section. Inner FB2 markup is
 * mapped to a small HTML subset (`<p>`, `<br/>`, `<em>`, `<strong>`);
 * unknown tags are stripped while their text is preserved.
 *
 * Parsing is regex-based, sufficient for well-formed fixtures. Nested
 * sections are treated flatly (split on top-level `<section>`).
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
        sectionSources.getOrNull(index)?.let { renderBody(it.body) }

    override fun baseUrl(index: Int): String = ""

    internal data class SectionSource(val title: String?, val approxChars: Int, val body: String)

    companion object {
        private val SECTION_REGEX =
            Regex("<section\\b[^>]*>(.*?)</section>", RegexOption.DOT_MATCHES_ALL)
        private val TITLE_REGEX =
            Regex("<title\\b[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        private val TAG_REGEX = Regex("<[^>]+>")

        fun fromXml(xml: String): Fb2ReflowSource {
            val body =
                Regex("<body\\b[^>]*>(.*?)</body>", RegexOption.DOT_MATCHES_ALL)
                    .find(xml)?.groupValues?.get(1) ?: xml
            val sources =
                SECTION_REGEX.findAll(body).map { match ->
                    val inner = match.groupValues[1]
                    val titleBlock = TITLE_REGEX.find(inner)?.groupValues?.get(0)
                    val titleText =
                        TITLE_REGEX.find(inner)?.groupValues?.get(1)
                            ?.let { stripTags(it) }?.takeIf { it.isNotEmpty() }
                    val withoutTitle =
                        if (titleBlock != null) inner.replace(titleBlock, "") else inner
                    SectionSource(
                        title = titleText,
                        approxChars = stripTags(withoutTitle).length,
                        body = withoutTitle,
                    )
                }.toList()
            return Fb2ReflowSource(sources)
        }

        private fun stripTags(s: String): String =
            TAG_REGEX.replace(s, " ").replace(Regex("\\s+"), " ").trim()

        /** Maps the FB2 markup subset to HTML, stripping unknown tags. */
        private fun renderBody(body: String): String {
            var html = body
            html = Regex("<empty-line\\s*/>", RegexOption.IGNORE_CASE).replace(html, "<br/>")
            html = Regex("<emphasis\\b[^>]*>", RegexOption.IGNORE_CASE).replace(html, "<em>")
            html = Regex("</emphasis>", RegexOption.IGNORE_CASE).replace(html, "</em>")
            html = Regex("<strong\\b[^>]*>", RegexOption.IGNORE_CASE).replace(html, "<strong>")
            html = Regex("</strong>", RegexOption.IGNORE_CASE).replace(html, "</strong>")
            html = Regex("<p\\b[^>]*>", RegexOption.IGNORE_CASE).replace(html, "<p>")
            html = Regex("</p>", RegexOption.IGNORE_CASE).replace(html, "</p>")
            // Strip any remaining (unknown) tags but keep their text content.
            html = Regex("</?(?!p\\b|br\\b|em\\b|strong\\b)[a-zA-Z][^>]*>").replace(html, "")
            return html.trim()
        }
    }
}
