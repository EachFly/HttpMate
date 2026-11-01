package com.github.jeraxxxxxxx.httpmate.doc

import com.github.jeraxxxxxxx.httpmate.constants.AppConstants
import com.github.jeraxxxxxxx.httpmate.generator.JsonFieldResolver
import com.github.jeraxxxxxxx.httpmate.generator.JsonTypeResolver
import com.github.jeraxxxxxxx.httpmate.generator.MockJsonGenerator
import com.github.jeraxxxxxxx.httpmate.services.RestMappingResolver
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.PsiTypesUtil

class DocGenerator {
    private val mockJsonGenerator = MockJsonGenerator()
    private data class DocType(val type: PsiType, val groups: Set<String>)

    fun generate(method: PsiMethod): String {
        ProgressManager.checkCanceled()
        val className = method.containingClass?.name ?: "Unknown"
        val mapping = RestMappingResolver.method(method)
        val paths = mapping?.let { RestMappingResolver.fullPaths(method, it) }.orEmpty()
        val params = method.parameterList.parameters
        val returnType = responseBodyType(method)
        return buildString {
            append("# ${cell(className)} - ${cell(method.name)}\n\n## 接口信息\n\n")
            append("| 属性 | 值 |\n| --- | --- |\n")
            row("接口名称", method.name)
            row("请求方式", mapping?.methods?.ifEmpty { listOf("ALL") }?.joinToString(", ") ?: "无法识别")
            row("接口路径", paths.joinToString(", ").ifEmpty { "无法解析（未生成假定路径）" })
            append("\n## 请求参数\n\n")
            appendRequestParameters(params)
            append("\n## 响应参数\n\n")
            if (returnType != null) {
                append("| 参数名称 | 类型 | 说明 |\n| --- | --- | --- |\n")
                appendResponseTable(returnType)
            } else append("无响应参数\n")
            append("\n## 请求示例\n\n")
            val body = requestBody(method)
            if (body == null) append("无请求体\n\n")
            else append("```json\n${mockJsonGenerator.generate(body.type, 0)}\n```\n\n")
            append("## 响应示例\n\n")
            if (returnType == null) append("无响应体\n")
            else append("```json\n${mockJsonGenerator.generate(returnType, 0)}\n```\n")
        }
    }

    private fun requestBody(method: PsiMethod): PsiParameter? {
        val params = method.parameterList.parameters
        params.firstOrNull { it.hasAnnotation("org.springframework.web.bind.annotation.RequestBody") }?.let { return it }
        if (RestMappingResolver.method(method)?.spring != false) return null
        return params.singleOrNull { param -> param.annotations.none { it.qualifiedName in JAX_INJECTIONS } }
    }

    private fun responseBodyType(method: PsiMethod): PsiType? {
        val declared = method.returnType ?: return null
        val wrapper = listOf("org.springframework.http.ResponseEntity", "org.springframework.http.HttpEntity")
            .firstOrNull { JsonTypeResolver.isType(declared, it) }
        val body = if (wrapper == null) declared else JsonTypeResolver.argument(declared, wrapper, 0)
            ?: PsiType.getJavaLangObject(method.manager, method.resolveScope)
        return body.takeUnless { it == PsiTypes.voidType() || it.canonicalText == "java.lang.Void" }
    }

    private fun StringBuilder.appendRequestParameters(params: Array<PsiParameter>) {
        if (params.isEmpty()) {
            append("无请求参数\n")
            return
        }
        val nested = linkedSetOf<DocType>()
        append("| 参数名称 | 类型 | 必填 | 长度 | 说明 |\n| --- | --- | --- | --- | --- |\n")
        for (param in params) {
            ProgressManager.checkCanceled()
            val groups = ValidationRules.groups(param)
            row(parameterName(param), param.type.presentableText, isRequired(param),
                extractLength(param.annotations.toList(), groups), extractComment(param))
            collectNested(DocType(param.type, groups), nested)
        }
        append('\n')
        appendNestedTables(nested)
    }

    private fun StringBuilder.appendResponseTable(type: PsiType) {
        val nested = linkedSetOf<DocType>()
        val groups = ValidationRules.defaultGroups()
        if (shouldExpand(type)) {
            for (field in JsonFieldResolver.resolve(type)) {
                ProgressManager.checkCanceled()
                row(field.name, field.type.presentableText, extractFieldComment(field.declaration))
                collectNested(DocType(field.type, groups), nested)
            }
        } else {
            row("result", type.presentableText, "")
            collectContainer(DocType(type, groups), nested)
        }
        append('\n')
        appendNestedTables(nested)
    }

    private fun StringBuilder.appendNestedTables(nested: MutableSet<DocType>) {
        val processed = mutableSetOf<Pair<String, Set<String>>>()
        repeat(AppConstants.MAX_NESTED_TYPE_DEPTH) {
            val batch = nested.toList()
            nested.clear()
            for ((type, groups) in batch) {
                ProgressManager.checkCanceled()
                if (!processed.add(type.canonicalText to groups)) continue
                append("**${cell(type.presentableText)}**\n\n")
                if (groups != ValidationRules.defaultGroups()) {
                    val description = if ("?" in groups) "按运行时校验条件判断" else groups.joinToString(", ")
                    append("校验组：${cell(description)}\n\n")
                }
                append("| 字段名称 | 类型 | 必填 | 长度 | 说明 |\n| --- | --- | --- | --- | --- |\n")
                val owner = PsiTypesUtil.getPsiClass(type)
                for (field in JsonFieldResolver.resolve(type)) {
                    ProgressManager.checkCanceled()
                    val annotations = ValidationRules.annotations(field.declaration, owner)
                    row(field.name, field.type.presentableText, ValidationRules.required(annotations, groups),
                        extractLength(annotations, groups), extractFieldComment(field.declaration))
                    collectNested(DocType(field.type, ValidationRules.cascadeGroups(annotations, groups)), nested)
                }
                append('\n')
            }
        }
    }

    private fun collectNested(item: DocType, nested: MutableSet<DocType>) {
        if (shouldExpand(item.type)) nested += item else collectContainer(item, nested)
    }

    private fun collectContainer(item: DocType, nested: MutableSet<DocType>, depth: Int = 0) {
        if (depth >= AppConstants.MAX_NESTED_TYPE_DEPTH) return
        val container = JsonTypeResolver.container(item.type) ?: return
        val args = if (container.kind == JsonTypeResolver.Kind.MAP) container.arguments.drop(1) else container.arguments
        for (type in args.filterNotNull()) {
            ProgressManager.checkCanceled()
            val child = DocType(type, item.groups)
            if (shouldExpand(type)) nested += child else collectContainer(child, nested, depth + 1)
        }
    }

    internal fun extractClassPaths(psiClass: PsiClass?): List<String> = RestMappingResolver.classPaths(psiClass)

    private fun parameterName(param: PsiParameter): String {
        val binding = param.annotations.firstOrNull { it.qualifiedName in NAMED_BINDINGS } ?: return param.name
        return listOf("name", "value").firstNotNullOfOrNull {
            RestMappingResolver.string(binding.findAttributeValue(it))?.takeIf(String::isNotEmpty)
        } ?: param.name
    }

    private fun isRequired(param: PsiParameter): String {
        if (param.type.canonicalText.substringBefore('<') == "java.util.Optional" ||
            param.annotations.any { it.qualifiedName in NULLABLE_ANNOTATIONS }) return "否"
        if (param.annotations.any { it.qualifiedName in JAX_DEFAULTS }) return "否"
        val jax = param.annotations.firstOrNull { it.qualifiedName in JAX_INJECTIONS }
        if (jax != null) return if (jax.qualifiedName?.endsWith(".PathParam") == true) "是" else "否"
        val binding = param.annotations.firstOrNull { it.qualifiedName in REQUEST_BINDINGS } ?: return "是"
        val helper = JavaPsiFacade.getInstance(param.project).constantEvaluationHelper
        if (binding.findAttributeValue("required")?.let { helper.computeConstantExpression(it) } == false) return "否"
        val default = RestMappingResolver.string(binding.findAttributeValue("defaultValue"))
        return if (default != null && default != DEFAULT_NONE) "否" else "是"
    }

    private fun shouldExpand(type: PsiType): Boolean =
        type !is PsiPrimitiveType && !type.canonicalText.startsWith("java.") &&
            JsonTypeResolver.container(type) == null && PsiTypesUtil.getPsiClass(type)?.isEnum == false

    private fun extractComment(param: PsiParameter): String =
        (param.declarationScope as? PsiMethod)?.docComment?.findTagsByName("param")
            ?.firstOrNull { it.valueElement?.text == param.name }?.dataElements?.joinToString(" ") { it.text }?.trim().orEmpty()

    private fun extractFieldComment(field: PsiField): String {
        field.docComment?.let {
            val description = it.descriptionElements.joinToString(" ") { element -> element.text }.trim().replace(Regex("\\s+"), " ")
            if (description.isNotEmpty()) return description
        }
        return field.children.filterIsInstance<PsiComment>().firstOrNull()?.text
            ?.removePrefix("//")?.removePrefix("/*")?.removeSuffix("*/")?.trim().orEmpty()
    }

    private fun extractLength(annotations: List<PsiAnnotation>, groups: Set<String>): String =
        annotations.filter { ValidationRules.active(it, groups) }.mapNotNull { annotation ->
            fun value(name: String) = annotation.findAttributeValue(name)?.let {
                JavaPsiFacade.getInstance(annotation.project).constantEvaluationHelper.computeConstantExpression(it)?.toString() ?: it.text
            }
            when (annotation.qualifiedName?.substringAfterLast('.')) {
                "Size", "Length" -> {
                    val min = value("min") ?: "0"
                    val max = value("max")
                    when {
                        max != null && max != Int.MAX_VALUE.toString() -> "$min-$max"
                        min != "0" -> ">= $min"
                        else -> null
                    }
                }
                "Min" -> value("value")?.let { ">= $it" }
                "Max" -> value("value")?.let { "<= $it" }
                "DecimalMin" -> value("value")?.let { "${if (value("inclusive") == "false") ">" else ">="} $it" }
                "DecimalMax" -> value("value")?.let { "${if (value("inclusive") == "false") "<" else "<="} $it" }
                "Pattern" -> value("regexp")?.takeIf { it.isNotEmpty() && it.length < 30 }?.let { "格式: $it" }
                else -> null
            }
        }.distinct().joinToString(", ")

    private fun StringBuilder.row(vararg values: String) {
        append("| ").append(values.joinToString(" | ") { cell(it) }).append(" |\n")
    }

    private fun cell(text: String): String = text.replace("&", "&amp;").replace("\\", "&#92;")
        .replace("<", "&lt;").replace(">", "&gt;")
        .replace("`", "&#96;").replace("|", "\\|").replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>")

    private companion object {
        const val DEFAULT_NONE = "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n"
        val REQUEST_BINDINGS = setOf("RequestParam", "RequestBody", "RequestHeader", "CookieValue", "PathVariable", "RequestPart", "MatrixVariable")
            .map { "org.springframework.web.bind.annotation.$it" }.toSet()
        val JAX_NAMESPACES = listOf("javax.ws.rs", "jakarta.ws.rs")
        val JAX_INJECTIONS = JAX_NAMESPACES.flatMap { ns ->
            listOf("QueryParam", "PathParam", "HeaderParam", "CookieParam", "MatrixParam", "FormParam", "BeanParam").map { "$ns.$it" } + "$ns.core.Context"
        }.toSet()
        val JAX_DEFAULTS = JAX_NAMESPACES.map { "$it.DefaultValue" }.toSet()
        val NAMED_BINDINGS = REQUEST_BINDINGS - "org.springframework.web.bind.annotation.RequestBody" + JAX_INJECTIONS
        val NULLABLE_ANNOTATIONS = setOf("org.jetbrains.annotations.Nullable", "javax.annotation.Nullable", "jakarta.annotation.Nullable",
            "org.springframework.lang.Nullable", "org.jspecify.annotations.Nullable")
    }
}
