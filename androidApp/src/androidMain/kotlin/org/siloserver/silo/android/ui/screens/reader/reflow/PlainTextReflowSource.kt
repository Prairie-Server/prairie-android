package org.siloserver.silo.android.ui.screens.reader.reflow

/**
 * Renders a flat plaintext file as a single reflow section. Blank-line
 * runs delimit paragraphs; single newlines inside a block become spaces.
 */
class PlainTextReflowSource(private val text: String) : ReflowableSource {
    override val sections: List<ReflowSection> =
        listOf(ReflowSection(index = 0, title = null, approxChars = text.length))

    override val tableOfContents: List<ReflowTocEntry> = emptyList()

    override suspend fun html(index: Int): String? {
        if (index != 0) return null
        return text
            .split(BLANK_LINE)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { block ->
                "<p>${htmlEscape(block.replace(Regex("\\s*\\n\\s*"), " "))}</p>"
            }
    }

    override fun baseUrl(index: Int): String = ""

    private companion object {
        val BLANK_LINE = Regex("\\n\\s*\\n")
    }
}
