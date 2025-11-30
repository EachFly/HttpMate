package com.github.jeraxxxxxxx.httpmate.doc

import com.github.jeraxxxxxxx.httpmate.generator.MockJsonGenerator
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil

class DocGenerator {
    private val mockJsonGenerator = MockJsonGenerator()

    fun generate(method: PsiMethod): String {
        val sb = StringBuilder()
        val className = method.containingClass?.name ?: "Unknown"
        val methodName = method.name

        sb.append("# $className - $methodName\n\n")

        // 1. Interface Info
        val (httpMethod, path) = getApiInfo(method)
        sb.append("## 接口信息\n\n")
        sb.append("| 属性 | 值 |\n")
        sb.append("| --- | --- |\n")
        sb.append("| 接口名称 | $methodName |\n")
        sb.append("| 请求方式 | $httpMethod |\n")
        sb.append("| 接口路径 | `$path` |\n\n")

        // 2. Request Parameters
        sb.append("## 请求参数\n\n")
        val params = method.parameterList.parameters
        if (params.isNotEmpty()) {
            sb.append("| 参数名称 | 类型 | 必填 | 说明 |\n")
            sb.append("| --- | --- | --- | --- |\n")
            params.forEach { param ->
                val name = param.name
                val type = param.type
                val required = isRequired(param)
                
                // 检查是否是自定义类型(非基本类型、非 Java 标准库类型)
                val psiClass = PsiTypesUtil.getPsiClass(type)
                if (psiClass != null && !psiClass.isEnum && !type.canonicalText.startsWith("java.") && !isSimpleType(type)) {
                    // 如果是自定义类型,展开其字段
                    val paramDesc = extractComment(param)
                    sb.append("| $name | ${type.presentableText} | $required | $paramDesc |\n")
                    val fields = psiClass.allFields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }
                    fields.forEach { field ->
                        val fieldName = "└─ ${field.name}"
                        val fieldType = field.type.presentableText
                        val fieldRequired = "否" // 字段的必填性需要根据注解判断
                        val fieldDesc = extractFieldComment(field)
                        sb.append("| $fieldName | $fieldType | $fieldRequired | $fieldDesc |\n")
                    }
                } else {
                    // 基本类型或 Java 标准类型,直接显示
                    val desc = extractComment(param)
                    sb.append("| $name | ${type.presentableText} | $required | $desc |\n")
                }
            }
        } else {
            sb.append("无请求参数\n")
        }
        sb.append("\n")

        // 3. Response Parameters
        sb.append("## 响应参数\n\n")
        val returnType = method.returnType
        if (returnType != null && returnType != PsiTypes.voidType()) {
            sb.append("| 参数名称 | 类型 | 说明 |\n")
            sb.append("| --- | --- | --- |\n")
            generateResponseTable(returnType, sb)
        } else {
            sb.append("无响应参数\n")
        }
        sb.append("\n")

        // 4. Examples
        sb.append("## 请求示例\n\n")
        val requestBodyParam = params.find { it.hasAnnotation("org.springframework.web.bind.annotation.RequestBody") }
        if (requestBodyParam != null) {
            sb.append("```json\n")
            sb.append(mockJsonGenerator.generate(requestBodyParam.type, 0))
            sb.append("\n```\n\n")
        } else {
            sb.append("无请求体\n\n")
        }

        sb.append("## 响应示例\n\n")
        if (returnType != null && returnType != PsiTypes.voidType()) {
            sb.append("```json\n")
            sb.append(mockJsonGenerator.generate(returnType, 0))
            sb.append("\n```\n")
        } else {
            sb.append("无响应体\n")
        }

        return sb.toString()
    }

    private fun getApiInfo(method: PsiMethod): Pair<String, String> {
        var httpMethod = "GET" // Default
        var path = ""

        // Class level path (Spring Boot)
        val classMapping = method.containingClass?.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
        if (classMapping != null) {
            val value = classMapping.findAttributeValue("value")?.text?.replace("\"", "") ?: ""
            path += value
        }
        
        // Class level path (JAX-RS javax)
        val jaxrsClassPath = method.containingClass?.getAnnotation("javax.ws.rs.Path")
        if (jaxrsClassPath != null) {
            val value = jaxrsClassPath.findAttributeValue("value")?.text?.replace("\"", "") ?: ""
            path += value
        }
        
        // Class level path (Jakarta JAX-RS)
        val jakartaClassPath = method.containingClass?.getAnnotation("jakarta.ws.rs.Path")
        if (jakartaClassPath != null) {
            val value = jakartaClassPath.findAttributeValue("value")?.text?.replace("\"", "") ?: ""
            path += value
        }

        // Method level path
        val annotations = method.annotations
        for (annotation in annotations) {
            val name = annotation.qualifiedName ?: continue
            
            // Spring Boot annotations
            if (name.startsWith("org.springframework.web.bind.annotation")) {
                if (name.endsWith("GetMapping")) { httpMethod = "GET"; path += getPathValue(annotation); break }
                if (name.endsWith("PostMapping")) { httpMethod = "POST"; path += getPathValue(annotation); break }
                if (name.endsWith("PutMapping")) { httpMethod = "PUT"; path += getPathValue(annotation); break }
                if (name.endsWith("DeleteMapping")) { httpMethod = "DELETE"; path += getPathValue(annotation); break }
                if (name.endsWith("RequestMapping")) {
                    httpMethod = getRequestMethod(annotation)
                    path += getPathValue(annotation)
                    break
                }
            }
            
            // JAX-RS (javax) annotations
            if (name.startsWith("javax.ws.rs")) {
                when {
                    name.endsWith(".GET") -> { httpMethod = "GET"; path += getJaxRsPath(annotation) }
                    name.endsWith(".POST") -> { httpMethod = "POST"; path += getJaxRsPath(annotation) }
                    name.endsWith(".PUT") -> { httpMethod = "PUT"; path += getJaxRsPath(annotation) }
                    name.endsWith(".DELETE") -> { httpMethod = "DELETE"; path += getJaxRsPath(annotation) }
                    name.endsWith(".PATCH") -> { httpMethod = "PATCH"; path += getJaxRsPath(annotation) }
                    name.endsWith(".Path") -> path += getJaxRsPath(annotation)
                }
            }
            
            // Jakarta JAX-RS annotations
            if (name.startsWith("jakarta.ws.rs")) {
                when {
                    name.endsWith(".GET") -> { httpMethod = "GET"; path += getJaxRsPath(annotation) }
                    name.endsWith(".POST") -> { httpMethod = "POST"; path += getJaxRsPath(annotation) }
                    name.endsWith(".PUT") -> { httpMethod = "PUT"; path += getJaxRsPath(annotation) }
                    name.endsWith(".DELETE") -> { httpMethod = "DELETE"; path += getJaxRsPath(annotation) }
                    name.endsWith(".PATCH") -> { httpMethod = "PATCH"; path += getJaxRsPath(annotation) }
                    name.endsWith(".Path") -> path += getJaxRsPath(annotation)
                }
            }
        }
        
        // Normalize path
        path = path.replace("//", "/")
        if (!path.startsWith("/")) path = "/$path"
        
        return Pair(httpMethod, path)
    }

    private fun getPathValue(annotation: PsiAnnotation): String {
        return annotation.findAttributeValue("value")?.text?.replace("\"", "") 
            ?: annotation.findAttributeValue("path")?.text?.replace("\"", "") 
            ?: ""
    }
    
    private fun getJaxRsPath(annotation: PsiAnnotation): String {
        return annotation.findAttributeValue("value")?.text?.replace("\"", "") ?: ""
    }

    private fun getRequestMethod(annotation: PsiAnnotation): String {
        val methodValue = annotation.findAttributeValue("method")?.text
        return if (methodValue != null && methodValue.contains("POST")) "POST"
        else if (methodValue != null && methodValue.contains("PUT")) "PUT"
        else if (methodValue != null && methodValue.contains("DELETE")) "DELETE"
        else "GET"
    }

    private fun isRequired(param: PsiParameter): String {
        val requestParam = param.getAnnotation("org.springframework.web.bind.annotation.RequestParam")
        if (requestParam != null) {
            val required = requestParam.findAttributeValue("required")?.text
            if (required == "false") return "否"
        }
        return "是"
    }

    private fun generateResponseTable(type: PsiType, sb: StringBuilder) {
        val psiClass = PsiTypesUtil.getPsiClass(type)
        if (psiClass != null && !psiClass.isEnum && !type.canonicalText.startsWith("java.")) {
             val fields = psiClass.allFields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }
             fields.forEach { field ->
                 val fieldDesc = extractFieldComment(field)
                 sb.append("| ${field.name} | ${field.type.presentableText} | $fieldDesc |\n")
             }
        } else {
            sb.append("| result | ${type.presentableText} | |\n")
        }
    }

    private fun isSimpleType(type: PsiType): Boolean {
        val canonicalText = type.canonicalText
        return when {
            // 基本类型
            type is PsiPrimitiveType -> true
            // 包装类型
            canonicalText in listOf(
                "java.lang.String",
                "java.lang.Integer",
                "java.lang.Long",
                "java.lang.Double",
                "java.lang.Float",
                "java.lang.Boolean",
                "java.lang.Byte",
                "java.lang.Short",
                "java.lang.Character"
            ) -> true
            // 常用类型
            canonicalText.startsWith("java.math.") -> true
            canonicalText.startsWith("java.time.") -> true
            canonicalText.startsWith("java.util.Date") -> true
            canonicalText.startsWith("java.sql.") -> true
            // 集合类型
            canonicalText.startsWith("java.util.List") -> true
            canonicalText.startsWith("java.util.Set") -> true
            canonicalText.startsWith("java.util.Map") -> true
            canonicalText.startsWith("java.util.Collection") -> true
            else -> false
        }
    }

    /**
     * 提取参数的注释
     */
    private fun extractComment(param: PsiParameter): String {
        // 尝试从方法的 JavaDoc 中提取参数说明
        val method = param.declarationScope as? PsiMethod
        if (method != null) {
            val docComment = method.docComment
            if (docComment != null) {
                val paramTags = docComment.findTagsByName("param")
                for (tag in paramTags) {
                    val valueElement = tag.valueElement
                    if (valueElement?.text == param.name) {
                        // 提取 @param 标签后的描述文本
                        val descElements = tag.dataElements
                        if (descElements.isNotEmpty()) {
                            return descElements.joinToString(" ") { it.text }.trim()
                        }
                    }
                }
            }
        }
        return ""
    }

    /**
     * 提取字段的注释
     */
    private fun extractFieldComment(field: PsiField): String {
        // 1. 尝试从 JavaDoc 注释提取
        val docComment = field.docComment
        if (docComment != null) {
            val description = docComment.descriptionElements
                .joinToString(" ") { it.text }
                .trim()
                .replace("\n", " ")
                .replace("\\s+".toRegex(), " ")
            if (description.isNotEmpty()) {
                return description
            }
        }

        // 2. 尝试从行内注释提取 (// 注释)
        val comment = field.children.find { it is PsiComment }
        if (comment != null) {
            return comment.text
                .removePrefix("//")
                .removePrefix("/*")
                .removeSuffix("*/")
                .trim()
        }

        return ""
    }
}
