package com.github.jeraxxxxxxx.httpmate.actions

internal object CodeLineAnalyzer {
    private data class CommentSyntax(
        val linePrefixes: List<String> = emptyList(),
        val blockOpen: String? = null,
        val blockClose: String? = null,
        val multilineStrings: List<MultilineString> = emptyList()
    )

    private data class MultilineString(val delimiter: String, val escapes: Boolean = true)

    fun analyze(content: String, extension: String): LineAnalysisResult {
        if (content.isEmpty()) return LineAnalysisResult(0, 0, 0, 0)

        val splitLines = content.split(Regex("\\r\\n|\\n|\\r"))
        val lines = if (splitLines.lastOrNull().isNullOrEmpty()) splitLines.dropLast(1) else splitLines
        val syntax = syntaxFor(extension)

        var codeLines = 0
        var commentLines = 0
        var blankLines = 0
        var inBlockComment = false
        var multilineString: MultilineString? = null

        for (line in lines) {
            var index = 0
            var hasCode = multilineString != null
            var hasComment = inBlockComment

            while (index < line.length) {
                val activeString = multilineString
                if (activeString != null) {
                    hasCode = true
                    val end = findStringEnd(line, index, activeString)
                    if (end < 0) break
                    index = end + activeString.delimiter.length
                    multilineString = null
                    continue
                }

                if (inBlockComment) {
                    val blockClose = syntax.blockClose ?: break
                    val closeIndex = line.indexOf(blockClose, index)
                    if (closeIndex < 0) {
                        break
                    }
                    hasComment = true
                    inBlockComment = false
                    index = closeIndex + blockClose.length
                    continue
                }

                if (line[index].isWhitespace()) {
                    index++
                    continue
                }

                val lineComment = syntax.linePrefixes.firstOrNull { line.startsWith(it, index) }
                if (lineComment != null) {
                    hasComment = true
                    break
                }

                val blockOpen = syntax.blockOpen
                if (blockOpen != null && line.startsWith(blockOpen, index)) {
                    hasComment = true
                    inBlockComment = true
                    index += blockOpen.length
                    continue
                }

                val current = line[index]
                val stringStart = syntax.multilineStrings.firstOrNull { line.startsWith(it.delimiter, index) }
                if (stringStart != null) {
                    hasCode = true
                    multilineString = stringStart
                    index += stringStart.delimiter.length
                    continue
                }
                if (current == '"' || current == '\'' || current == '`') {
                    hasCode = true
                    index = skipQuotedText(line, index, current)
                    continue
                }

                hasCode = true
                index++
            }

            when {
                hasCode -> codeLines++
                hasComment -> commentLines++
                else -> blankLines++
            }
        }

        return LineAnalysisResult(lines.size, codeLines, commentLines, blankLines)
    }

    private fun findStringEnd(line: String, start: Int, string: MultilineString): Int {
        var index = start
        while (index < line.length) {
            if (string.escapes && line[index] == '\\') {
                index += 2
            } else if (line.startsWith(string.delimiter, index)) {
                return index
            } else {
                index++
            }
        }
        return -1
    }

    private fun skipQuotedText(line: String, start: Int, quote: Char): Int {
        var index = start + 1
        while (index < line.length) {
            if (line[index] == '\\') {
                index += 2
                continue
            }
            if (line[index] == quote) {
                return index + 1
            }
            index++
        }
        return line.length
    }

    private fun syntaxFor(extension: String): CommentSyntax {
        return when (extension.lowercase()) {
            "java" -> CommentSyntax(listOf("//"), "/*", "*/", listOf(MultilineString("\"\"\"")))
            "kt", "kts" -> CommentSyntax(listOf("//"), "/*", "*/", listOf(MultilineString("\"\"\"", false)))
            "js", "ts" -> CommentSyntax(listOf("//"), "/*", "*/", listOf(MultilineString("`")))
            "go" -> CommentSyntax(listOf("//"), "/*", "*/", listOf(MultilineString("`", false)))
            "groovy", "gradle" -> CommentSyntax(
                listOf("//"), "/*", "*/", listOf(MultilineString("\"\"\""), MultilineString("'''"))
            )
            "c", "cpp", "h", "hpp", "rs" -> CommentSyntax(listOf("//"), "/*", "*/")

            "css" -> CommentSyntax(blockOpen = "/*", blockClose = "*/")
            "py" -> CommentSyntax(linePrefixes = listOf("#"), multilineStrings = listOf(
                MultilineString("\"\"\""), MultilineString("'''")
            ))
            "yaml", "yml" -> CommentSyntax(listOf("#"))
            "properties" -> CommentSyntax(listOf("#", "!"))
            "sql" -> CommentSyntax(listOf("--"), "/*", "*/")
            "xml", "html", "md" -> CommentSyntax(blockOpen = "<!--", blockClose = "-->")
            else -> CommentSyntax()
        }
    }
}

internal data class LineAnalysisResult(
    val totalLines: Int,
    val codeLines: Int,
    val commentLines: Int,
    val blankLines: Int
)
