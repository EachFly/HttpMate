package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.services.RestApiScanner
import com.github.jeraxxxxxxx.httpmate.ui.RestApiSearchDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager

class RestApiSearchAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        ApplicationManager.getApplication().runReadAction {
            val scanner = RestApiScanner(project)
            val items = scanner.scan()
            
            ApplicationManager.getApplication().invokeLater {
                RestApiSearchDialog(project, items).show()
            }
        }
    }
}
