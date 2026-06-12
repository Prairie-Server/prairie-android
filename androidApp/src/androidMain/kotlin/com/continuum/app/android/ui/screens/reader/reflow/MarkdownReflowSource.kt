package com.continuum.app.android.ui.screens.reader.reflow

/**
 * Splits Markdown into reflow sections at top-level ATX h1 (`# `) lines.
 * Content before the first h1 forms an untitled section 0. Within a
 * section, ATX headings render as `<h1>`..`<h6>` and blank-line-delimited
 * blocks render as `<p>`.
 */
class MarkdownReflowSource(md: String) : ReflowableSource {
    private val sectionSources: List<SectionSource> = splitSections(md)

    override val sections: List<ReflowSection> =
        sectionSources.mapIndexed { i, s ->
            ReflowSection(index = i, title = s.title, approxChars = s.source.length)
        }

    override val tableOfContents: List<ReflowTocEntry> =
        sections.mapNotNull { s -> s.title?.let { ReflowTocEntry(it, s.index) } }

    override suspend fun html(index: Int): String? {
        val section = sectionSources.getOrNull(index) ?: return null
        return render(section.source)
    }

    override fun baseUrl(index: Int): String = ""

    private data class SectionSource(val title: String?, val source: String)

    private fun splitSections(md: String): List<SectionSource> {
        val lines = md.split("\n")
        val result = mutableListOf<SectionSource>()
        val current = StringBuilder()
        var currentTitle: String? = null
        var started = false

        fun flush() {
            if (started || current.isNotEmpty()) {
                result.add(SectionSource(currentTitle, current.toString()))
            }
        }

        for (line in lines) {
            val h1 = H1_REGEX.matchEntire(line)
            if (h1 != null) {
                flush()
                current.setLength(0)
                currentTitle = h1.groupValues[1].trim()
                started = true
                current.append(line).append("\n")
            } else {
                started = true
                current.append(line).append("\n")
            }
        }
        flush()
        return if (result.isEmpty()) listOf(SectionSource(null, "")) else result
    }

    private fun render(source: String): String {
        val blocks = source.split(BLANK_LINE).map { it.trim() }.filter { it.isNotEmpty() }
        return blocks.joinToString("\n") { block ->
            val heading = HEADING_REGEX.matchEntire(block)
            if (heading != null) {
                val level = heading.groupValues[1].length.coerceIn(1, 6)
                "<h$level>${htmlEscape(heading.groupValues[2].trim())}</h$level>"
            } else {
                "<p>${htmlEscape(block.replace(Regex("\\s*\\n\\s*"), " "))}</p>"
            }
        }
    }

    private companion object {
        val H1_REGEX = Regex("^# (.*)$")
        val HEADING_REGEX = Regex("^(#{1,6}) (.*)$", RegexOption.DOT_MATCHES_ALL)
        val BLANK_LINE = Regex("\\n\\s*\\n")
    }
}
