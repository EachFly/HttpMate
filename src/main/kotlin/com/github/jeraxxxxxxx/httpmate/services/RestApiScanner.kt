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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiClass
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

        val annotationQueue = ArrayDeque<PsiClass>()
        RestAnnotations.ALL.mapNotNullTo(annotationQueue) { javaPsiFacade.findClass(it, annotationScope) }
        val visitedAnnotations = mutableSetOf<String>()
        while (annotationQueue.isNotEmpty()) {
            val annotationClass = annotationQueue.removeFirst()
            val annotationName = annotationClass.qualifiedName ?: continue
            if (!visitedAnnotations.add(annotationName)) continue
            try {
                val annotatedElements = AnnotatedElementsSearch.searchPsiMethods(annotationClass, endpointScope)

                annotatedElements.forEach(Processor { method ->
                    ProgressManager.checkCanceled()
                    if (!processedMethods.add(method)) {
                        return@Processor true
                    }
                    pointers.add(SmartPointerManager.createPointer(method))
                    true
                })
                // Definitions may live in binary dependencies, but endpoint queries remain project-only.
                if (annotationName.startsWith("org.springframework.") || annotationName !in RestAnnotations.ALL) {
                    AnnotatedElementsSearch.searchPsiClasses(annotationClass, annotationScope).forEach(Processor { child ->
                        ProgressManager.checkCanceled()
                        if (child.isAnnotationType) annotationQueue.add(child)
                        true
                    })
                }
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
        val implementations = mutableMapOf<PsiClass, List<PsiClass>>()
        val scope = GlobalSearchScope.projectScope(project)

        for (candidate in candidates) {
            ProgressManager.checkCanceled()
            val method = candidate.element ?: continue
            val mapping = RestMappingResolver.method(method) ?: continue
            val owner = method.containingClass
            if (!mapping.spring || owner == null || method.hasModifierProperty(PsiModifier.PRIVATE) ||
                method.hasModifierProperty(PsiModifier.STATIC)) {
                appendItems(items, method, mapping, owner)
                continue
            }
            val controllers = implementations.getOrPut(owner) {
                ClassInheritorsSearch.search(owner, scope, true).findAll().filter(::isConcreteController)
            }
            var foundImplementation = false
            for (controller in controllers) {
                ProgressManager.checkCanceled()
                val implementation = controller.findMethodBySignature(method, true) ?: continue
                val effective = RestMappingResolver.method(implementation, controller) ?: continue
                if (!effective.spring) continue
                appendItems(items, implementation, effective, controller)
                foundImplementation = true
            }
            if (!foundImplementation || isConcreteController(owner)) appendItems(items, method, mapping, owner)
        }
        return items.distinctBy(RestApiItem::locationKey)
    }

    private fun isConcreteController(owner: PsiClass): Boolean =
        !owner.isInterface && !owner.hasModifierProperty(PsiModifier.ABSTRACT) &&
            RestMappingResolver.isSpringController(owner)

    private fun appendItems(
        items: MutableList<RestApiItem>, method: PsiMethod, mapping: RestMappingResolver.Mapping, owner: PsiClass?
    ) {
        val navigationElement = method.navigationElement.takeIf(PsiElement::isValid) ?: method
        val pointer = SmartPointerManager.createPointer(navigationElement)

        for (fullPath in RestMappingResolver.fullPaths(method, mapping, owner)) {
            for (httpMethod in mapping.methods.ifEmpty { listOf("ALL") }) {
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
                        RestMappingResolver.string(expression)
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

}
