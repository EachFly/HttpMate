package com.github.jeraxxxxxxx.httpmate.services

import com.github.jeraxxxxxxx.httpmate.constants.RestAnnotations
import com.github.jeraxxxxxxx.httpmate.model.RestApiItem
import com.github.jeraxxxxxxx.httpmate.ui.RestApiIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor

/**
 * REST API 扫描器
 * 扫描项目中的 Spring Boot 和 JAX-RS REST API 定义
 */
class RestApiScanner(private val project: Project) {

    /**
     * 扫描项目中的所有 REST API
     * @return REST API 列表
     */
    fun collectApiCandidates(): List<SmartPsiElementPointer<PsiMethod>> {
        val endpointScope = GlobalSearchScope.projectScope(project)
        // Annotation definitions usually live in dependency JARs; endpoints must stay project-only.
        val annotationScope = GlobalSearchScope.allScope(project)
        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val processedMethods = mutableSetOf<PsiMethod>()
        val pointers = mutableListOf<SmartPsiElementPointer<PsiMethod>>()

        for (annotationName in RestAnnotations.ALL) {
            try {
                val annotationClass = javaPsiFacade.findClass(annotationName, annotationScope) ?: continue
                val annotatedElements = AnnotatedElementsSearch.searchPsiMethods(annotationClass, endpointScope)

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
            val declaredMethods = extractMethods(method, annotationName, annotation)
            val httpMethods = if (annotationName.startsWith("org.springframework.web.bind.annotation.")) {
                SpringRequestMethods.combine(
                    SpringRequestMethods.declared(method.containingClass?.getAnnotation(SpringRequestMethods.ANNOTATION)),
                    declaredMethods
                )
            } else declaredMethods

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

        return items.distinctBy {
            listOf(it.method, it.path, it.elementPointer.virtualFile?.url, it.navigationOffset)
        }
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
                SpringRequestMethods.declared(annotation).ifEmpty { listOf("ALL") }
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
     * 按声明结构扫描文本作为后备方案；调用方不应持有读锁。
     * 文件快照和导航指针使用短读任务，文本解析在读锁之外完成。
     * @return 扫描到的 REST API 列表
     */
    fun scanFallback(): List<RestApiItem> {
        val items = mutableListOf<RestApiItem>()
        thisLogger().info("Starting fallback text scan...")

        val allFiles = readModel { collectProjectFiles() }
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)

        for (virtualFile in allFiles) {
            ProgressManager.checkCanceled()
            // If a write invalidates a snapshot, retry this file rather than attach stale offsets.
            while (true) {
                ProgressManager.checkCanceled()
                val snapshot = readModel {
                    if (!virtualFile.isValid) return@readModel null
                    val file = psiManager.findFile(virtualFile) ?: return@readModel null
                    file.text to file.modificationStamp
                } ?: break
                val endpoints = FallbackApiParser.parse(snapshot.first, ProgressManager::checkCanceled) { start, end ->
                    if (virtualFile.extension != "java") null else readModel {
                        val file = psiManager.findFile(virtualFile) ?: return@readModel null
                        if (file.modificationStamp != snapshot.second) return@readModel null
                        val expression = PsiTreeUtil.findElementOfClassAtRange(file, start, end, PsiExpression::class.java)
                            ?: return@readModel null
                        if (expression.textRange.startOffset != start || expression.textRange.endOffset != end) {
                            return@readModel null
                        }
                        evaluateString(expression)
                    }
                }
                val fileItems = readModel {
                    if (!virtualFile.isValid) return@readModel emptyList()
                    val file = psiManager.findFile(virtualFile) ?: return@readModel emptyList()
                    if (file.modificationStamp != snapshot.second) return@readModel null
                    endpoints.map { endpoint ->
                        RestApiItem(
                            method = endpoint.method,
                            path = endpoint.path,
                            fileName = file.name,
                            navigationOffset = endpoint.offset,
                            elementPointer = SmartPointerManager.createPointer(file.findElementAt(endpoint.offset) ?: file),
                            icon = RestApiIcons.getIcon(endpoint.method)
                        )
                    }
                } ?: continue
                items.addAll(fileItems)
                break
            }
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

    private fun <T> readModel(action: () -> T): T =
        ReadAction.nonBlocking<T> { action() }.inSmartMode(project).expireWith(project).executeSynchronously()

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

}
