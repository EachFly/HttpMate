package com.github.jeraxxxxxxx.httpmate.actions

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object GeneratedFileNames {
    fun forClass(psiClass: PsiClass): String {
        val simpleName = psiClass.name ?: "Unknown"
        return withIdentitySuffix(simpleName, classIdentity(psiClass))
    }

    fun forMethod(psiMethod: PsiMethod): String {
        val containingClass = psiMethod.containingClass
        val className = containingClass?.name ?: "Unknown"
        val ownerIdentity = containingClass?.let(::classIdentity)
            ?: "${psiMethod.containingFile?.virtualFile?.path}:${psiMethod.textOffset}"
        val parameterTypes = psiMethod.parameterList.parameters.joinToString(",") { it.type.canonicalText }
        val identity = "$ownerIdentity#${psiMethod.name}($parameterTypes)"
        return withIdentitySuffix("${className}_${psiMethod.name}", identity)
    }

    private fun classIdentity(psiClass: PsiClass): String {
        return psiClass.qualifiedName
            ?: "${psiClass.containingFile?.virtualFile?.path}:${psiClass.textOffset}:${psiClass.name}"
    }

    private fun withIdentitySuffix(readableName: String, identity: String): String {
        val safeName = readableName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(StandardCharsets.UTF_8))
            .take(6)
            .joinToString("") { "%02x".format(it) }
        return "${safeName}_$digest"
    }
}
