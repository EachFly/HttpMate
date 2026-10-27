package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.HttpMateBundle
import com.github.jeraxxxxxxx.httpmate.generator.JsonGenerator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import java.io.File

abstract class BaseGenerateJsonAction : AnAction() {

    abstract fun getGenerator(): JsonGenerator

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val documentManager = PsiDocumentManager.getInstance(project)
        documentManager.commitAllDocuments()
        documentManager.performWhenAllCommitted {
            if (project.isDisposed) return@performWhenAllCommitted
            val psiClass = ActionContextResolver.resolvePsiClass(e) ?: return@performWhenAllCommitted
            val classPointer = SmartPointerManager.createPointer(psiClass)

            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating JSON...", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    try {
                        val result =
                            ProjectBackgroundWork.read(project) {
                                val targetClass = classPointer.element ?: return@read null
                                val fileStem = GeneratedFileNames.forClass(targetClass)
                                val generator = getGenerator()
                                fileStem to generator.generate(
                                    JavaPsiFacade.getElementFactory(project).createType(targetClass), 0
                                )
                            } ?: return
                        saveJsonToFile(project, indicator, result.first, result.second)
                    } catch (ex: ProcessCanceledException) {
                        throw ex
                    } catch (ex: Exception) {
                        ProjectBackgroundWork.later(project, indicator) {
                            Messages.showErrorDialog(
                                project,
                                HttpMateBundle.message("action.generate.error", "JSON", ex.message ?: "Unknown error"),
                                HttpMateBundle.message("dialog.error.title")
                            )
                        }
                    }
                }
            })
        }
    }

    override fun update(e: AnActionEvent) {
        val psiClass = ActionContextResolver.resolvePsiClass(e)
        e.presentation.isEnabledAndVisible = psiClass != null
    }

    private fun saveJsonToFile(project: Project, indicator: ProgressIndicator, fileStem: String, jsonContent: String) {
        val projectBasePath = project.basePath ?: return
        val targetDir = File(projectBasePath, "http-mate")

        val targetFile = File(targetDir, "$fileStem.json")

        ProjectBackgroundWork.writeText(project, indicator, targetFile, jsonContent)
        ProjectBackgroundWork.later(project, indicator) {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)?.refresh(false, false)
            Messages.showInfoMessage(
                project,
                HttpMateBundle.message("json.generate.success", targetFile.absolutePath),
                HttpMateBundle.message("dialog.success.title")
            )
        }
    }
}
