package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.HttpMateBundle
import com.github.jeraxxxxxxx.httpmate.model.RestApiItem
import com.github.jeraxxxxxxx.httpmate.services.RestApiScanner
import com.github.jeraxxxxxxx.httpmate.ui.RestApiSearchDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.Messages
import java.util.concurrent.Callable
import com.github.jeraxxxxxxx.httpmate.services.ScanLimitExceeded
import com.github.jeraxxxxxxx.httpmate.services.ScanResultLimit

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
        RestSearchSession.current(project)?.let { existing ->
            if (!existing.closed) existing.focus() else Messages.showInfoMessage(
                project, HttpMateBundle.message("rest.search.canceling"), HttpMateBundle.message("rest.search.title")
            )
            return
        }

        if (DumbService.isDumb(project)) {
            Messages.showWarningDialog(
                project,
                HttpMateBundle.message("rest.search.indexing.message"),
                HttpMateBundle.message("rest.search.title")
            )
            return
        }

        val session = RestSearchSession.acquire(project) ?: return
        val dialog = RestApiSearchDialog(project, emptyList()) { session.close(project) }
        session.focus = { dialog.window?.toFront(); dialog.preferredFocusedComponent.requestFocusInWindow() }
        dialog.showLoading()
        dialog.show()
        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(project, HttpMateBundle.message("rest.search.progress.title"), true) {
                override fun run(indicator: ProgressIndicator) {
                    session.indicator.set(indicator)
                    if (dialog.isDisposed) indicator.cancel()
                    indicator.checkCanceled()
                    indicator.isIndeterminate = false
                    val started = System.nanoTime()

                    val items = try {
                        val scanner = RestApiScanner(project)
                        val candidates = ReadAction.nonBlocking(Callable {
                            scanner.collectApiCandidates()
                        }).inSmartMode(project).expireWith(project).executeSynchronously()
                        thisLogger().info("REST candidate collection: ${candidates.size} candidates, ${(System.nanoTime() - started) / 1_000_000} ms")

                        val result = linkedMapOf<List<Any?>, RestApiItem>()
                        fun addResults(items: List<RestApiItem>) {
                            for (item in items) {
                                indicator.checkCanceled()
                                result.putIfAbsent(item.locationKey(), item)
                                ScanResultLimit.checkSize(result.size.toLong())
                            }
                        }
                        val batches = candidates.chunked(200)
                        val totalBatches = batches.size.coerceAtLeast(1)

                        batches.forEachIndexed { index, batch ->
                            indicator.checkCanceled()
                            indicator.text = HttpMateBundle.message("rest.search.progress.title")
                            indicator.text2 =
                                HttpMateBundle.message("rest.search.progress.batch", index + 1, totalBatches)
                            indicator.fraction = (index + 1).toDouble() / totalBatches

                            addResults(ReadAction.nonBlocking(Callable {
                                scanner.scanBatch(batch)
                            }).inSmartMode(project).expireWith(project).executeSynchronously())
                        }

                        if (result.isEmpty()) {
                            indicator.text = HttpMateBundle.message("rest.search.progress.fallback")
                            indicator.text2 = HttpMateBundle.message("rest.search.progress.files")
                            thisLogger().info("Standard scan returned 0 items. Trying fallback scan.")
                            result.clear()
                            addResults(scanner.scanFallback())
                        }

                        thisLogger().info("Scanned ${result.size} items in ${(System.nanoTime() - started) / 1_000_000} ms")
                        result.values.toList()
                    } catch (ex: ScanLimitExceeded) {
                        ProjectBackgroundWork.later(project, indicator) {
                            dialog.scanStopped(ex.message ?: "Scan limit exceeded; results are incomplete.")
                        }
                        return
                    } catch (ex: ProcessCanceledException) {
                        throw ex
                    } catch (ex: Exception) {
                        thisLogger().error("Error during REST API scan", ex)
                        ProjectBackgroundWork.later(project, indicator) {
                            if (dialog.isDisposed) return@later
                            dialog.scanStopped(HttpMateBundle.message("rest.search.error.title"))
                            Messages.showErrorDialog(
                                project,
                                HttpMateBundle.message("rest.search.error.message", ex.message ?: "Unknown error"),
                                HttpMateBundle.message("rest.search.error.title")
                            )
                        }
                        return
                    }

                    ProjectBackgroundWork.later(project, indicator) {
                        dialog.setItems(items)
                    }
                }

                override fun onCancel() {
                    dialog.scanStopped(HttpMateBundle.message("rest.search.canceled"))
                }

                override fun onFinished() {
                    session.finish(project)
                }
            })
    }
}
