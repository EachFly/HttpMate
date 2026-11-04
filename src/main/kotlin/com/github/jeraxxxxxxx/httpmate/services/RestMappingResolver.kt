package com.github.jeraxxxxxxx.httpmate.services

import com.github.jeraxxxxxxx.httpmate.constants.RestAnnotations
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
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

    private fun <T> hierarchy(start: T, parents: (T) -> List<T>): Sequence<T> = sequence {
        val queue = ArrayDeque(listOf(start))
        val visited = mutableSetOf<T>()
        while (queue.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val next = queue.removeFirst()
            if (!visited.add(next)) continue
            yield(next)
            queue.addAll(parents(next))
        }
    }

    private fun springClass(owner: PsiClass?): Mapping? = owner?.let { root ->
        hierarchy(root) { cls -> cls.supers.toList() }.firstNotNullOfOrNull { cls ->
            cls.annotations.firstNotNullOfOrNull { annotation -> spring(annotation) }
        }
    }

    fun isSpringController(owner: PsiClass): Boolean {
        val visited = mutableSetOf<String>()
        fun controller(annotation: PsiAnnotation, depth: Int): Boolean {
            ProgressManager.checkCanceled()
            val name = annotation.qualifiedName ?: return false
            if (name == "org.springframework.stereotype.Controller" ||
                name == "org.springframework.web.bind.annotation.RestController") return true
            if (depth >= 32 || !visited.add(name)) return false
            return annotation.resolveAnnotationType()?.annotations?.any { controller(it, depth + 1) } == true
        }
        return hierarchy(owner) { it.supers.toList() }.any { cls ->
            cls.annotations.any { controller(it, 0) }
        }
    }

    fun method(method: PsiMethod, owner: PsiClass? = method.containingClass): Mapping? {
        hierarchy(method) { it.findSuperMethods().toList() }.firstNotNullOfOrNull { declaration ->
            declaration.annotations.firstNotNullOfOrNull { spring(it) }
        }?.let { mapping ->
            val classMapping = springClass(owner)
            return mapping.copy(methods = SpringRequestMethods.combine(classMapping?.methods.orEmpty(), mapping.methods))
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
        springClass(psiClass)?.let { return it.paths }
        return psiClass.annotations.firstOrNull { it.qualifiedName in RestAnnotations.CLASS_PATH_ANNOTATIONS }
            ?.let(::paths) ?: listOf("")
    }

    fun fullPaths(method: PsiMethod, mapping: Mapping, owner: PsiClass? = method.containingClass): List<String> =
        (if (mapping.spring) classPaths(owner) else jaxBasePaths(owner))
            .flatMap { base -> mapping.paths.map { combine(base, it) } }.distinct()

    private fun jaxBasePaths(owner: PsiClass?, visited: Set<PsiClass> = emptySet()): List<String> {
        if (owner == null || owner in visited || visited.size >= 16) return emptyList()
        val result = mutableListOf<String>()
        owner.annotations.firstOrNull { it.qualifiedName in jaxNamespaces.map { ns -> "$ns.Path" } }
            ?.let { result.addAll(paths(it)) }
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

    private fun spring(
        annotation: PsiAnnotation,
        visited: Set<String> = emptySet(),
        overrides: Map<Pair<String, String>, PsiAnnotationMemberValue> = emptyMap()
    ): Mapping? {
        ProgressManager.checkCanceled()
        val name = annotation.qualifiedName ?: return null
        if (name in visited || visited.size >= 32) return null
        if (name == SpringRequestMethods.ANNOTATION || name in springVerbs) {
            val methods = springVerbs[name]?.let(::listOf)
                ?: overrides[name to "method"]?.let(SpringRequestMethods::values)
                ?: SpringRequestMethods.declared(annotation)
            val path = overrides[name to "path"] ?: overrides[name to "value"]
            return Mapping(path?.let(::pathValues) ?: paths(annotation), methods, true)
        }
        val declaration = annotation.resolveAnnotationType() ?: return null
        val forwarded = overrides.toMutableMap()
        val conventional = mutableMapOf<String, PsiAnnotationMemberValue>()
        for (attribute in declaration.methods) {
            ProgressManager.checkCanceled()
            val alias = attribute.getAnnotation("org.springframework.core.annotation.AliasFor")
            val target = string(alias?.findDeclaredAttributeValue("attribute"))?.takeIf(String::isNotEmpty)
                ?: string(alias?.findDeclaredAttributeValue("value"))?.takeIf(String::isNotEmpty)
                ?: attribute.name
            val explicit = overrides[name to attribute.name] ?: annotation.findDeclaredAttributeValue(attribute.name)
            val value = explicit ?: annotation.findAttributeValue(attribute.name) ?: continue
            // An omitted empty default must not erase a fixed path on the meta-annotation.
            val emptyArray = value is PsiArrayInitializerMemberValue && value.initializers.isEmpty()
            if (explicit == null && (emptyArray || string(value) == "")) continue
            val targetAnnotation = (alias?.findAttributeValue("annotation") as? PsiClassObjectAccessExpression)
                ?.operand?.type?.canonicalText
            if (alias == null && target in setOf("value", "path", "method")) conventional[target] = value
            if (targetAnnotation != null && targetAnnotation != "java.lang.annotation.Annotation") {
                forwarded.putIfAbsent(targetAnnotation to target, value)
            }
        }
        val inherited = declaration.annotations.firstNotNullOfOrNull { spring(it, visited + name, forwarded) } ?: return null
        val path = conventional["path"] ?: conventional["value"]
        return inherited.copy(
            paths = path?.let(::pathValues) ?: inherited.paths,
            methods = conventional["method"]?.let(SpringRequestMethods::values) ?: inherited.methods
        )
    }
}
