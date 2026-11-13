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
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.Processor

/**
 * REST API 扫描器
 * 扫描项目中的 Spring Boot 和 JAX-RS REST API 定义
 */
class RestApiScanner(private val project: Project) {
    private var modificationCount = -1L
    private val implementations = mutableMapOf<PsiClass, List<PsiClass>>()
    private val directChildren = mutableMapOf<PsiClass, List<PsiClass>>()
    private val resolvedMethods = mutableMapOf<Pair<PsiMethod, PsiClass?>, RestMappingResolver.Mapping?>()
    private val resolvedItems = mutableMapOf<Pair<PsiMethod, PsiClass?>, List<RestApiItem>>()
    private var cachedItemCount = 0L
    internal var methodResolutions = 0
        private set
    private val controllerKinds = mutableMapOf<PsiClass, Boolean>()
    private val springPaths = mutableMapOf<PsiClass?, List<String>>()
    private var mappings = RestMappingResolver.ScanContext(project)
    internal var inheritorQueries = 0
        private set
    internal val locatorIndexBuilds: Int get() = mappings.locatorIndexBuilds

    private fun refreshCaches() {
        val current = PsiModificationTracker.getInstance(project).modificationCount
        if (current == modificationCount) return
        implementations.clear()
        directChildren.clear()
        resolvedMethods.clear()
        resolvedItems.clear()
        cachedItemCount = 0
        controllerKinds.clear()
        springPaths.clear()
        mappings = RestMappingResolver.ScanContext(project)
        modificationCount = current
    }

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
        refreshCaches()
        val items = mutableListOf<RestApiItem>()
        val scope = GlobalSearchScope.projectScope(project)
        // Per-attempt only: a restarted read action must still produce its complete batch.
        val emitted = mutableSetOf<Pair<PsiMethod, PsiClass?>>()
        fun emit(method: PsiMethod, mapping: RestMappingResolver.Mapping, owner: PsiClass?) {
            if (emitted.add(method to owner)) appendItems(items, method, mapping, owner)
        }

        for (candidate in candidates) {
            ProgressManager.checkCanceled()
            val method = candidate.element ?: continue
            val owner = method.containingClass
            val mapping = resolveMethod(method, owner) ?: continue
            if (!mapping.spring || owner == null || method.hasModifierProperty(PsiModifier.PRIVATE) ||
                method.hasModifierProperty(PsiModifier.STATIC)) {
                emit(method, mapping, owner)
                continue
            }
            val controllers = implementations.getOrPut(owner) {
                val queue = ArrayDeque(listOf(owner))
                val visited = mutableSetOf(owner)
                val descendants = mutableListOf<PsiClass>()
                while (queue.isNotEmpty()) {
                    ProgressManager.checkCanceled()
                    val parent = queue.removeFirst()
                    val children = directChildren.getOrPut(parent) {
                        if (parent.hasModifierProperty(PsiModifier.FINAL)) emptyList() else {
                            inheritorQueries++
                            DirectClassInheritorsSearch.search(parent, scope).findAll().toList()
                        }
                    }
                    for (child in children) if (visited.add(child)) {
                        queue.add(child)
                        if (isConcreteController(child)) descendants.add(child)
                    }
                }
                descendants
            }
            var foundImplementation = false
            for (controller in controllers) {
                ProgressManager.checkCanceled()
                val implementation = controller.findMethodBySignature(method, true) ?: continue
                val effective = resolveMethod(implementation, controller) ?: continue
                if (!effective.spring) continue
                emit(implementation, effective, controller)
                foundImplementation = true
            }
            if (!foundImplementation || isConcreteController(owner)) emit(method, mapping, owner)
        }
        return items.distinctBy(RestApiItem::locationKey)
    }

    private fun resolveMethod(method: PsiMethod, owner: PsiClass?): RestMappingResolver.Mapping? {
        val key = method to owner
        if (!resolvedMethods.containsKey(key)) {
            val mapping = RestMappingResolver.method(method, owner, mappings)
            resolvedMethods[key] = mapping
            methodResolutions++
        }
        return resolvedMethods[key]
    }

    private fun isConcreteController(owner: PsiClass): Boolean =
        controllerKinds.getOrPut(owner) {
            !owner.isInterface && !owner.hasModifierProperty(PsiModifier.ABSTRACT) &&
                RestMappingResolver.isSpringController(owner)
        }

    private fun appendItems(
        items: MutableList<RestApiItem>, method: PsiMethod, mapping: RestMappingResolver.Mapping, owner: PsiClass?
    ) {
        val resolved = resolvedItems.getOrPut(method to owner) {
            buildItems(method, mapping, owner).also {
                ScanResultLimit.checkSize(cachedItemCount + it.size)
                cachedItemCount += it.size
            }
        }
        ScanResultLimit.checkSize(items.size.toLong() + resolved.size)
        items.addAll(resolved)
    }

    private fun buildItems(method: PsiMethod, mapping: RestMappingResolver.Mapping, owner: PsiClass?): List<RestApiItem> {
        val items = mutableListOf<RestApiItem>()
        val navigationElement = method.navigationElement.takeIf(PsiElement::isValid) ?: method
        val pointer = SmartPointerManager.createPointer(navigationElement)

        val bases = if (mapping.spring) springPaths.getOrPut(owner) { RestMappingResolver.classPaths(owner) }
            else mappings.jaxPaths(owner)
        for (fullPath in RestMappingResolver.fullPaths(mapping, bases)) {
            for (httpMethod in mapping.methods.ifEmpty { listOf("ALL") }) {
                ProgressManager.checkCanceled()
                ScanResultLimit.checkSize(items.size.toLong() + 1)
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
        return items
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
            var completed = false
            for (attempt in 1..3) {
                ProgressManager.checkCanceled()
                val snapshot = readModel {
                    if (!virtualFile.isValid) return@readModel null
                    val text = FileDocumentManager.getInstance().getCachedDocument(virtualFile)?.immutableCharSequence
                        ?: LoadTextUtil.loadText(virtualFile)
                    if (!FallbackScanPolicy.mightContainRoutes(text)) return@readModel null
                    if (text.length > FallbackScanPolicy.MAX_FILE_CHARS) {
                        throw ScanLimitExceeded("Fallback scan file is too large: ${virtualFile.name}. Results are incomplete.")
                    }
                    val file = psiManager.findFile(virtualFile) ?: return@readModel null
                    file.text to file.modificationStamp
                }
                if (snapshot == null) {
                    completed = true
                    break
                }
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
                        ProgressManager.checkCanceled()
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
                ScanResultLimit.checkSize(items.size.toLong() + fileItems.size)
                items.addAll(fileItems)
                completed = true
                break
            }
            if (!completed) throw ScanLimitExceeded(
                "File kept changing during fallback scan: ${virtualFile.name}. Results are incomplete; retry after editing."
            )
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
