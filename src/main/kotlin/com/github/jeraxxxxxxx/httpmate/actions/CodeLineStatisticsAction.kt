package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.HttpMateBundle
import com.github.jeraxxxxxxx.httpmate.ui.CodeLineStatisticsDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiDirectory
import java.io.InputStreamReader

class CodeLineStatisticsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val target = resolveTarget(e)
        e.presentation.isEnabledAndVisible = target != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val target = resolveTarget(e) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            HttpMateBundle.message("code.stats.progress.title"),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = HttpMateBundle.message("code.stats.progress.collecting")

                val targetExtensions = setOf(
                    "java", "kt", "kts", "xml", "properties", "json",
                    "yaml", "yml", "sql", "html", "css", "js", "ts",
                    "py", "go", "rs", "c", "cpp", "h", "hpp",
                    "groovy", "gradle", "md", "txt"
                )
                val allFiles = mutableListOf<VirtualFile>()
                
                if (target.isDirectory) {
                    VfsUtilCore.visitChildrenRecursively(target, object : VirtualFileVisitor<Void>() {
                        override fun visitFile(file: VirtualFile): Boolean {
                            if (indicator.isCanceled) return false
                            if (!file.isDirectory) {
                                val ext = file.extension?.lowercase().orEmpty()
                                if (ext in targetExtensions) {
                                    allFiles.add(file)
                                }
                            }
                            return true
                        }
                    })
                } else {
                    val ext = target.extension?.lowercase().orEmpty()
                    if (ext in targetExtensions) {
                        allFiles.add(target)
                    }
                }

                if (allFiles.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            HttpMateBundle.message("code.stats.no.files"),
                            HttpMateBundle.message("dialog.info.title")
                        )
                    }
                    return
                }

                val fileStatsList = mutableListOf<FileStats>()
                val totalFiles = allFiles.size

                for ((index, file) in allFiles.withIndex()) {
                    if (indicator.isCanceled) return
                    indicator.fraction = (index + 1).toDouble() / totalFiles
                    indicator.text2 = file.name

                    try {
                        val content = ApplicationManager.getApplication().runReadAction<String> {
                            InputStreamReader(file.inputStream, file.charset).use { reader -> reader.readText() }
                        }
                        val ext = file.extension?.lowercase().orEmpty()
                        val stats = CodeLineAnalyzer.analyze(content, ext)
                        fileStatsList.add(
                            FileStats(
                                fileName = file.name,
                                relativePath = file.path,
                                extension = ext,
                                totalLines = stats.totalLines,
                                codeLines = stats.codeLines,
                                commentLines = stats.commentLines,
                                blankLines = stats.blankLines
                            )
                        )
                    } catch (e: ProcessCanceledException) {
                        throw e
                    } catch (_: Exception) {
                        // Skip unreadable files
                    }
                }

                ApplicationManager.getApplication().invokeLater {
                    val dialog = CodeLineStatisticsDialog(project, target.name, fileStatsList)
                    dialog.show()
                }
            }
        })
    }

    private fun resolveTarget(e: AnActionEvent): VirtualFile? {
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (psiElement is PsiDirectory) {
            return psiElement.virtualFile
        }
        return e.getData(CommonDataKeys.VIRTUAL_FILE)
    }

}

data class FileStats(
    val fileName: String,
    val relativePath: String,
    val extension: String,
    val totalLines: Int,
    val codeLines: Int,
    val commentLines: Int,
    val blankLines: Int
)
