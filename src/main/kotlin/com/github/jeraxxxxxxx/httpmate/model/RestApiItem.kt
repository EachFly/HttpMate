package com.github.jeraxxxxxxx.httpmate.model

import com.intellij.psi.PsiElement
import javax.swing.Icon

data class RestApiItem(
    val method: String,
    val path: String,
    val element: PsiElement,
    val icon: Icon? = null
)
