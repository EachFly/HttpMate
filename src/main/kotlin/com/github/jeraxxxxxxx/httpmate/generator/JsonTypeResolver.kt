package com.github.jeraxxxxxxx.httpmate.generator

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiCapturedWildcardType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil

/** Resolves container arguments through the type hierarchy, including concrete subclasses. */
internal object JsonTypeResolver {
    enum class Kind { SEQUENCE, MAP }
    data class Container(val kind: Kind, val arguments: List<PsiType?>)

    fun isType(type: PsiType, qualifiedName: String): Boolean =
        type is PsiClassType && (type.rawType().canonicalText == qualifiedName ||
            InheritanceUtil.isInheritor(type, qualifiedName))

    fun argument(type: PsiType, baseClass: String, index: Int): PsiType? {
        val substituted = PsiUtil.substituteTypeParameter(type, baseClass, index, false)
        val direct = (type as? PsiClassType)?.takeIf { it.rawType().canonicalText == baseClass }
            ?.parameters?.getOrNull(index)
        return readableType(substituted ?: direct)
    }

    fun container(type: PsiType): Container? {
        if (type is PsiArrayType) return Container(Kind.SEQUENCE, listOf(readableType(type.componentType)))
        if (isType(type, "java.util.Map")) {
            return Container(Kind.MAP, listOf(argument(type, "java.util.Map", 0), argument(type, "java.util.Map", 1)))
        }
        val base = listOf("java.util.Collection", "java.util.List", "java.util.Set")
            .firstOrNull { isType(type, it) } ?: return null
        return Container(Kind.SEQUENCE, listOf(argument(type, base, 0)))
    }

    private fun readableType(type: PsiType?): PsiType? = when (type) {
        is PsiCapturedWildcardType -> readableType(type.wildcard)
        is PsiWildcardType -> readableType(type.bound)
        else -> type
    }
}
