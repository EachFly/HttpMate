package com.github.jeraxxxxxxx.httpmate.services

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue

/** Mirrors Spring's RequestMethodsRequestCondition.combine (a union, not an intersection). */
internal object SpringRequestMethods {
    const val ANNOTATION = "org.springframework.web.bind.annotation.RequestMapping"
    val verbs = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE")

    fun declared(annotation: PsiAnnotation?): List<String> {
        val value = annotation?.findDeclaredAttributeValue("method") ?: return emptyList()
        val values = if (value is PsiArrayInitializerMemberValue) value.initializers.toList() else listOf(value)
        return values.map { it.text.substringAfterLast('.').trim() }.filter { it in verbs }.distinct()
    }

    fun combine(classMethods: List<String>, methodMethods: List<String>): List<String> =
        (classMethods + methodMethods).filter { it != "ALL" }.distinct().ifEmpty { listOf("ALL") }
}
