package com.github.jeraxxxxxxx.httpmate.services

import com.github.jeraxxxxxxx.httpmate.constants.RestAnnotations
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.Processor

/** Shared by search and documentation: unresolved paths are not the same as an empty path. */
internal object RestMappingResolver {
    data class Mapping(val paths: List<String>, val methods: List<String>, val spring: Boolean)

    private val springVerbs = mapOf(
        "GetMapping" to "GET", "PostMapping" to "POST", "PutMapping" to "PUT",
        "DeleteMapping" to "DELETE", "PatchMapping" to "PATCH"
    ).mapKeys { "org.springframework.web.bind.annotation.${it.key}" }
    private val jaxNamespaces = listOf("javax.ws.rs", "jakarta.ws.rs")

    fun method(method: PsiMethod): Mapping? {
        method.annotations.firstNotNullOfOrNull { spring(it) }?.let { mapping ->
            val owner = method.containingClass?.annotations?.firstNotNullOfOrNull { spring(it) }
            return mapping.copy(methods = SpringRequestMethods.combine(owner?.methods.orEmpty(), mapping.methods))
        }
        val verbs = method.annotations.mapNotNull { annotation ->
            val name = annotation.qualifiedName ?: return@mapNotNull null
            name.substringAfterLast('.').takeIf { verb ->
                verb in SpringRequestMethods.verbs && jaxNamespaces.any { name == "$it.$verb" }
            }
        }.distinct()
        // @Path alone is a subresource locator, not an endpoint accepting all verbs.
        if (verbs.isEmpty()) return null
        val path = method.annotations.firstOrNull { it.qualifiedName in jaxNamespaces.map { namespace -> "$namespace.Path" } }
        return Mapping(path?.let(::paths) ?: listOf(""), verbs, false)
    }

    fun classPaths(psiClass: PsiClass?): List<String> {
        if (psiClass == null) return listOf("")
        psiClass.annotations.firstNotNullOfOrNull { spring(it) }?.let { return it.paths }
        return psiClass.annotations.firstOrNull { it.qualifiedName in RestAnnotations.CLASS_PATH_ANNOTATIONS }
            ?.let(::paths) ?: listOf("")
    }

    fun fullPaths(method: PsiMethod, mapping: Mapping): List<String> =
        (if (mapping.spring) classPaths(method.containingClass) else jaxBasePaths(method.containingClass))
            .flatMap { base -> mapping.paths.map { combine(base, it) } }.distinct()

    private fun jaxBasePaths(owner: PsiClass?, visited: Set<PsiClass> = emptySet()): List<String> {
        if (owner == null || owner in visited || visited.size >= 16) return emptyList()
        owner.annotations.firstOrNull { it.qualifiedName in jaxNamespaces.map { ns -> "$ns.Path" } }
            ?.let { return paths(it) }
        val result = mutableListOf<String>()
        val facade = JavaPsiFacade.getInstance(owner.project)
        for (namespace in jaxNamespaces) {
            val pathClass = facade.findClass("$namespace.Path", GlobalSearchScope.allScope(owner.project)) ?: continue
            AnnotatedElementsSearch.searchPsiMethods(pathClass, GlobalSearchScope.projectScope(owner.project)).forEach(Processor { locator ->
                ProgressManager.checkCanceled()
                if (method(locator) == null && locator.returnType?.let(PsiTypesUtil::getPsiClass) == owner) {
                    val annotation = locator.getAnnotation("$namespace.Path")
                    if (annotation != null) {
                        for (base in jaxBasePaths(locator.containingClass, visited + owner)) {
                            paths(annotation).mapTo(result) { combine(base, it) }
                        }
                    }
                }
                true
            })
        }
        return result.distinct()
    }

    fun combine(base: String, path: String): String =
        "/" + listOf(base, path).map { it.trim().trim('/') }.filter { it.isNotEmpty() }.joinToString("/")

    fun string(value: PsiAnnotationMemberValue?): String? {
        if (value == null) return null
        val helper = JavaPsiFacade.getInstance(value.project).constantEvaluationHelper
        val computed = helper.computeConstantExpression(value)
        if (computed is String) return computed
        val initializer = ((value as? PsiReferenceExpression)?.resolve() as? com.intellij.psi.PsiField)?.initializer
        return initializer?.let { helper.computeConstantExpression(it) } as? String
    }

    private fun pathValues(value: PsiAnnotationMemberValue): List<String> {
        val values = (value as? PsiArrayInitializerMemberValue)?.initializers?.toList() ?: listOf(value)
        if (values.isEmpty()) return listOf("")
        val evaluated = values.map { string(it) }
        return if (evaluated.any { it == null }) emptyList() else evaluated.filterNotNull().distinct()
    }

    private fun paths(annotation: PsiAnnotation): List<String> =
        (annotation.findDeclaredAttributeValue("value") ?: annotation.findDeclaredAttributeValue("path")
            ?: annotation.findAttributeValue("value") ?: annotation.findAttributeValue("path"))
            ?.let(::pathValues) ?: listOf("")

    private fun spring(annotation: PsiAnnotation, visited: Set<String> = emptySet()): Mapping? {
        ProgressManager.checkCanceled()
        val name = annotation.qualifiedName ?: return null
        if (name in visited || visited.size >= 32) return null
        if (name == SpringRequestMethods.ANNOTATION || name in springVerbs) {
            val methods = springVerbs[name]?.let(::listOf) ?: SpringRequestMethods.declared(annotation)
            return Mapping(paths(annotation), methods, true)
        }
        val declaration = annotation.resolveAnnotationType() ?: return null
        val inherited = declaration.annotations.firstNotNullOfOrNull { spring(it, visited + name) } ?: return null
        var result = inherited
        for (attribute in declaration.methods) {
            ProgressManager.checkCanceled()
            val alias = attribute.getAnnotation("org.springframework.core.annotation.AliasFor")
            val target = string(alias?.findDeclaredAttributeValue("attribute"))?.takeIf(String::isNotEmpty)
                ?: string(alias?.findDeclaredAttributeValue("value"))?.takeIf(String::isNotEmpty)
                ?: attribute.name
            if (target !in setOf("value", "path", "method")) continue
            val explicit = annotation.findDeclaredAttributeValue(attribute.name)
            val value = explicit ?: annotation.findAttributeValue(attribute.name) ?: continue
            // An omitted empty default must not erase a fixed path on the meta-annotation.
            val emptyArray = value is PsiArrayInitializerMemberValue && value.initializers.isEmpty()
            if (explicit == null && (emptyArray || string(value) == "")) continue
            result = if (target == "method") {
                val values = (value as? PsiArrayInitializerMemberValue)?.initializers?.toList() ?: listOf(value)
                result.copy(methods = values.map { it.text.substringAfterLast('.') }.filter { it in SpringRequestMethods.verbs })
            } else result.copy(paths = pathValues(value))
        }
        return result
    }
}
