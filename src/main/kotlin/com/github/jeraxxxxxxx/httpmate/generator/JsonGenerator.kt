package com.github.jeraxxxxxxx.httpmate.generator

import com.intellij.psi.PsiType

interface JsonGenerator {
    fun generate(type: PsiType, depth: Int): String
}
