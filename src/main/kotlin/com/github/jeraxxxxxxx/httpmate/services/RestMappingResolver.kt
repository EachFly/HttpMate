package com.github.jeraxxxxxxx.httpmate.services

import com.github.jeraxxxxxxx.httpmate.constants.RestAnnotations
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
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

    fun method(method: PsiMethod, owner: PsiClass? = method.containingClass, context: ScanContext? = null): Mapping? {
        hierarchy(method) { it.findSuperMethods().toList() }.firstNotNullOfOrNull { declaration ->
            declaration.annotations.firstNotNullOfOrNull { spring(it) }
        }?.let { mapping ->
            val classMapping = if (context == null) springClass(owner) else context.springMapping(owner)
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
        fullPaths(mapping, if (mapping.spring) classPaths(owner) else ScanContext(method.project).jaxPaths(owner))

    fun fullPaths(mapping: Mapping, bases: List<String>): List<String> {
        val budget = RouteBudget()
        val paths = linkedSetOf<String>()
        for (base in bases) for (path in mapping.paths) {
            budget.step()
            paths.add(combine(base, path))
        }
        return paths.toList()
    }

    /** Read-action confined; the scanner discards this context after any PSI modification. */
    class ScanContext(private val project: Project) {
        private data class Locator(val owner: PsiClass?, val paths: List<String>)
        private var locators: Map<PsiClass, List<Locator>>? = null
        private val roots = mutableMapOf<PsiClass, List<String>>()
        private val springClasses = mutableMapOf<PsiClass?, Mapping?>()
        internal var locatorIndexBuilds = 0
            private set

        fun springMapping(owner: PsiClass?): Mapping? {
            if (!springClasses.containsKey(owner)) springClasses[owner] = springClass(owner)
            return springClasses[owner]
        }

        private fun locatorIndex(): Map<PsiClass, List<Locator>> {
            locators?.let { return it }
            val result = mutableMapOf<PsiClass, MutableList<Locator>>()
            val facade = JavaPsiFacade.getInstance(project)
            for (namespace in jaxNamespaces) {
                val pathClass = facade.findClass("$namespace.Path", GlobalSearchScope.allScope(project)) ?: continue
                AnnotatedElementsSearch.searchPsiMethods(pathClass, GlobalSearchScope.projectScope(project)).forEach(Processor { locator ->
                    ProgressManager.checkCanceled()
                    if (method(locator) == null) {
                        val target = locator.returnType?.let(PsiTypesUtil::getPsiClass)
                        val annotation = locator.getAnnotation("$namespace.Path")
                        if (target != null && annotation != null) {
                            result.getOrPut(target) { mutableListOf() }.add(Locator(locator.containingClass, paths(annotation)))
                        }
                    }
                    true
                })
            }
            locatorIndexBuilds++
            locators = result
            return result
        }

        fun jaxPaths(owner: PsiClass?): List<String> = owner?.let {
            roots.getOrPut(it) { bases(it, emptySet(), RouteBudget()) }
        }.orEmpty()

        // Only top-level answers are cached: cycle-pruned recursive answers depend on visited.
        private fun bases(owner: PsiClass?, visited: Set<PsiClass>, budget: RouteBudget): List<String> {
            budget.step()
            if (owner == null || owner in visited) return emptyList()
            if (visited.size >= 16) throw ScanLimitExceeded("Subresource depth exceeded 16. Results are incomplete.")
            val result = linkedSetOf<String>()
            owner.annotations.firstOrNull { it.qualifiedName in jaxNamespaces.map { ns -> "$ns.Path" } }
                ?.let { result.addAll(paths(it)) }
            for ((locatorOwner, locatorPaths) in locatorIndex()[owner].orEmpty()) {
                ProgressManager.checkCanceled()
                for (base in bases(locatorOwner, visited + owner, budget)) {
                    for (path in locatorPaths) {
                        budget.step()
                        result.add(combine(base, path))
                    }
                }
            }
            return result.toList()
        }
    }

    fun combine(base: String, path: String): String {
        ProgressManager.checkCanceled()
        if (base.length.toLong() + path.length > 16_384) {
            throw ScanLimitExceeded("Route length exceeded 16384 characters. Results are incomplete.")
        }
        return "/" + listOf(base, path).map { it.trim().trim('/') }.filter { it.isNotEmpty() }.joinToString("/")
    }

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
        // Standard meta-annotations cannot contain a Spring mapping.
        if (name.startsWith("java.lang.annotation.")) return null
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
