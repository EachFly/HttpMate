package com.github.jeraxxxxxxx.httpmate.services

/** Index-independent, structural scan of Java/Kotlin declaration headers. */
internal object FallbackApiParser {
    data class Endpoint(val method: String, val path: String, val offset: Int)
    private data class Token(val text: String, val offset: Int, val isString: Boolean = false)
    private data class Annotation(val name: String, val arguments: List<Token>, val offset: Int) {
        fun attribute(name: String): List<Token> {
            var start = 0
            var depth = 0
            for (index in 0..arguments.size) {
                val text = arguments.getOrNull(index)?.text
                val structural = arguments.getOrNull(index)?.isString != true
                if (index == arguments.size || (structural && text == "," && depth == 0)) {
                    val part = arguments.subList(start, index)
                    if (part.getOrNull(1)?.text == "=") {
                        if (part[0].text == name) return part.drop(2)
                    } else if (name == "value" && part.isNotEmpty()) {
                        return part
                    }
                    start = index + 1
                } else if (structural && text in listOf("(", "{", "[")) {
                    depth++
                } else if (structural && text in listOf(")", "}", "]")) {
                    depth--
                }
            }
            return emptyList()
        }

        fun paths(): List<String> {
            val tokens = attribute("value").ifEmpty { attribute("path") }
            return tokens.filter { it.isString }.map { it.text }.distinct().ifEmpty { listOf("") }
        }
    }

    private val verbs = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
    private val springMethods = mapOf(
        "GetMapping" to "GET", "PostMapping" to "POST", "PutMapping" to "PUT",
        "DeleteMapping" to "DELETE", "PatchMapping" to "PATCH"
    )
    private val mappings = springMethods.keys + setOf("RequestMapping", "Path") + verbs

    fun parse(text: String, checkCanceled: () -> Unit = {}): List<Endpoint> {
        val tokens = tokenize(text, checkCanceled)
        val endpoints = mutableListOf<Endpoint>()

        fun matchingEnd(start: Int, end: Int): Int {
            val close = when (tokens[start].text) {
                "(" -> ")"
                "[" -> "]"
                else -> "}"
            }
            var depth = 1
            var index = start + 1
            while (index < end) {
                checkCanceled()
                val token = tokens[index]
                if (!token.isString) {
                    if (token.text == tokens[start].text) depth++
                    if (token.text == close) {
                        depth--
                        if (depth == 0) return index
                    }
                }
                index++
            }
            return end
        }

        fun emit(annotations: List<Annotation>, classPaths: List<String>) {
            val mapping = annotations.firstOrNull { it.name in mappings } ?: return
            val isJaxRs = mapping.name == "Path" || mapping.name in verbs
            val paths = if (isJaxRs) annotations.firstOrNull { it.name == "Path" }?.paths() ?: listOf("")
                else mapping.paths()
            val methods = when {
                isJaxRs -> annotations.map { it.name }.filter { it in verbs }.ifEmpty { listOf("ALL") }
                mapping.name in springMethods -> listOf(springMethods.getValue(mapping.name))
                else -> mapping.attribute("method").map { it.text }.filter { it in verbs }.distinct()
                    .ifEmpty { listOf("ALL") }
            }
            for (base in classPaths) for (path in paths) for (method in methods) {
                val segments = listOf(base, path).map { it.trim().trim('/') }.filter { it.isNotEmpty() }
                endpoints += Endpoint(method, "/" + segments.joinToString("/"), mapping.offset)
            }
        }

        fun scanRegion(start: Int, end: Int, classPaths: List<String>) {
            val annotations = mutableListOf<Annotation>()
            var index = start
            while (index < end) {
                checkCanceled()
                val token = tokens[index]
                if (token.isString) {
                    index++
                    continue
                }
                when (token.text) {
                    "@" -> {
                        val offset = token.offset
                        index++
                        var name = tokens.getOrNull(index)?.text ?: break
                        index++
                        while (index + 1 < end && tokens[index].text == ".") {
                            name = tokens[index + 1].text
                            index += 2
                        }
                        val args = if (index < end && tokens[index].text == "(") {
                            val close = matchingEnd(index, end)
                            val values = tokens.subList(index + 1, close)
                            index = (close + 1).coerceAtMost(end)
                            values
                        } else emptyList()
                        annotations += Annotation(name, args, offset)
                    }
                    "class", "interface", "record", "object" -> {
                        if (tokens.getOrNull(index - 1)?.text in listOf(":", ".")) {
                            index++
                            continue
                        }
                        val paths = annotations.firstOrNull { it.name == "RequestMapping" || it.name == "Path" }
                            ?.paths() ?: listOf("")
                        annotations.clear()
                        index++
                        while (index < end && tokens[index].text !in listOf("{", ";", "}", "@", "class", "fun", "val", "var")) {
                            if (tokens[index].text in listOf("(", "[")) index = matchingEnd(index, end)
                            index++
                        }
                        if (index < end && tokens[index].text == "{") {
                            val close = matchingEnd(index, end)
                            scanRegion(index + 1, close, paths)
                            index = (close + 1).coerceAtMost(end)
                        }
                    }
                    "fun" -> {
                        emit(annotations, classPaths)
                        annotations.clear()
                        index++
                    }
                    "(" -> {
                        // Java method headers; parameter annotations belong to the signature, not a new endpoint.
                        emit(annotations, classPaths)
                        annotations.clear()
                        index = (matchingEnd(index, end) + 1).coerceAtMost(end)
                    }
                    "{" -> {
                        annotations.clear()
                        index = (matchingEnd(index, end) + 1).coerceAtMost(end)
                    }
                    ";", "=", "val", "var", "typealias" -> {
                        annotations.clear()
                        index++
                    }
                    else -> index++
                }
            }
        }

        scanRegion(0, tokens.size, listOf(""))
        return endpoints.distinct()
    }

    private fun tokenize(text: String, checkCanceled: () -> Unit): List<Token> {
        val tokens = mutableListOf<Token>()
        var index = 0
        while (index < text.length) {
            checkCanceled()
            when {
                text[index].isWhitespace() -> index++
                text.startsWith("//", index) -> {
                    index = text.indexOf('\n', index).takeIf { it >= 0 } ?: text.length
                }
                text.startsWith("/*", index) -> {
                    index += 2
                    var depth = 1
                    while (index < text.length && depth > 0) {
                        checkCanceled()
                        when {
                            text.startsWith("/*", index) -> { depth++; index += 2 }
                            text.startsWith("*/", index) -> { depth--; index += 2 }
                            else -> index++
                        }
                    }
                }
                text[index] == '"' || text[index] == '\'' -> {
                    val start = index
                    val quote = text[index]
                    val triple = text.startsWith(quote.toString().repeat(3), index)
                    val delimiter = quote.toString().repeat(if (triple) 3 else 1)
                    index += delimiter.length
                    val value = StringBuilder()
                    while (index < text.length && !text.startsWith(delimiter, index)) {
                        checkCanceled()
                        if (!triple && text[index] == '\\' && index + 1 < text.length) {
                            value.append(when (text[index + 1]) {
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                else -> text[index + 1]
                            })
                            index += 2
                        } else {
                            value.append(text[index++])
                        }
                    }
                    index = (index + delimiter.length).coerceAtMost(text.length)
                    tokens += Token(value.toString(), start, true)
                }
                text[index].isJavaIdentifierStart() -> {
                    val start = index++
                    while (index < text.length && text[index].isJavaIdentifierPart()) index++
                    tokens += Token(text.substring(start, index), start)
                }
                else -> {
                    tokens += Token(text[index].toString(), index)
                    index++
                }
            }
        }
        return tokens
    }
}
