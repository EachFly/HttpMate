package com.github.jeraxxxxxxx.httpmate.generator

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiField
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiType
import com.intellij.psi.util.TypeConversionUtil

/** Resolves both the object's type arguments and those of inherited fields. */
internal object JsonFieldResolver {
    data class Field(val declaration: PsiField, val type: PsiType) {
        val name: String get() = declaration.name
    }

    fun resolve(type: PsiType): List<Field> {
        val resolved = (type as? PsiClassType)?.resolveGenerics() ?: return emptyList()
        val psiClass = resolved.element ?: return emptyList()
        return psiClass.allFields.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }.map { field ->
            ProgressManager.checkCanceled()
            val owner = field.containingClass ?: psiClass
            val substitutor = TypeConversionUtil.getSuperClassSubstitutor(owner, psiClass, resolved.substitutor)
            val inheritedType = substitutor.substitute(field.type) ?: field.type
            Field(field, resolved.substitutor.substitute(inheritedType) ?: inheritedType)
        }
    }
}
