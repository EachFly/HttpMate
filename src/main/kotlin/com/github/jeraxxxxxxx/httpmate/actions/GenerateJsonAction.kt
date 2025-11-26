package com.github.jeraxxxxxxx.httpmate.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import java.io.File

class GenerateJsonAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = getPsiClassFromContext(e) ?: return

        val jsonString = generateJson(psiClass)
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

    private fun generateJson(psiClass: PsiClass): String {
        return generateJsonForType(JavaPsiFacade.getElementFactory(psiClass.project).createType(psiClass), 0)
    }

    private fun generateJsonForType(type: PsiType, depth: Int): String {
        if (depth > 5) return "{}" // Prevent infinite recursion

        if (type is PsiPrimitiveType) {
            return when (type) {
                PsiTypes.booleanType() -> "false"
                PsiTypes.byteType(), PsiTypes.shortType(), PsiTypes.intType(), PsiTypes.longType() -> "0"
                PsiTypes.floatType(), PsiTypes.doubleType() -> "0.0"
                else -> "null"
            }
        }

        val canonicalText = type.canonicalText
        // Handle String
        if (canonicalText == "java.lang.String") return "\"\""
        
        // Handle Boxed Primitives
        if (canonicalText == "java.lang.Integer" || canonicalText == "java.lang.Long" || 
            canonicalText == "java.lang.Short" || canonicalText == "java.lang.Byte") return "0"
        if (canonicalText == "java.lang.Double" || canonicalText == "java.lang.Float" ||
            canonicalText == "java.math.BigDecimal") return "0.0"
        if (canonicalText == "java.lang.Boolean") return "false"

        // Handle Date and Time
        if (canonicalText == "java.util.Date" || canonicalText == "java.sql.Date" || 
            canonicalText == "java.sql.Timestamp" || canonicalText == "java.time.LocalDateTime" ||
            canonicalText == "java.time.LocalDate" || canonicalText == "java.time.LocalTime" ||
            canonicalText == "java.time.ZonedDateTime") {
            val now = java.time.LocalDateTime.now()
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            return "\"${now.format(formatter)}\""
        }

        if (canonicalText.startsWith("java.util.List") || canonicalText.startsWith("java.util.Set") || type is PsiArrayType) return "[]"
        if (canonicalText.startsWith("java.util.Map")) return "{}"

        val psiClass = PsiTypesUtil.getPsiClass(type) ?: return "{}"
        if (psiClass.isEnum) {
            val fields = psiClass.fields.filterIsInstance<PsiEnumConstant>()
            return if (fields.isNotEmpty()) "\"${fields[0].name}\"" else "\"\""
        }

        val sb = StringBuilder()
        sb.append("{\n")
        
        val fields = psiClass.allFields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }
        val indent = "  ".repeat(depth + 1)
        
        fields.forEachIndexed { index, field ->
            sb.append(indent)
            sb.append("\"${field.name}\": ")
            sb.append(generateJsonForType(field.type, depth + 1))
            if (index < fields.size - 1) {
                sb.append(",")
            }
            sb.append("\n")
        }
        
        sb.append("  ".repeat(depth))
        sb.append("}")
        return sb.toString()
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
