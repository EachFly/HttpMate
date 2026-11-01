package com.github.jeraxxxxxxx.httpmate.doc

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.util.InheritanceUtil

internal object ValidationRules {
    private const val DEFAULT = "Default"
    private const val CONDITIONAL = "?"
    private val required = setOf("javax.validation.constraints", "jakarta.validation.constraints")
        .flatMap { namespace -> listOf("NotNull", "NotBlank", "NotEmpty").map { "$namespace.$it" } }.toSet() +
        setOf("org.hibernate.validator.constraints.NotBlank", "org.hibernate.validator.constraints.NotEmpty")

    fun groups(param: PsiParameter): Set<String> {
        val method = param.declarationScope as? com.intellij.psi.PsiMethod
        val validated = param.getAnnotation("org.springframework.validation.annotation.Validated")
        if (validated == null && (method?.hasAnnotation("org.springframework.validation.annotation.Validated") == true ||
                method?.containingClass?.hasAnnotation("org.springframework.validation.annotation.Validated") == true)) {
            // Executable validation and MVC argument validation can apply different groups.
            return setOf(CONDITIONAL)
        }
        val selected = classNames(validated?.findAttributeValue("value"))
        // A sequence can stop after an earlier group fails; don't claim unconditional requiredness.
        if (selected.any { name ->
            JavaPsiFacade.getInstance(param.project).findClass(name, param.resolveScope)?.annotations?.any {
                it.qualifiedName in setOf("javax.validation.GroupSequence", "jakarta.validation.GroupSequence")
            } == true
        }) return setOf(CONDITIONAL)
        return selected
    }

    private fun classNames(value: com.intellij.psi.PsiAnnotationMemberValue?): Set<String> {
        if (value == null) return setOf(DEFAULT)
        val values = (value as? PsiArrayInitializerMemberValue)?.initializers?.toList() ?: listOf(value)
        if (values.isEmpty()) return setOf(DEFAULT)
        return values.map { item ->
            (item as? PsiClassObjectAccessExpression)?.operand?.type?.canonicalText?.let {
                if (it == "jakarta.validation.groups.Default" || it == "javax.validation.groups.Default") DEFAULT else it
            } ?: CONDITIONAL
        }.toSet()
    }

    fun annotations(field: PsiField, owner: PsiClass?): List<PsiAnnotation> {
        val capitalized = field.name.replaceFirstChar { it.uppercase() }
        val names = mutableListOf("get$capitalized", "is$capitalized")
        if (field.name.startsWith("is") && field.name.getOrNull(2)?.isUpperCase() == true) names += field.name
        val getters = names.flatMap {
            (owner ?: field.containingClass)?.findMethodsByName(it, true)?.toList().orEmpty()
        }.filter {
            it.parameterList.parametersCount == 0 && !it.hasModifierProperty(PsiModifier.STATIC) &&
                (!it.name.startsWith("is") || it.returnType?.canonicalText in setOf("boolean", "java.lang.Boolean"))
        }
        return field.annotations.toList() + getters.flatMap { it.annotations.toList() }
    }

    fun cascadeGroups(annotations: List<PsiAnnotation>, selected: Set<String>): Set<String> =
        if (annotations.any { it.qualifiedName in GROUP_CONVERSIONS }) setOf(CONDITIONAL) else selected

    fun active(annotation: PsiAnnotation, selected: Set<String>): Boolean {
        if (CONDITIONAL in selected) return false
        val declared = classNames(annotation.findAttributeValue("groups"))
        return declared.any { group -> group in selected || selected.any { candidate ->
            val psiClass = JavaPsiFacade.getInstance(annotation.project).findClass(candidate, annotation.resolveScope)
            psiClass != null && InheritanceUtil.isInheritor(psiClass, group)
        } }
    }

    fun required(annotations: List<PsiAnnotation>, groups: Set<String>): String {
        val constraints = annotations.filter { it.qualifiedName in required }
        if (constraints.isEmpty()) return "否"
        if (CONDITIONAL in groups || constraints.any { CONDITIONAL in classNames(it.findAttributeValue("groups")) }) return "条件必填"
        return if (constraints.any { active(it, groups) }) "是" else "否"
    }

    fun defaultGroups(): Set<String> = setOf(DEFAULT)

    private val GROUP_CONVERSIONS = setOf("javax.validation.groups.ConvertGroup", "jakarta.validation.groups.ConvertGroup",
        "javax.validation.groups.ConvertGroup.List", "jakarta.validation.groups.ConvertGroup.List")
}
