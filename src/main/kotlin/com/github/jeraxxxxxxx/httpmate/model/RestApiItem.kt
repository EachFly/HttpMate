package com.github.jeraxxxxxxx.httpmate.model

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import javax.swing.Icon

/**
 * REST API 项数据模型
 * 使用 SmartPsiElementPointer 避免持有 PsiElement 导致内存泄漏
 */
data class RestApiItem(
    val method: String,
    val path: String,
    val fileName: String,
    val navigationOffset: Int,
    val elementPointer: SmartPsiElementPointer<PsiElement>,
    val icon: Icon? = null
) {
    /** 获取关联的 PSI 元素，元素失效时返回 null */
    val element: PsiElement? get() = elementPointer.element
}
