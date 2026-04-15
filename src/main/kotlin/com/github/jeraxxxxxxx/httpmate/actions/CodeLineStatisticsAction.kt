package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.HttpMateBundle
import com.github.jeraxxxxxxx.httpmate.ui.CodeLineStatisticsDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
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
        val directory = resolveDirectory(e)
        e.presentation.isEnabledAndVisible = directory != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val directory = resolveDirectory(e) ?: return

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
                VfsUtilCore.visitChildrenRecursively(directory, object : VirtualFileVisitor<Void>() {
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
                        val stats = analyzeFile(content, ext)
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
                    } catch (_: Exception) {
                        // Skip unreadable files
                    }
                }

                ApplicationManager.getApplication().invokeLater {
                    val dialog = CodeLineStatisticsDialog(project, directory.name, fileStatsList)
                    dialog.show()
                }
            }
        })
    }

    private fun resolveDirectory(e: AnActionEvent): VirtualFile? {
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (psiElement is PsiDirectory) {
            return psiElement.virtualFile
        }
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null && virtualFile.isDirectory) {
            return virtualFile
        }
        return null
    }

    private fun analyzeFile(content: String, extension: String): LineAnalysisResult {
        val lines = content.lines()
        val totalLines = lines.size
        var codeLines = 0
        var commentLines = 0
        var blankLines = 0
        var inBlockComment = false

        val supportsComments = extension in setOf(
            "java", "kt", "kts", "js", "ts", "c", "cpp", "h", "hpp",
            "go", "rs", "groovy", "gradle", "css"
        )

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                blankLines++
                continue
            }

            if (!supportsComments) {
                codeLines++
                continue
            }

            if (inBlockComment) {
                commentLines++
                val closeIdx = trimmed.indexOf("*/")
                if (closeIdx >= 0) {
                    inBlockComment = false
                    val afterClose = trimmed.substring(closeIdx + 2).trim()
                    if (afterClose.isNotEmpty() && !afterClose.startsWith("//")) {
                        codeLines++
                    }
                }
                continue
            }

            when {
                trimmed.startsWith("//") -> {
                    commentLines++
                }
                trimmed.startsWith("/*") -> {
                    commentLines++
                    if (!trimmed.contains("*/")) {
                        inBlockComment = true
                    }
                }
                trimmed.contains("/*") -> {
                    codeLines++
                    commentLines++
                    if (!trimmed.contains("*/")) {
                        inBlockComment = true
                    }
                }
                else -> {
                    codeLines++
                }
            }
        }

        return LineAnalysisResult(totalLines, codeLines, commentLines, blankLines)
    }

    data class LineAnalysisResult(
        val totalLines: Int,
        val codeLines: Int,
        val commentLines: Int,
        val blankLines: Int
    )
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
