package com.github.jeraxxxxxxx.httpmate.generator

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiClass
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
        val fields = linkedMapOf<String, Field>()
        val visited = mutableSetOf<PsiClass>()
        var owner: PsiClass? = psiClass
        while (owner != null && visited.add(owner)) {
            ProgressManager.checkCanceled()
            val substitutor = TypeConversionUtil.getSuperClassSubstitutor(owner, psiClass, resolved.substitutor)
            for (field in owner.fields) {
                ProgressManager.checkCanceled()
                if (field.hasModifierProperty(PsiModifier.STATIC) || field.name in fields) continue
                val inheritedType = substitutor.substitute(field.type) ?: field.type
                fields[field.name] = Field(field, resolved.substitutor.substitute(inheritedType) ?: inheritedType)
            }
            owner = owner.superClass
        }
        return fields.values.toList()
    }
}
