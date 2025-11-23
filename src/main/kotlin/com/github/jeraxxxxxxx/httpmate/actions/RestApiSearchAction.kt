package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.services.RestApiScanner
import com.github.jeraxxxxxxx.httpmate.ui.RestApiSearchDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager

class RestApiSearchAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val logger = com.intellij.openapi.diagnostic.Logger.getInstance(RestApiSearchAction::class.java)
        logger.info("RestApiSearchAction triggered")

        if (com.intellij.openapi.project.DumbService.isDumb(project)) {
            com.intellij.openapi.ui.Messages.showWarningDialog(project, "Indexing in progress. Please wait until indexing is finished.", "Rest API Search")
            return
        }
        
        ApplicationManager.getApplication().runReadAction {
            try {
                val scanner = RestApiScanner(project)
                var items = scanner.scan()
                
                if (items.isEmpty()) {
                    logger.info("Standard scan returned 0 items. Trying fallback scan.")
                    items = scanner.scanFallback()
                }
                
                logger.info("Scanned ${items.size} items")
                
                ApplicationManager.getApplication().invokeLater {
                    RestApiSearchDialog(project, items).show()
                }
            } catch (ex: Exception) {
                logger.error("Error during REST API scan", ex)
                ApplicationManager.getApplication().invokeLater {
                    com.intellij.openapi.ui.Messages.showErrorDialog(project, "Error scanning for REST APIs: ${ex.message}", "Rest API Search Error")
                }
            }
        }
    }
}
