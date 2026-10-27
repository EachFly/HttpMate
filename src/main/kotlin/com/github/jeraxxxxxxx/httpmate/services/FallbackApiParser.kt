package com.github.jeraxxxxxxx.httpmate.services

/** Index-independent, structural scan of Java/Kotlin declaration headers. */
internal object FallbackApiParser {
    data class Endpoint(val method: String, val path: String, val offset: Int)
    private data class Token(
        val text: String, val offset: Int, val isString: Boolean = false,
        val endOffset: Int = offset + text.length
    )
    private data class Constant(val name: String, val scope: Int, val expression: List<Token>)
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

        fun paths(evaluate: (List<Token>) -> String?): List<String> {
            val tokens = attribute("value").ifEmpty { attribute("path") }
            if (tokens.isEmpty()) return listOf("")
            if (tokens.first().isString) return listOfNotNull(evaluate(tokens))
            val values = when (tokens.first().text) {
                "{", "[" -> tokens.drop(1).dropLast(1)
                "arrayOf" -> tokens.drop(2).dropLast(1)
                else -> return listOfNotNull(evaluate(tokens))
            }
            if (values.isEmpty()) return listOf("")
            var depth = 0
            var start = 0
            val paths = mutableListOf<String>()
            for (index in 0..values.size) {
                val token = values.getOrNull(index)
                if (index == values.size || (token?.text == "," && !token.isString && depth == 0)) {
                    evaluate(values.subList(start, index))?.let(paths::add)
                    start = index + 1
                } else if (token?.isString == false) {
                    if (token.text == "(") depth++
                    if (token.text == ")") depth--
                }
            }
            return paths.distinct()
        }
    }

    private val verbs = SpringRequestMethods.verbs
    private val springMethods = mapOf(
        "GetMapping" to "GET", "PostMapping" to "POST", "PutMapping" to "PUT",
        "DeleteMapping" to "DELETE", "PatchMapping" to "PATCH"
    )
    private val mappings = springMethods.keys + setOf("RequestMapping", "Path") + verbs

    fun parse(
        text: String,
        checkCanceled: () -> Unit = {},
        resolveExpression: (Int, Int) -> String? = { _, _ -> null }
    ): List<Endpoint> {
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

        fun annotationEnd(start: Int, end: Int): Int {
            var index = start + 2
            while (index + 1 < end && tokens[index].text == ".") index += 2
            if (index < end && tokens[index].text == "(") index = matchingEnd(index, end) + 1
            return index.coerceAtMost(end)
        }

        fun classHeaderEnd(start: Int, end: Int): Int {
            var index = start + 1
            while (index < end && tokens[index].text !in setOf("{", ";", "}", "class", "interface", "object", "fun", "val", "var")) {
                checkCanceled()
                when (tokens[index].text) {
                    "(", "[" -> index = (matchingEnd(index, end) + 1).coerceAtMost(end)
                    "@" -> {
                        // Constructor annotations are part of the header; annotations of a following
                        // bodyless Kotlin declaration must remain for the outer scan.
                        var next = annotationEnd(index, end)
                        while (next < end && tokens[next].text == "@") next = annotationEnd(next, end)
                        if (tokens.getOrNull(next)?.text !in setOf("constructor", "public", "private", "protected", "internal")) break
                        index = next
                    }
                    else -> index++
                }
            }
            return index
        }

        // Keep constants scoped to their declaration, so another controller's API constant cannot leak in.
        val scopeAt = IntArray(tokens.size)
        val parents = mutableMapOf<Int, Int>()
        val owners = mutableMapOf<Int, String>()
        val companionScopes = mutableSetOf<Int>()
        val stack = mutableListOf(-1)
        tokens.forEachIndexed { index, token ->
            checkCanceled()
            scopeAt[index] = stack.last()
            if (!token.isString && token.text == "{") {
                parents[index] = stack.last()
                stack += index
            } else if (!token.isString && token.text == "}" && stack.size > 1) stack.removeAt(stack.lastIndex)
            if (!token.isString && token.text in setOf("class", "interface", "record", "object") &&
                tokens.getOrNull(index - 1)?.text !in setOf(".", ":")) {
                val body = classHeaderEnd(index, tokens.size)
                if (tokens.getOrNull(body)?.text == "{") {
                    val companion = tokens.getOrNull(index - 1)?.text == "companion"
                    owners[body] = tokens.getOrNull(index + 1)?.text?.takeUnless { it == "{" }
                        ?: if (companion) "Companion" else ""
                    if (companion) companionScopes += body
                }
            }
        }
        val constants = mutableListOf<Constant>()
        tokens.forEachIndexed { index, token ->
            checkCanceled()
            if (token.isString || token.text != "=") return@forEachIndexed
            var start = index - 1
            while (start >= 0 && tokens[start].text !in setOf(";", "{", "}") &&
                (start == index - 1 || !text.substring(tokens[start].endOffset, tokens[start + 1].offset).contains('\n'))) start--
            val prefix = tokens.subList(start + 1, index).map { it.text }
            val kotlin = "const" in prefix && "val" in prefix
            if (!kotlin && !("final" in prefix && "String" in prefix)) return@forEachIndexed
            val name = if (kotlin) prefix.getOrNull(prefix.indexOf("val") + 1) else prefix.lastOrNull()
            if (name == null) return@forEachIndexed
            var end = index + 1
            var depth = 0
            while (end < tokens.size) {
                checkCanceled()
                val current = tokens[end]
                if (!current.isString && depth == 0 && current.text in setOf(";", "}", ",")) break
                if (kotlin && end > index + 1 && depth == 0 &&
                    text.substring(tokens[end - 1].endOffset, current.offset).contains('\n') &&
                    tokens[end - 1].text != "+" && current.text != "+") break
                if (!current.isString && current.text == "(") depth++
                if (!current.isString && current.text == ")") {
                    if (depth == 0) break
                    depth--
                }
                end++
            }
            constants += Constant(name, scopeAt[index], tokens.subList(index + 1, end))
        }

        fun ownerName(scope: Int, includeCompanion: Boolean = true): String {
            val names = mutableListOf<String>()
            var current = scope
            while (current != -1) {
                if (includeCompanion || current !in companionScopes) owners[current]?.let(names::add)
                current = parents[current] ?: -1
            }
            return names.asReversed().joinToString(".")
        }

        fun evaluate(expression: List<Token>, visiting: Set<Constant> = emptySet()): String? {
            checkCanceled()
            if (expression.isEmpty() || visiting.size > 64) return null
            resolveExpression(expression.first().offset, expression.last().endOffset)?.let { return it }
            var index = 0
            fun value(): String? {
                val token = expression.getOrNull(index++) ?: return null
                if (token.isString) return token.text
                if (!token.text.first().isJavaIdentifierStart()) return null
                val reference = StringBuilder(token.text)
                while (expression.getOrNull(index)?.text == ".") {
                    index++
                    reference.append('.').append(expression.getOrNull(index++)?.text ?: return null)
                }
                resolveExpression(token.offset, expression[index - 1].endOffset)?.let { return it }
                val name = reference.toString()
                val candidates = if ('.' in name) {
                    constants.filter { constant ->
                        constant.name == name.substringAfterLast('.') &&
                            listOf(ownerName(constant.scope), ownerName(constant.scope, false)).any { owner ->
                                owner.isNotEmpty() && (name.substringBeforeLast('.') == owner ||
                                    name.substringBeforeLast('.').endsWith(".$owner"))
                            }
                    }
                } else {
                    var scope = scopeAt[tokens.binarySearchBy(token.offset) { it.offset }.coerceAtLeast(0)]
                    fun visible() = constants.filter {
                        it.name == name && (it.scope == scope ||
                            (it.scope in companionScopes && parents[it.scope] == scope))
                    }
                    var found = visible()
                    while (found.isEmpty() && scope != -1) {
                        scope = parents[scope] ?: -1
                        found = visible()
                    }
                    found
                }
                val constant = candidates.singleOrNull() ?: return null
                if (constant in visiting) return null
                return evaluate(constant.expression, visiting + constant)
            }
            // Parentheses can wrap an entire expression or a concatenation operand.
            val parts = mutableListOf<String>()
            while (index < expression.size) {
                val part = if (expression[index].text == "(" && !expression[index].isString) {
                    val start = ++index
                    var depth = 1
                    while (index < expression.size && depth > 0) {
                        if (!expression[index].isString) {
                            if (expression[index].text == "(") depth++
                            if (expression[index].text == ")") depth--
                        }
                        if (depth > 0) index++
                    }
                    if (depth != 0) return null
                    val result = evaluate(expression.subList(start, index), visiting)
                    index++
                    result
                } else value()
                parts += part ?: return null
                if (index < expression.size && (expression[index].isString || expression[index++].text != "+")) return null
                if (index == expression.size && expression.last().text == "+" && !expression.last().isString) return null
            }
            return parts.joinToString("")
        }

        fun emit(annotations: List<Annotation>, classPaths: List<String>, classMethods: List<String>) {
            val mapping = annotations.firstOrNull { it.name in mappings } ?: return
            val isJaxRs = mapping.name == "Path" || mapping.name in verbs
            val paths = if (isJaxRs) annotations.firstOrNull { it.name == "Path" }?.paths { evaluate(it) } ?: listOf("")
                else mapping.paths { evaluate(it) }
            val methods = when {
                isJaxRs -> annotations.map { it.name }.filter { it in verbs }.ifEmpty { listOf("ALL") }
                mapping.name in springMethods -> listOf(springMethods.getValue(mapping.name))
                else -> mapping.attribute("method").filter { !it.isString && it.text in verbs }.map { it.text }.distinct()
                    .ifEmpty { listOf("ALL") }
            }
            val effectiveMethods = if (isJaxRs) methods else SpringRequestMethods.combine(classMethods, methods)
            for (base in classPaths) for (path in paths) for (method in effectiveMethods) {
                val segments = listOf(base, path).map { it.trim().trim('/') }.filter { it.isNotEmpty() }
                endpoints += Endpoint(method, "/" + segments.joinToString("/"), mapping.offset)
            }
        }

        fun scanRegion(start: Int, end: Int, classPaths: List<String>, classMethods: List<String>) {
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
                            ?.paths { evaluate(it) } ?: listOf("")
                        val methods = annotations.firstOrNull { it.name == "RequestMapping" }
                            ?.attribute("method")?.filter { !it.isString && it.text in verbs }?.map { it.text }.orEmpty()
                        annotations.clear()
                        index = classHeaderEnd(index, end)
                        if (index < end && tokens[index].text == "{") {
                            val close = matchingEnd(index, end)
                            scanRegion(index + 1, close, paths, methods)
                            index = (close + 1).coerceAtMost(end)
                        }
                    }
                    "fun" -> {
                        emit(annotations, classPaths, classMethods)
                        annotations.clear()
                        index++
                    }
                    "(" -> {
                        // Java method headers; parameter annotations belong to the signature, not a new endpoint.
                        emit(annotations, classPaths, classMethods)
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

        scanRegion(0, tokens.size, listOf(""), emptyList())
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
                    tokens += Token(value.toString(), start, true, index)
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
