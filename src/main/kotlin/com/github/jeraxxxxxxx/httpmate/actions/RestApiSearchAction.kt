package com.github.jeraxxxxxxx.httpmate.actions

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
                "Indexing in progress. Please wait until indexing is finished.",
                "Rest API Search"
            )
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Scanning REST APIs...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val items = try {
                    ReadAction.compute<List<RestApiItem>, Exception> {
                        val scanner = RestApiScanner(project)
                        var result = scanner.scan()

                        if (result.isEmpty()) {
                            indicator.text = "Trying fallback scan..."
                            thisLogger().info("Standard scan returned 0 items. Trying fallback scan.")
                            result = scanner.scanFallback()
                        }

                        thisLogger().info("Scanned ${result.size} items")
                        result
                    }
                } catch (ex: Exception) {
                    thisLogger().error("Error during REST API scan", ex)
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Error scanning for REST APIs: ${ex.message}",
                            "Rest API Search Error"
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
