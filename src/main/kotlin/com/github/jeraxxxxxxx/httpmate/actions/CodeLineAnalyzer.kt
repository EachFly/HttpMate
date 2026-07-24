package com.github.jeraxxxxxxx.httpmate.actions

internal object CodeLineAnalyzer {
    private data class CommentSyntax(
        val linePrefixes: List<String> = emptyList(),
        val blockOpen: String? = null,
        val blockClose: String? = null
    )

    fun analyze(content: String, extension: String): LineAnalysisResult {
        if (content.isEmpty()) return LineAnalysisResult(0, 0, 0, 0)

        val splitLines = content.split(Regex("\\r\\n|\\n|\\r"))
        val lines = if (splitLines.lastOrNull().isNullOrEmpty()) splitLines.dropLast(1) else splitLines
        val syntax = syntaxFor(extension)

        var codeLines = 0
        var commentLines = 0
        var blankLines = 0
        var inBlockComment = false

        for (line in lines) {
            var index = 0
            var hasCode = false
            var hasComment = inBlockComment

            while (index < line.length) {
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
            "java", "kt", "kts", "js", "ts", "c", "cpp", "h", "hpp",
            "go", "rs", "groovy", "gradle" -> CommentSyntax(listOf("//"), "/*", "*/")

            "css" -> CommentSyntax(blockOpen = "/*", blockClose = "*/")
            "py", "yaml", "yml" -> CommentSyntax(listOf("#"))
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
