package com.github.jeraxxxxxxx.httpmate.generator

import com.github.jeraxxxxxxx.httpmate.constants.AppConstants
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.random.Random

/**
 * Mock JSON 生成器
 * 生成带有真实模拟数据的 JSON
 */
class MockJsonGenerator : JsonGenerator {
    private val random = Random.Default

    override fun generate(type: PsiType, depth: Int): String {
        if (depth > AppConstants.MAX_JSON_RECURSION_DEPTH) return "{}"

        if (type is PsiPrimitiveType) {
            return when (type) {
                PsiTypes.booleanType() -> random.nextBoolean().toString()
                PsiTypes.byteType(), PsiTypes.shortType(), PsiTypes.intType() -> random.nextInt(0, 100).toString()
                PsiTypes.longType() -> random.nextLong(0, 10000).toString()
                PsiTypes.floatType() -> String.format("%.2f", random.nextDouble(0.0, 100.0))
                PsiTypes.doubleType() -> String.format("%.2f", random.nextDouble(0.0, 100.0))
                else -> "null"
            }
        }

        val canonicalText = type.canonicalText
        // Handle String
        if (canonicalText == "java.lang.String") {
            val mockStrings = listOf("lorem", "ipsum", "dolor", "sit", "amet", "test", "mock", "data")
            return "\"${mockStrings.random()}_${random.nextInt(100)}\""
        }
        
        // Handle Boxed Primitives
        if (canonicalText == "java.lang.Integer" || canonicalText == "java.lang.Short" || canonicalText == "java.lang.Byte") return random.nextInt(0, 100).toString()
        if (canonicalText == "java.lang.Long") return random.nextLong(0, 10000).toString()
        if (canonicalText == "java.lang.Double" || canonicalText == "java.lang.Float" || canonicalText == "java.math.BigDecimal") return String.format("%.2f", random.nextDouble(0.0, 100.0))
        if (canonicalText == "java.lang.Boolean") return random.nextBoolean().toString()

        // Handle Date and Time
        if (canonicalText == "java.util.Date" || canonicalText == "java.sql.Date" || 
            canonicalText == "java.sql.Timestamp" || canonicalText == "java.time.LocalDateTime" ||
            canonicalText == "java.time.LocalDate" || canonicalText == "java.time.ZonedDateTime") {
            val now = LocalDateTime.now().minusDays(random.nextLong(0, 365))
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            return "\"${now.format(formatter)}\""
        }
        if (canonicalText == "java.time.LocalTime") {
             val now = LocalDateTime.now().minusHours(random.nextLong(0, 24))
             val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
             return "\"${now.format(formatter)}\""
        }

        // Handle Collections
        if (canonicalText.startsWith("java.util.List") || canonicalText.startsWith("java.util.Set") || type is PsiArrayType) {
            val elementType = if (type is PsiArrayType) type.componentType else (type as? PsiClassType)?.parameters?.firstOrNull()
            if (elementType != null) {
                val sb = StringBuilder()
                sb.append("[\n")
                val count = random.nextInt(1, 4) // 1 to 3 items
                val indent = "  ".repeat(depth + 1)
                for (i in 0 until count) {
                    sb.append(indent)
                    sb.append(generate(elementType, depth + 1))
                    if (i < count - 1) sb.append(",")
                    sb.append("\n")
                }
                sb.append("  ".repeat(depth))
                sb.append("]")
                return sb.toString()
            }
            return "[]"
        }
        
        if (canonicalText.startsWith("java.util.Map")) {
            // Simple map mock: {"key1": value, "key2": value}
             val keyType = (type as? PsiClassType)?.parameters?.getOrNull(0)
             val valueType = (type as? PsiClassType)?.parameters?.getOrNull(1)
             
             if (keyType != null && valueType != null && keyType.canonicalText == "java.lang.String") {
                 val sb = StringBuilder()
                 sb.append("{\n")
                 val count = random.nextInt(1, 3)
                 val indent = "  ".repeat(depth + 1)
                 for (i in 0 until count) {
                     sb.append(indent)
                     sb.append("\"key_${i + 1}\": ")
                     sb.append(generate(valueType, depth + 1))
                     if (i < count - 1) sb.append(",")
                     sb.append("\n")
                 }
                 sb.append("  ".repeat(depth))
                 sb.append("}")
                 return sb.toString()
             }
             return "{}"
        }

        val psiClass = PsiTypesUtil.getPsiClass(type) ?: return "{}"
        if (psiClass.isEnum) {
            val fields = psiClass.fields.filterIsInstance<PsiEnumConstant>()
            return if (fields.isNotEmpty()) "\"${fields.random().name}\"" else "\"\""
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
