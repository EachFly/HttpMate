package com.github.jeraxxxxxxx.httpmate.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

object ActionContextResolver {

    fun resolvePsiClass(e: AnActionEvent): PsiClass? {
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)

        return findPsiClass(psiElement) ?: findPsiClass(editor?.caretModel?.offset?.let { psiFile?.findElementAt(it) })
        ?: findSingleTopLevelClass(psiFile)
    }

    fun resolvePsiMethod(e: AnActionEvent): PsiMethod? {
        val psiElement = e.getData(CommonDataKeys.PSI_ELEMENT)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)

        return findPsiMethod(psiElement)
            ?: findPsiMethod(editor?.caretModel?.offset?.let { psiFile?.findElementAt(it) })
    }

    internal fun findPsiClass(element: PsiElement?): PsiClass? {
        return PsiTreeUtil.getParentOfType(element, PsiClass::class.java, false) ?: (element as? PsiClass)
    }

    internal fun findPsiMethod(element: PsiElement?): PsiMethod? {
        return PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false) ?: (element as? PsiMethod)
    }

    private fun findSingleTopLevelClass(psiFile: PsiFile?): PsiClass? {
        val classOwner = psiFile as? PsiClassOwner ?: return null
        return classOwner.classes.singleOrNull()
    }
}
