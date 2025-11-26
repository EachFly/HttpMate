package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.generator.JsonGenerator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import java.io.File

abstract class BaseGenerateJsonAction : AnAction() {

    abstract fun getGenerator(): JsonGenerator

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = getPsiClassFromContext(e) ?: return

        val generator = getGenerator()
        val jsonString = generator.generate(JavaPsiFacade.getElementFactory(project).createType(psiClass), 0)
        saveJsonToFile(project, psiClass.name ?: "Unknown", jsonString)
    }

    override fun update(e: AnActionEvent) {
        val psiClass = getPsiClassFromContext(e)
        e.presentation.isEnabledAndVisible = psiClass != null
    }

    private fun getPsiClassFromContext(e: AnActionEvent): PsiClass? {
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        val classFromElement = findParentClass(psiElement)
        if (classFromElement != null) return classFromElement
        
        // If cursor is inside a class
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (editor != null && psiFile != null) {
            val elementAtCaret = psiFile.findElementAt(editor.caretModel.offset)
            return findParentClass(elementAtCaret)
        }
        return null
    }

    private fun findParentClass(element: PsiElement?): PsiClass? {
        var current = element
        while (current != null) {
            if (current is PsiClass) return current
            current = current.parent
        }
        return null
    }

    private fun saveJsonToFile(project: Project, className: String, jsonContent: String) {
        val projectBasePath = project.basePath ?: return
        val targetDir = File(projectBasePath, "http-mate")
        
        if (!targetDir.exists()) {
            if (!targetDir.mkdirs()) {
                Messages.showErrorDialog(project, "Failed to create directory: ${targetDir.absolutePath}", "Error")
                return
            }
        }

        val targetFile = File(targetDir, "$className.json")
        
        try {
            targetFile.writeText(jsonContent)
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)?.refresh(false, false)
            Messages.showInfoMessage(project, "JSON file generated at: ${targetFile.absolutePath}", "Success")
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to write file: ${e.message}", "Error")
        }
    }
}
