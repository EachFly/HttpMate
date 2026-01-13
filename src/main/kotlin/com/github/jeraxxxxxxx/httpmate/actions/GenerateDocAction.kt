package com.github.jeraxxxxxxx.httpmate.actions

import com.github.jeraxxxxxxx.httpmate.constants.RestAnnotations
import com.github.jeraxxxxxxx.httpmate.doc.DocGenerator
import com.github.jeraxxxxxxx.httpmate.services.HttpMateProjectService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import java.io.File

/**
 * 生成 API 文档的 Action
 * 支持单个方法或整个类的文档生成
 */
class GenerateDocAction : AnAction() {

    private val docGenerator = DocGenerator()

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 尝试获取选中的类
        val psiClass = getPsiClassFromContext(e)
        if (psiClass != null) {
            // 如果选中的是类,生成该类所有方法的文档
            generateClassDoc(project, psiClass)
            return
        }
        
        // 否则尝试获取选中的方法
        val psiMethod = getPsiMethodFromContext(e)
        if (psiMethod != null) {
            // 如果选中的是方法,只生成该方法的文档
            val docContent = docGenerator.generate(psiMethod)
            val className = psiMethod.containingClass?.name ?: "Unknown"
            val methodName = psiMethod.name
            val fileName = "${className}_${methodName}"
            saveDocToFile(project, fileName, docContent)
            
            // 记录生成统计
            val service = project.getService(HttpMateProjectService::class.java)
            service.recordGeneration("$fileName.md")
        }
    }

    override fun update(e: AnActionEvent) {
        val psiClass = getPsiClassFromContext(e)
        val psiMethod = getPsiMethodFromContext(e)
        e.presentation.isEnabledAndVisible = (psiClass != null || psiMethod != null)
    }

    private fun getPsiClassFromContext(e: AnActionEvent): PsiClass? {
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (psiElement is PsiClass) return psiElement
        
        // 如果光标在类内部
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (editor != null && psiFile != null) {
            val elementAtCaret = psiFile.findElementAt(editor.caretModel.offset)
            var current = elementAtCaret
            while (current != null) {
                if (current is PsiClass) return current
                current = current.parent
            }
        }
        return null
    }

    private fun getPsiMethodFromContext(e: AnActionEvent): PsiMethod? {
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        if (psiElement is PsiMethod) return psiElement
        
        // 如果光标在方法内部
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        if (editor != null && psiFile != null) {
            val elementAtCaret = psiFile.findElementAt(editor.caretModel.offset)
            var current = elementAtCaret
            while (current != null) {
                if (current is PsiMethod) return current
                current = current.parent
            }
        }
        return null
    }

    private fun generateClassDoc(project: Project, psiClass: PsiClass) {
        val className = psiClass.name ?: "Unknown"
        val sb = StringBuilder()
        
        // 添加类级别的标题
        sb.append("# $className 接口文档\n\n")
        
        // 获取类上的 @RequestMapping 注解(如果有)
        val classMapping = psiClass.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
        if (classMapping != null) {
            val basePath = classMapping.findAttributeValue("value")?.text?.replace("\"", "") ?: ""
            sb.append("**基础路径**: `$basePath`\n\n")
        }
        
        sb.append("---\n\n")
        
        // 遍历所有公共方法
        val methods = psiClass.methods.filter { method ->
            // 只处理公共方法且带有 REST 注解的方法
            method.hasModifierProperty(PsiModifier.PUBLIC) && hasRestAnnotation(method)
        }
        
        if (methods.isEmpty()) {
            Messages.showInfoMessage(project, "该类中没有找到 REST API 方法", "提示")
            return
        }
        
        // 为每个方法生成文档
        methods.forEachIndexed { index, method ->
            if (index > 0) {
                sb.append("\n---\n\n")
            }
            sb.append(docGenerator.generate(method))
        }
        
        // 保存文档
        val service = project.getService(HttpMateProjectService::class.java)
        val targetFile = File(service.getDocOutputPath(), "$className.md")
        saveDocToFile(project, className, sb.toString(), showSuccessMessage = false)
        
        // 记录生成统计
        service.recordGeneration("$className.md")
        
        Messages.showInfoMessage(
            project, 
            "已为 $className 生成 ${methods.size} 个接口的文档\n文件位置: ${targetFile.absolutePath}", 
            "成功"
        )
    }

    private fun hasRestAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            RestAnnotations.ALL.contains(annotation.qualifiedName)
        }
    }

    private fun saveDocToFile(project: Project, className: String, content: String, showSuccessMessage: Boolean = true) {
        val service = project.getService(HttpMateProjectService::class.java)
        val targetDir = File(service.getDocOutputPath())
        
        if (!targetDir.exists()) {
            if (!targetDir.mkdirs()) {
                Messages.showErrorDialog(project, "Failed to create directory: ${targetDir.absolutePath}", "Error")
                return
            }
        }

        val targetFile = File(targetDir, "$className.md")
        
        try {
            targetFile.writeText(content)
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)?.refresh(false, false)
            if (showSuccessMessage) {
                Messages.showInfoMessage(project, "API Documentation generated at: ${targetFile.absolutePath}", "Success")
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to write file: ${e.message}", "Error")
        }
    }
}
