package com.github.jeraxxxxxxx.httpmate.doc

import com.github.jeraxxxxxxx.httpmate.constants.AppConstants
import com.github.jeraxxxxxxx.httpmate.generator.MockJsonGenerator
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.PsiTypesUtil

class DocGenerator {
    private val mockJsonGenerator = MockJsonGenerator()

    fun generate(method: PsiMethod): String {
        val className = method.containingClass?.name ?: "Unknown"
        val methodName = method.name
        val (httpMethod, path) = getApiInfo(method)
        val params = method.parameterList.parameters
        val returnType = method.returnType

        return buildString {
            append("# $className - $methodName\n\n")
            append("## 接口信息\n\n")
            append("| 属性 | 值 |\n")
            append("| --- | --- |\n")
            append("| 接口名称 | $methodName |\n")
            append("| 请求方式 | $httpMethod |\n")
            append("| 接口路径 | `$path` |\n\n")

            append("## 请求参数\n\n")
            appendRequestParameters(params)
            append('\n')

            append("## 响应参数\n\n")
            if (returnType != null && returnType != PsiTypes.voidType()) {
                append("| 参数名称 | 类型 | 说明 |\n")
                append("| --- | --- | --- |\n")
                appendResponseTable(returnType)
            } else {
                append("无响应参数\n")
            }
            append('\n')

            append("## 请求示例\n\n")
            val requestBodyParam =
                params.find { it.hasAnnotation("org.springframework.web.bind.annotation.RequestBody") }
            if (requestBodyParam != null) {
                append("```json\n")
                append(mockJsonGenerator.generate(requestBodyParam.type, 0))
                append("\n```\n\n")
            } else {
                append("无请求体\n\n")
            }

            append("## 响应示例\n\n")
            if (returnType != null && returnType != PsiTypes.voidType()) {
                append("```json\n")
                append(mockJsonGenerator.generate(returnType, 0))
                append("\n```\n")
            } else {
                append("无响应体\n")
            }
        }
    }

    private fun StringBuilder.appendRequestParameters(params: Array<PsiParameter>) {
        if (params.isEmpty()) {
            append("无请求参数\n")
            return
        }

        val nestedTypes = linkedSetOf<PsiClass>()
        append("| 参数名称 | 类型 | 必填 | 长度 | 说明 |\n")
        append("| --- | --- | --- | --- | --- |\n")
        params.forEach { param ->
            val type = param.type
            append(
                "| ${param.name} | ${type.presentableText} | ${isRequired(param)} | ${extractLengthConstraint(param)} | ${
                    extractComment(
                        param
                    )
                } |\n"
            )

            val psiClass = PsiTypesUtil.getPsiClass(type)
            if (shouldExpand(psiClass, type)) {
                nestedTypes += psiClass!!
            }
        }
        append('\n')

        val processedTypes = mutableSetOf<String>()
        repeat(AppConstants.MAX_NESTED_TYPE_DEPTH) {
            if (nestedTypes.isEmpty()) return
            val currentBatch = nestedTypes.toList()
            nestedTypes.clear()
            currentBatch.forEach { psiClass ->
                appendNestedTypeTable(psiClass, processedTypes, nestedTypes)
            }
        }
    }

    private fun StringBuilder.appendResponseTable(type: PsiType) {
        val psiClass = PsiTypesUtil.getPsiClass(type)
        if (shouldExpand(psiClass, type)) {
            ownInstanceFields(psiClass!!).forEach { field ->
                append("| ${field.name} | ${field.type.presentableText} | ${extractFieldComment(field)} |\n")
            }
            return
        }

        append("| result | ${type.presentableText} | |\n")
    }

    private fun StringBuilder.appendNestedTypeTable(
        psiClass: PsiClass,
        processedTypes: MutableSet<String>,
        nestedTypes: MutableSet<PsiClass>
    ) {
        val className = psiClass.qualifiedName ?: psiClass.name ?: return
        if (!processedTypes.add(className)) return

        append("**${psiClass.name}**\n\n")
        append("| 字段名称 | 类型 | 必填 | 长度 | 说明 |\n")
        append("| --- | --- | --- | --- | --- |\n")

        ownInstanceFields(psiClass).forEach { field ->
            append(
                "| ${field.name} | ${field.type.presentableText} | 否 | ${extractFieldLengthConstraint(field)} | ${
                    extractFieldComment(
                        field
                    )
                } |\n"
            )

            val nestedClass = PsiTypesUtil.getPsiClass(field.type)
            if (shouldExpand(nestedClass, field.type)) {
                nestedTypes += nestedClass!!
            }
        }
        append('\n')
    }

    private fun getApiInfo(method: PsiMethod): Pair<String, String> {
        val classPaths = extractClassPaths(method.containingClass)
        var methodPaths = listOf("")
        val httpMethods = linkedSetOf<String>()

        for (annotation in method.annotations) {
            val name = annotation.qualifiedName ?: continue

            if (name.startsWith("org.springframework.web.bind.annotation")) {
                val fixedMethod = when {
                    name.endsWith("GetMapping") -> "GET"
                    name.endsWith("PostMapping") -> "POST"
                    name.endsWith("PutMapping") -> "PUT"
                    name.endsWith("DeleteMapping") -> "DELETE"
                    name.endsWith("PatchMapping") -> "PATCH"
                    else -> null
                }

                if (fixedMethod != null) {
                    httpMethods += fixedMethod
                    methodPaths = extractPaths(annotation)
                    break
                }

                if (name.endsWith("RequestMapping")) {
                    httpMethods += extractRequestMethods(annotation)
                    methodPaths = extractPaths(annotation)
                    break
                }
            }

            if (name.startsWith("javax.ws.rs") || name.startsWith("jakarta.ws.rs")) {
                when {
                    name.endsWith(".GET") -> httpMethods += "GET"
                    name.endsWith(".POST") -> httpMethods += "POST"
                    name.endsWith(".PUT") -> httpMethods += "PUT"
                    name.endsWith(".DELETE") -> httpMethods += "DELETE"
                    name.endsWith(".PATCH") -> httpMethods += "PATCH"
                    name.endsWith(".HEAD") -> httpMethods += "HEAD"
                    name.endsWith(".OPTIONS") -> httpMethods += "OPTIONS"
                    name.endsWith(".Path") -> methodPaths = extractPaths(annotation)
                }
            }
        }

        if (httpMethods.isEmpty()) {
            httpMethods += "ALL"
        }

        val fullPaths = classPaths.flatMap { classPath ->
            methodPaths.map { methodPath -> combinePaths(classPath, methodPath) }
        }.distinct()

        return httpMethods.joinToString(", ") to fullPaths.joinToString(", ")
    }

    internal fun extractClassPaths(psiClass: PsiClass?): List<String> {
        if (psiClass == null) return listOf("")

        val annotationNames = listOf(
            "org.springframework.web.bind.annotation.RequestMapping",
            "javax.ws.rs.Path",
            "jakarta.ws.rs.Path"
        )
        for (annotationName in annotationNames) {
            psiClass.getAnnotation(annotationName)?.let { return extractPaths(it) }
        }
        return listOf("")
    }

    private fun extractPaths(annotation: PsiAnnotation): List<String> {
        val value = annotation.findDeclaredAttributeValue("value")
            ?: annotation.findDeclaredAttributeValue("path")
            ?: annotation.findAttributeValue("value")
            ?: annotation.findAttributeValue("path")
            ?: return listOf("")

        return flattenAnnotationValues(value)
            .mapNotNull { evaluateString(annotation, it) }
            .distinct()
            .ifEmpty { listOf("") }
    }

    private fun extractRequestMethods(annotation: PsiAnnotation): List<String> {
        val value = annotation.findDeclaredAttributeValue("method") ?: return listOf("ALL")
        return flattenAnnotationValues(value)
            .mapNotNull { member ->
                member.text.substringAfterLast('.').trim().takeIf(String::isNotEmpty)
            }
            .distinct()
            .ifEmpty { listOf("ALL") }
    }

    private fun flattenAnnotationValues(value: PsiAnnotationMemberValue): List<PsiAnnotationMemberValue> {
        return if (value is PsiArrayInitializerMemberValue) value.initializers.toList() else listOf(value)
    }

    private fun evaluateString(annotation: PsiAnnotation, value: PsiAnnotationMemberValue): String? {
        val helper = JavaPsiFacade.getInstance(annotation.project).constantEvaluationHelper
        val computed = helper.computeConstantExpression(value)
        if (computed is String) return computed

        val initializer = (value as? PsiReferenceExpression)
            ?.resolve()
            ?.let { it as? PsiField }
            ?.initializer
        val referencedValue = initializer?.let { helper.computeConstantExpression(it) }
        return referencedValue as? String ?: (value as? PsiLiteralExpression)?.value as? String
    }

    private fun combinePaths(classPath: String, methodPath: String): String {
        val segments = listOf(classPath, methodPath)
            .map { it.trim().trim('/') }
            .filter { it.isNotEmpty() }
        return if (segments.isEmpty()) "/" else "/" + segments.joinToString("/")
    }

    private fun isRequired(param: PsiParameter): String {
        val requestParam = param.getAnnotation("org.springframework.web.bind.annotation.RequestParam")
        return if (requestParam?.findAttributeValue("required")?.text == "false") "否" else "是"
    }

    private fun shouldExpand(psiClass: PsiClass?, type: PsiType): Boolean {
        return psiClass != null &&
                !psiClass.isEnum &&
                !type.canonicalText.startsWith("java.") &&
                !isSimpleType(type)
    }

    private fun ownInstanceFields(psiClass: PsiClass): List<PsiField> {
        return psiClass.fields.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }
    }

    private fun isSimpleType(type: PsiType) = when {
        type is PsiPrimitiveType -> true
        type.canonicalText in listOf(
            "java.lang.String", "java.lang.Integer", "java.lang.Long",
            "java.lang.Double", "java.lang.Float", "java.lang.Boolean",
            "java.lang.Byte", "java.lang.Short", "java.lang.Character"
        ) -> true

        type.canonicalText.startsWith("java.math.") -> true
        type.canonicalText.startsWith("java.time.") -> true
        type.canonicalText.startsWith("java.util.Date") -> true
        type.canonicalText.startsWith("java.sql.") -> true
        type.canonicalText.startsWith("java.util.List") -> true
        type.canonicalText.startsWith("java.util.Set") -> true
        type.canonicalText.startsWith("java.util.Map") -> true
        type.canonicalText.startsWith("java.util.Collection") -> true
        else -> false
    }

    private fun extractComment(param: PsiParameter): String {
        val method = param.declarationScope as? PsiMethod ?: return ""
        val docComment = method.docComment ?: return ""
        return docComment.findTagsByName("param")
            .firstOrNull { it.valueElement?.text == param.name }
            ?.dataElements
            ?.joinToString(" ") { it.text }
            ?.trim()
            ?: ""
    }

    private fun extractFieldComment(field: PsiField): String {
        field.docComment?.let {
            val desc = it.descriptionElements.joinToString(" ") { el -> el.text }
                .trim()
                .replace("\n", " ")
                .replace("\\s+".toRegex(), " ")
            if (desc.isNotEmpty()) return desc
        }

        field.children.find { it is PsiComment }?.let {
            return it.text.removePrefix("//").removePrefix("/*").removeSuffix("*/").trim()
        }

        return ""
    }

    private fun extractLengthConstraint(param: PsiParameter): String =
        extractLengthFromAnnotations(param.annotations)

    private fun extractFieldLengthConstraint(field: PsiField): String =
        extractLengthFromAnnotations(field.annotations)

    private fun extractLengthFromAnnotations(annotations: Array<PsiAnnotation>): String {
        val constraints = mutableListOf<String>()

        annotations.forEach { annotation ->
            when {
                annotation.qualifiedName?.endsWith(".Size") == true ||
                        annotation.qualifiedName?.endsWith(".Length") == true -> {
                    val min = annotation.findAttributeValue("min")?.text ?: "0"
                    val max = annotation.findAttributeValue("max")?.text ?: ""
                    when {
                        max.isNotEmpty() && max != Int.MAX_VALUE.toString() -> constraints += "$min-$max"
                        min != "0" -> constraints += ">= $min"
                    }
                }

                annotation.qualifiedName?.endsWith(".Min") == true -> {
                    annotation.findAttributeValue("value")?.text?.let { constraints += ">= $it" }
                }

                annotation.qualifiedName?.endsWith(".Max") == true -> {
                    annotation.findAttributeValue("value")?.text?.let { constraints += "<= $it" }
                }

                annotation.qualifiedName?.endsWith(".DecimalMin") == true -> {
                    annotation.findAttributeValue("value")?.text?.replace("\"", "")?.takeIf(String::isNotEmpty)?.let {
                        constraints += ">= $it"
                    }
                }

                annotation.qualifiedName?.endsWith(".DecimalMax") == true -> {
                    annotation.findAttributeValue("value")?.text?.replace("\"", "")?.takeIf(String::isNotEmpty)?.let {
                        constraints += "<= $it"
                    }
                }

                annotation.qualifiedName?.endsWith(".Pattern") == true -> {
                    annotation.findAttributeValue("regexp")?.text?.replace("\"", "")?.takeIf {
                        it.isNotEmpty() && it.length < 30
                    }?.let { constraints += "格式: $it" }
                }
            }
        }

        return constraints.joinToString(", ")
    }
}
