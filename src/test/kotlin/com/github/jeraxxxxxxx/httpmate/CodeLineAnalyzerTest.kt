package com.github.jeraxxxxxxx.httpmate

import com.github.jeraxxxxxxx.httpmate.actions.CodeLineAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

class CodeLineAnalyzerTest {
    @Test
    fun `classifies comments for Python XML and SQL`() {
        val python = CodeLineAnalyzer.analyze("value = 1 # inline\n# comment\n\n", "py")
        assertEquals(3, python.totalLines)
        assertEquals(1, python.codeLines)
        assertEquals(1, python.commentLines)
        assertEquals(1, python.blankLines)

        val xml = CodeLineAnalyzer.analyze("<root>\n<!-- comment -->\n<item/>\n</root>", "xml")
        assertEquals(4, xml.totalLines)
        assertEquals(3, xml.codeLines)
        assertEquals(1, xml.commentLines)
        assertEquals(0, xml.blankLines)

        val sql = CodeLineAnalyzer.analyze(
            "select 1; -- inline\n-- comment\n/* block\nstill block */\n\n",
            "sql"
        )
        assertEquals(5, sql.totalLines)
        assertEquals(1, sql.codeLines)
        assertEquals(3, sql.commentLines)
        assertEquals(1, sql.blankLines)
    }

    @Test
    fun `assigns every line to exactly one category and ignores markers in strings`() {
        val result = CodeLineAnalyzer.analyze(
            "val marker = \"/*\"\nval next = 1\n// comment",
            "kt"
        )

        assertEquals(3, result.totalLines)
        assertEquals(2, result.codeLines)
        assertEquals(1, result.commentLines)
        assertEquals(result.totalLines, result.codeLines + result.commentLines + result.blankLines)
    }

    @Test
    fun `empty files contain zero lines`() {
        assertEquals(LineCounts.ZERO, CodeLineAnalyzer.analyze("", "java").toCounts())
    }

    @Test
    fun multilineStringsContainCodeRatherThanComments() {
        val quotes = "\"\"\""
        val content = "val text = $quotes\n// not a comment\n/* not a comment */\n\n$quotes // real inline comment\n// real comment"
        for (extension in listOf("kt", "kts", "java")) {
            assertEquals(LineCounts(6, 5, 1, 0), CodeLineAnalyzer.analyze(content, extension).toCounts())
        }
    }

    @Test
    fun templateStringsResumeCommentParsingAfterClosing() {
        val tick = 96.toChar()
        val content = "const text = $tick\n// string\n/* string */\n$tick; /* real comment\nstill a comment */\nconst next = 1;"
        for (extension in listOf("js", "ts", "go")) {
            assertEquals(LineCounts(6, 5, 1, 0), CodeLineAnalyzer.analyze(content, extension).toCounts())
        }
    }

    @Test
    fun escapedTemplateDelimitersDoNotTerminateTheString() {
        val tick = 96.toChar()
        val content = "const text = $tick\n\\$tick\n// string\n$tick;\n// comment"
        assertEquals(LineCounts(5, 4, 1, 0), CodeLineAnalyzer.analyze(content, "js").toCounts())
    }

    @Test
    fun pythonTripleQuotesRetainTheirStringMeaning() {
        val content = "text = '''\n# string\n'''\n# comment"
        assertEquals(LineCounts(4, 3, 1, 0), CodeLineAnalyzer.analyze(content, "py").toCounts())
    }

    private fun com.github.jeraxxxxxxx.httpmate.actions.LineAnalysisResult.toCounts() =
        LineCounts(totalLines, codeLines, commentLines, blankLines)

    private data class LineCounts(
        val total: Int,
        val code: Int,
        val comments: Int,
        val blanks: Int
    ) {
        companion object {
            val ZERO = LineCounts(0, 0, 0, 0)
        }
    }
}
