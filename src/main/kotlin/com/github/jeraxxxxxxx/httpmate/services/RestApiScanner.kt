package com.github.jeraxxxxxxx.httpmate.services

import com.github.jeraxxxxxxx.httpmate.constants.RestAnnotations
import com.github.jeraxxxxxxx.httpmate.model.RestApiItem
import com.github.jeraxxxxxxx.httpmate.ui.RestApiIcons
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.Processor

/**
 * REST API 扫描器
 * 扫描项目中的 Spring Boot 和 JAX-RS REST API 定义
 */
class RestApiScanner(private val project: Project) {

    // Regex patterns for fallback scan
    private val requestMappingRegex =
        Regex("@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|Path)\\s*\\(\\s*(?:[a-zA-Z0-9_]+\\s*=\\s*)?[\"']([^\"']+)[\"']")
    private val classMappingRegex =
        Regex("@(RequestMapping|Path)\\s*\\(\\s*(?:[a-zA-Z0-9_]+\\s*=\\s*)?[\"']([^\"']+)[\"']")
    private val methodAttributeRegex = Regex("method\\s*=\\s*(?:RequestMethod\\.)?([A-Z]+)")
    private val jaxRsVerbRegex = Regex("@(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)(?![a-zA-Z])")

    /**
     * 扫描项目中的所有 REST API
     * @return REST API 列表
     */
    fun collectApiCandidates(): List<SmartPsiElementPointer<PsiMethod>> {
        val scope = GlobalSearchScope.projectScope(project)
        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val processedMethods = mutableSetOf<PsiMethod>()
        val pointers = mutableListOf<SmartPsiElementPointer<PsiMethod>>()

        for (annotationName in RestAnnotations.ALL) {
            try {
                val annotationClass = javaPsiFacade.findClass(annotationName, scope) ?: continue
                val annotatedElements = AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope)

                annotatedElements.forEach(Processor { method ->
                    ProgressManager.checkCanceled()
                    if (!processedMethods.add(method)) {
                        return@Processor true
                    }
                    pointers.add(SmartPointerManager.createPointer(method))
                    true
                })
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                thisLogger().warn("Error scanning for annotation: $annotationName", e)
            }
        }
        return pointers
    }

    fun scanBatch(candidates: List<SmartPsiElementPointer<PsiMethod>>): List<RestApiItem> {
        val items = mutableListOf<RestApiItem>()

        for (candidate in candidates) {
            ProgressManager.checkCanceled()
            val method = candidate.element ?: continue
            val annotationData = findRestAnnotation(method) ?: continue
            val (annotationName, annotation) = annotationData
            val navigationElement = method.navigationElement.takeIf(PsiElement::isValid) ?: method
            val pointer = SmartPointerManager.createPointer(navigationElement)

            val classPaths = extractClassPaths(method.containingClass)
            val methodPaths = extractPaths(method, annotation)
            val httpMethods = extractMethods(method, annotationName, annotation)

            for (classPath in classPaths) {
                for (methodPath in methodPaths) {
                    val fullPath = combinePaths(classPath, methodPath)
                    for (httpMethod in httpMethods) {
                        items.add(
                            RestApiItem(
                                method = httpMethod,
                                path = fullPath,
                                fileName = navigationElement.containingFile?.name
                                    ?: method.containingFile?.name
                                    ?: "",
                                navigationOffset = navigationElement.textOffset,
                                elementPointer = pointer,
                                icon = RestApiIcons.getIcon(httpMethod)
                            )
                        )
                    }
                }
            }
        }

        return items.distinctBy { Triple(it.method, it.path, it.navigationOffset) }
    }

    private fun extractClassPaths(psiClass: com.intellij.psi.PsiClass?): List<String> {
        if (psiClass == null) return listOf("")

        for (annotationName in RestAnnotations.CLASS_PATH_ANNOTATIONS) {
            val annotation = psiClass.getAnnotation(annotationName)
            if (annotation != null) {
                return extractPathsFromAnnotation(annotation)
            }
        }
        return listOf("")
    }

    private fun combinePaths(classPath: String, methodPath: String): String {
        val segments = listOf(classPath, methodPath)
            .map { it.trim().trim('/') }
            .filter { it.isNotEmpty() }
        return if (segments.isEmpty()) "/" else "/" + segments.joinToString("/")
    }

    private fun extractPaths(method: PsiMethod, annotation: PsiAnnotation): List<String> {
        val qName = annotation.qualifiedName ?: ""
        if (isJaxRsVerb(qName)) {
            val pathAnnotation = method.getAnnotation("javax.ws.rs.Path")
                ?: method.getAnnotation("jakarta.ws.rs.Path")
            return if (pathAnnotation != null) extractPathsFromAnnotation(pathAnnotation) else listOf("")
        }
        return extractPathsFromAnnotation(annotation)
    }

    private fun extractPathsFromAnnotation(annotation: PsiAnnotation): List<String> {
        val value = annotation.findDeclaredAttributeValue("value")
            ?: annotation.findDeclaredAttributeValue("path")
            ?: annotation.findAttributeValue("value")
            ?: annotation.findAttributeValue("path")
            ?: return listOf("")

        return flattenAnnotationValues(value)
            .mapNotNull(::evaluateString)
            .distinct()
            .ifEmpty { listOf("") }
    }

    private fun flattenAnnotationValues(value: PsiAnnotationMemberValue): List<PsiAnnotationMemberValue> {
        return if (value is PsiArrayInitializerMemberValue) value.initializers.toList() else listOf(value)
    }

    private fun evaluateString(value: PsiAnnotationMemberValue): String? {
        val helper = JavaPsiFacade.getInstance(project).constantEvaluationHelper
        val computed = helper.computeConstantExpression(value)
        if (computed is String) return computed

        val initializer = (value as? PsiReferenceExpression)
            ?.resolve()
            ?.let { it as? com.intellij.psi.PsiField }
            ?.initializer
        val referencedValue = initializer?.let { helper.computeConstantExpression(it) }
        return referencedValue as? String ?: (value as? PsiLiteralExpression)?.value as? String
    }

    private fun isJaxRsVerb(qName: String): Boolean {
        return qName.endsWith(".GET") || qName.endsWith(".POST") ||
                qName.endsWith(".PUT") || qName.endsWith(".DELETE") ||
                qName.endsWith(".PATCH") || qName.endsWith(".HEAD") ||
                qName.endsWith(".OPTIONS")
    }

    private fun extractMethods(
        method: PsiMethod,
        annotationName: String,
        annotation: PsiAnnotation
    ): List<String> {
        return when (annotationName) {
            "org.springframework.web.bind.annotation.GetMapping" -> listOf("GET")
            "org.springframework.web.bind.annotation.PostMapping" -> listOf("POST")
            "org.springframework.web.bind.annotation.PutMapping" -> listOf("PUT")
            "org.springframework.web.bind.annotation.DeleteMapping" -> listOf("DELETE")
            "org.springframework.web.bind.annotation.PatchMapping" -> listOf("PATCH")
            "org.springframework.web.bind.annotation.RequestMapping" -> {
                val methodAttr = annotation.findDeclaredAttributeValue("method")
                    ?: return listOf("ALL")
                flattenAnnotationValues(methodAttr)
                    .mapNotNull { value ->
                        value.text.substringAfterLast('.').trim().takeIf(String::isNotEmpty)
                    }
                    .distinct()
                    .ifEmpty { listOf("ALL") }
            }

            "javax.ws.rs.GET", "jakarta.ws.rs.GET" -> listOf("GET")
            "javax.ws.rs.POST", "jakarta.ws.rs.POST" -> listOf("POST")
            "javax.ws.rs.PUT", "jakarta.ws.rs.PUT" -> listOf("PUT")
            "javax.ws.rs.DELETE", "jakarta.ws.rs.DELETE" -> listOf("DELETE")
            "javax.ws.rs.PATCH", "jakarta.ws.rs.PATCH" -> listOf("PATCH")
            "javax.ws.rs.HEAD", "jakarta.ws.rs.HEAD" -> listOf("HEAD")
            "javax.ws.rs.OPTIONS", "jakarta.ws.rs.OPTIONS" -> listOf("OPTIONS")
            "javax.ws.rs.Path", "jakarta.ws.rs.Path" -> {
                listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
                    .filter { verb ->
                        method.hasAnnotation("javax.ws.rs.$verb") || method.hasAnnotation("jakarta.ws.rs.$verb")
                    }
                    .ifEmpty { listOf("ALL") }
            }

            else -> listOf("UNKNOWN")
        }
    }

    /**
     * 使用文本正则扫描作为后备方案
     * 当 PSI 索引未准备好或需要扫描非索引文件时使用
     * @return 扫描到的 REST API 列表
     */
    fun scanFallback(): List<RestApiItem> {
        val items = mutableListOf<RestApiItem>()
        thisLogger().info("Starting fallback text scan...")

        val allFiles = collectProjectFiles()
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)

        for (virtualFile in allFiles) {
            ProgressManager.checkCanceled()
            val file = psiManager.findFile(virtualFile) ?: continue
            items.addAll(scanFileForApis(file))
        }

        thisLogger().info("Fallback scan found ${items.size} items")
        return items
    }

    private fun collectProjectFiles(): Collection<com.intellij.openapi.vfs.VirtualFile> {
        val scope = GlobalSearchScope.projectScope(project)
        val javaFiles = com.intellij.psi.search.FilenameIndex.getAllFilesByExt(project, "java", scope)
        val ktFiles = com.intellij.psi.search.FilenameIndex.getAllFilesByExt(project, "kt", scope)
        return javaFiles + ktFiles
    }

    private fun scanFileForApis(file: com.intellij.psi.PsiFile): List<RestApiItem> {
        val items = mutableListOf<RestApiItem>()
        val text = file.text

        // Simple heuristic: find class level mapping
        var classPath = ""
        val classMatch = classMappingRegex.find(text)
        if (classMatch != null) {
            classPath = classMatch.groupValues[2]
        }

        // Find method mappings
        val matches = requestMappingRegex.findAll(text)
        for (match in matches) {
            ProgressManager.checkCanceled()
            val annotationType = match.groupValues[1]
            val path = match.groupValues[2]

            // Skip if it looks like the class level mapping we just found (crude check)
            if (path == classPath && (annotationType == "RequestMapping" || annotationType == "Path")) continue

            val fullPath = combinePaths(classPath, path)
            var method = mapAnnotationToMethod(annotationType)

            // Refine method if needed
            method = refineMethodFromContext(annotationType, match, text, method)

            // Find the element offset to navigate to
            val offset = match.range.first
            val element = file.findElementAt(offset) ?: file
            val pointer = SmartPointerManager.createPointer(element)
            items.add(
                RestApiItem(
                    method = method,
                    path = fullPath,
                    fileName = file.name,
                    navigationOffset = offset,
                    elementPointer = pointer,
                    icon = RestApiIcons.getIcon(method)
                )
            )
        }
        return items
    }

    private fun findRestAnnotation(method: PsiMethod): Pair<String, PsiAnnotation>? {
        for (annotation in method.annotations) {
            if (!RestAnnotations.matches(annotation)) continue

            val annotationName = annotation.qualifiedName
                ?: RestAnnotations.ALL.firstOrNull { it.endsWith(".${annotation.nameReferenceElement?.referenceName}") }
                ?: continue
            return annotationName to annotation
        }
        return null
    }

    private fun refineMethodFromContext(
        annotationType: String,
        match: MatchResult,
        text: String,
        defaultMethod: String
    ): String {
        var method = defaultMethod

        // If it's RequestMapping, try to find the method attribute
        if (annotationType == "RequestMapping") {
            val range = match.range
            // Expand range to find closing ')'
            var endIndex = range.last
            var openCount = 1
            while (endIndex < text.length && openCount > 0) {
                endIndex++
                if (endIndex < text.length) {
                    if (text[endIndex] == '(') openCount++
                    if (text[endIndex] == ')') openCount--
                }
            }

            if (endIndex < text.length) {
                val annotationContent = text.substring(range.first, endIndex + 1)
                val methodMatch = methodAttributeRegex.find(annotationContent)
                if (methodMatch != null) {
                    method = methodMatch.groupValues[1]
                }
            }
        } else if (annotationType == "Path") {
            // For JAX-RS @Path, look for @GET, @POST etc. in the vicinity
            // We look 200 chars before and after the @Path match
            val startSearch = (match.range.first - 200).coerceAtLeast(0)
            val endSearch = (match.range.last + 200).coerceAtMost(text.length)
            val vicinityText = text.substring(startSearch, endSearch)

            val verbMatch = jaxRsVerbRegex.find(vicinityText)
            if (verbMatch != null) {
                method = verbMatch.groupValues[1]
            }
        }
        return method
    }

    private fun mapAnnotationToMethod(annotation: String): String {
        return when (annotation) {
            "GetMapping" -> "GET"
            "PostMapping" -> "POST"
            "PutMapping" -> "PUT"
            "DeleteMapping" -> "DELETE"
            "PatchMapping" -> "PATCH"
            "RequestMapping", "Path" -> "ALL"
            else -> "UNKNOWN"
        }
    }
}
