package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.HttpMateBundle
import com.github.jeraxxxxxxx.httpmate.constants.RestAnnotations
import com.github.jeraxxxxxxx.httpmate.doc.DocGenerator
import com.github.jeraxxxxxxx.httpmate.services.HttpMateProjectService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.SmartPointerManager
import java.io.File

class GenerateDocAction : AnAction() {

    private val docGenerator = DocGenerator()

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val documentManager = PsiDocumentManager.getInstance(project)
        documentManager.commitAllDocuments()
        documentManager.performWhenAllCommitted {
            getPsiMethodFromContext(e)?.let {
                generateMethodDocAsync(project, SmartPointerManager.createPointer(it))
                return@performWhenAllCommitted
            }

            getPsiClassFromContext(e)?.let {
                generateClassDocAsync(project, SmartPointerManager.createPointer(it))
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val psiClass = getPsiClassFromContext(e)
        val psiMethod = getPsiMethodFromContext(e)
        e.presentation.isEnabledAndVisible = (psiClass != null || psiMethod != null)
    }

    private fun getPsiClassFromContext(e: AnActionEvent): PsiClass? {
        return ActionContextResolver.resolvePsiClass(e)
    }

    private fun getPsiMethodFromContext(e: AnActionEvent): PsiMethod? {
        return ActionContextResolver.resolvePsiMethod(e)
    }

    private fun generateMethodDocAsync(project: Project, psiMethodPointer: com.intellij.psi.SmartPsiElementPointer<PsiMethod>) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating API Doc...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                try {
                    val result = ReadAction.compute<Triple<String, String, String>?, Exception> {
                        val psiMethod = psiMethodPointer.element ?: return@compute null
                        val className = psiMethod.containingClass?.name ?: "Unknown"
                        val methodName = psiMethod.name
                        val fileName = "${className}_${methodName}"
                        Triple(fileName, "${project.getService(HttpMateProjectService::class.java).getDocOutputPath()}/$fileName.md", docGenerator.generate(psiMethod))
                    } ?: return

                    saveDocToFileAsync(project, result.first, result.third) {
                        val service = project.getService(HttpMateProjectService::class.java)
                        service.recordGeneration("${result.first}.md")
                        Messages.showInfoMessage(
                            project,
                            HttpMateBundle.message("doc.generate.success", result.second),
                            HttpMateBundle.message("dialog.success.title")
                        )
                    }
                } catch (e: Exception) {
                    showGenerationError(project, e)
                }
            }
        })
    }

    private fun generateClassDocAsync(project: Project, psiClassPointer: com.intellij.psi.SmartPsiElementPointer<PsiClass>) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating API Doc...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                try {
                    val result = ReadAction.compute<Triple<String, Int, String>?, Exception> {
                        val psiClass = psiClassPointer.element ?: return@compute null
                        val className = psiClass.name ?: "Unknown"
                        val built = buildClassDoc(psiClass) ?: return@compute null
                        Triple(built.first, built.second, className)
                    } ?: run {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showInfoMessage(
                                project,
                                HttpMateBundle.message("doc.generate.class.empty"),
                                HttpMateBundle.message("dialog.info.title")
                            )
                        }
                        return
                    }

                    val className = result.third
                    val service = project.getService(HttpMateProjectService::class.java)
                    val targetFile = File(service.getDocOutputPath(), "$className.md")

                    saveDocToFileAsync(project, className, result.first) {
                        service.recordGeneration("$className.md")
                        Messages.showInfoMessage(
                            project,
                            HttpMateBundle.message("doc.generate.class.success", result.second, className, targetFile.absolutePath),
                            HttpMateBundle.message("dialog.success.title")
                        )
                    }
                } catch (e: Exception) {
                    showGenerationError(project, e)
                }
            }
        })
    }

    private fun buildClassDoc(psiClass: PsiClass): Pair<String, Int>? {
        val className = psiClass.name ?: "Unknown"
        val methods = psiClass.methods.filter { method ->
            method.hasModifierProperty(PsiModifier.PUBLIC) && hasRestAnnotation(method)
        }

        if (methods.isEmpty()) {
            return null
        }

        val content = buildString {
            append("# $className 接口文档\n\n")

            val classMapping = psiClass.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
            if (classMapping != null) {
                val basePath = classMapping.findAttributeValue("value")?.text?.replace("\"", "") ?: ""
                append("**基础路径**: `$basePath`\n\n")
            }

            append("---\n\n")

            methods.forEachIndexed { index, method ->
                if (index > 0) {
                    append("\n---\n\n")
                }
                append(docGenerator.generate(method))
            }
        }

        return content to methods.size
    }

    private fun hasRestAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            RestAnnotations.matches(annotation)
        }
    }

    private fun saveDocToFileAsync(
        project: Project,
        className: String,
        content: String,
        onSuccess: (() -> Unit)? = null
    ) {
        val service = project.getService(HttpMateProjectService::class.java)
        val targetDir = File(service.getDocOutputPath())

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            HttpMateBundle.message("action.generate.error", "API documentation", "Failed to create directory: ${targetDir.absolutePath}"),
                            HttpMateBundle.message("dialog.error.title")
                        )
                    }
                    return@executeOnPooledThread
                }

                val targetFile = File(targetDir, "$className.md")
                targetFile.writeText(content)

                ApplicationManager.getApplication().invokeLater {
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)?.refresh(false, false)
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                showGenerationError(project, e)
            }
        }
    }

    private fun showGenerationError(project: Project, throwable: Throwable) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(
                project,
                HttpMateBundle.message("action.generate.error", "API documentation", throwable.message ?: "Unknown error"),
                HttpMateBundle.message("dialog.error.title")
            )
        }
    }
}
