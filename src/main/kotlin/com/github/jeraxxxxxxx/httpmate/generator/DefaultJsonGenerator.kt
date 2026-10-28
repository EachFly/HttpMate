package com.github.jeraxxxxxxx.httpmate.generator

import com.github.jeraxxxxxxx.httpmate.constants.AppConstants
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.PsiTypesUtil

class DefaultJsonGenerator : JsonGenerator {
    override fun generate(type: PsiType, depth: Int): String {
        ProgressManager.checkCanceled()

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
            canonicalText == "java.lang.Short" || canonicalText == "java.lang.Byte"
        ) return "0"
        if (canonicalText == "java.lang.Double" || canonicalText == "java.lang.Float" ||
            canonicalText == "java.math.BigDecimal"
        ) return "0.0"
        if (canonicalText == "java.lang.Boolean") return "false"

        // Handle Date and Time
        JsonTemporalValues.generate(canonicalText)?.let { return it }

        JsonTypeResolver.container(type)?.let {
            return if (it.kind == JsonTypeResolver.Kind.SEQUENCE) "[]" else "{}"
        }

        val psiClass = PsiTypesUtil.getPsiClass(type) ?: return "{}"
        if (psiClass.isEnum) {
            val fields = psiClass.fields.filterIsInstance<PsiEnumConstant>()
            return if (fields.isNotEmpty()) "\"${fields[0].name}\"" else "\"\""
        }

        // Limit only recursive object expansion, never scalar values or container shapes.
        if (depth > AppConstants.MAX_JSON_RECURSION_DEPTH) return "{}"

        val sb = StringBuilder()
        sb.append("{\n")

        val fields = JsonFieldResolver.resolve(type)
        val indent = "  ".repeat(depth + 1)

        fields.forEachIndexed { index, field ->
            sb.append(indent)
            sb.append("\"${field.name}\": ")
            sb.append(generate(field.type, depth + 1))
            if (index < fields.size - 1) {
                sb.append(",")
            }
            sb.append("\n")
        }

        sb.append("  ".repeat(depth))
        sb.append("}")
        return sb.toString()
    }
}
