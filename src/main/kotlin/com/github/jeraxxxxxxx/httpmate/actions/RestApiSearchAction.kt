package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.HttpMateBundle
import com.github.jeraxxxxxxx.httpmate.model.RestApiItem
import com.github.jeraxxxxxxx.httpmate.services.RestApiScanner
import com.github.jeraxxxxxxx.httpmate.ui.RestApiSearchDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.Messages

/**
 * REST API 搜索 Action
 * 使用后台线程扫描项目中的 REST API 定义，避免阻塞 EDT
 */
class RestApiSearchAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        thisLogger().info("RestApiSearchAction triggered")

        if (DumbService.isDumb(project)) {
            Messages.showWarningDialog(
                project,
                HttpMateBundle.message("rest.search.indexing.message"),
                HttpMateBundle.message("rest.search.title")
            )
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, HttpMateBundle.message("rest.search.progress.title"), true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                val items = try {
                    val scanner = RestApiScanner(project)
                    val candidates = ReadAction.compute<List<com.intellij.psi.SmartPsiElementPointer<com.intellij.psi.PsiMethod>>, Exception> {
                        scanner.collectApiCandidates()
                    }

                    val result = mutableListOf<RestApiItem>()
                    val batches = candidates.chunked(200)
                    val totalBatches = batches.size.coerceAtLeast(1)

                    batches.forEachIndexed { index, batch ->
                        indicator.checkCanceled()
                        indicator.text = HttpMateBundle.message("rest.search.progress.title")
                        indicator.text2 = HttpMateBundle.message("rest.search.progress.batch", index + 1, totalBatches)
                        indicator.fraction = (index + 1).toDouble() / totalBatches

                        result += ReadAction.compute<List<RestApiItem>, Exception> {
                            scanner.scanBatch(batch)
                        }
                    }

                    if (result.isEmpty()) {
                        indicator.text = HttpMateBundle.message("rest.search.progress.fallback")
                        indicator.text2 = HttpMateBundle.message("rest.search.progress.files")
                        thisLogger().info("Standard scan returned 0 items. Trying fallback scan.")
                        result.clear()
                        result.addAll(ReadAction.compute<List<RestApiItem>, Exception> {
                            scanner.scanFallback()
                        })
                    }

                    thisLogger().info("Scanned ${result.size} items")
                    result
                } catch (ex: Exception) {
                    thisLogger().error("Error during REST API scan", ex)
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            HttpMateBundle.message("rest.search.error.message", ex.message ?: "Unknown error"),
                            HttpMateBundle.message("rest.search.error.title")
                        )
                    }
                    return
                }

                ApplicationManager.getApplication().invokeLater {
                    RestApiSearchDialog(project, items).show()
                }
            }
        })
    }
}
