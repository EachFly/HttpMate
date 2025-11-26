package com.github.jeraxxxxxxx.httpmate.generator

import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil

class DefaultJsonGenerator : JsonGenerator {
    override fun generate(type: PsiType, depth: Int): String {
        if (depth > 5) return "{}" // Prevent infinite recursion

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
            canonicalText == "java.lang.Short" || canonicalText == "java.lang.Byte") return "0"
        if (canonicalText == "java.lang.Double" || canonicalText == "java.lang.Float" ||
            canonicalText == "java.math.BigDecimal") return "0.0"
        if (canonicalText == "java.lang.Boolean") return "false"

        // Handle Date and Time
        if (canonicalText == "java.util.Date" || canonicalText == "java.sql.Date" || 
            canonicalText == "java.sql.Timestamp" || canonicalText == "java.time.LocalDateTime" ||
            canonicalText == "java.time.LocalDate" || canonicalText == "java.time.LocalTime" ||
            canonicalText == "java.time.ZonedDateTime") {
            val now = java.time.LocalDateTime.now()
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            return "\"${now.format(formatter)}\""
        }

        if (canonicalText.startsWith("java.util.List") || canonicalText.startsWith("java.util.Set") || type is PsiArrayType) return "[]"
        if (canonicalText.startsWith("java.util.Map")) return "{}"

        val psiClass = PsiTypesUtil.getPsiClass(type) ?: return "{}"
        if (psiClass.isEnum) {
            val fields = psiClass.fields.filterIsInstance<PsiEnumConstant>()
            return if (fields.isNotEmpty()) "\"${fields[0].name}\"" else "\"\""
        }

        val sb = StringBuilder()
        sb.append("{\n")
        
        val fields = psiClass.allFields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }
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
