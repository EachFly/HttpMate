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
            // 收集所有需要展开的自定义类型
            val nestedTypes = mutableSetOf<PsiClass>()
            
            // 主参数表格
            sb.append("| 参数名称 | 类型 | 必填 | 长度 | 说明 |\n")
            sb.append("| --- | --- | --- | --- | --- |\n")
            params.forEach { param ->
                val name = param.name
                val type = param.type
                val required = isRequired(param)
                val desc = extractComment(param)
                val length = extractLengthConstraint(param)
                
                sb.append("| $name | ${type.presentableText} | $required | $length | $desc |\n")
                
                // 检查是否是自定义类型,如果是则添加到待展开列表
                val psiClass = PsiTypesUtil.getPsiClass(type)
                if (psiClass != null && !psiClass.isEnum && !type.canonicalText.startsWith("java.") && !isSimpleType(type)) {
                    nestedTypes.add(psiClass)
                }
            }
            sb.append("\n")
            
            // 递归处理嵌套类型,最大深度3层
            val processedTypes = mutableSetOf<String>()
            var currentDepth = 0
            val maxDepth = 3
            
            while (nestedTypes.isNotEmpty() && currentDepth < maxDepth) {
                val typesToProcess = nestedTypes.toList()
                nestedTypes.clear()
                
                typesToProcess.forEach { psiClass ->
                    generateNestedTypeTable(psiClass, sb, processedTypes, nestedTypes)
                }
                
                currentDepth++
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
        var httpMethod = "GET"
        var path = ""

        // Class level path
        val classMapping = method.containingClass?.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
        if (classMapping != null) {
            path += classMapping.findAttributeValue("value")?.text?.replace("\"", "") ?: ""
        }
        
        val jaxrsClassPath = method.containingClass?.getAnnotation("javax.ws.rs.Path")
        if (jaxrsClassPath != null) {
            path += jaxrsClassPath.findAttributeValue("value")?.text?.replace("\"", "") ?: ""
        }
        
        val jakartaClassPath = method.containingClass?.getAnnotation("jakarta.ws.rs.Path")
        if (jakartaClassPath != null) {
            path += jakartaClassPath.findAttributeValue("value")?.text?.replace("\"", "") ?: ""
        }

        // Method level path
        for (annotation in method.annotations) {
            val name = annotation.qualifiedName ?: continue
            
            if (name.startsWith("org.springframework.web.bind.annotation")) {
                when {
                    name.endsWith("GetMapping") -> { httpMethod = "GET"; path += getPathValue(annotation); break }
                    name.endsWith("PostMapping") -> { httpMethod = "POST"; path += getPathValue(annotation); break }
                    name.endsWith("PutMapping") -> { httpMethod = "PUT"; path += getPathValue(annotation); break }
                    name.endsWith("DeleteMapping") -> { httpMethod = "DELETE"; path += getPathValue(annotation); break }
                    name.endsWith("RequestMapping") -> { httpMethod = getRequestMethod(annotation); path += getPathValue(annotation); break }
                }
            }
            
            if (name.startsWith("javax.ws.rs") || name.startsWith("jakarta.ws.rs")) {
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
        
        path = path.replace("//", "/")
        if (!path.startsWith("/")) path = "/$path"
        
        return Pair(httpMethod, path)
    }

    private fun getPathValue(annotation: PsiAnnotation) = 
        annotation.findAttributeValue("value")?.text?.replace("\"", "") 
            ?: annotation.findAttributeValue("path")?.text?.replace("\"", "") 
            ?: ""
    
    private fun getJaxRsPath(annotation: PsiAnnotation) = 
        annotation.findAttributeValue("value")?.text?.replace("\"", "") ?: ""

    private fun getRequestMethod(annotation: PsiAnnotation): String {
        val methodValue = annotation.findAttributeValue("method")?.text
        return when {
            methodValue?.contains("POST") == true -> "POST"
            methodValue?.contains("PUT") == true -> "PUT"
            methodValue?.contains("DELETE") == true -> "DELETE"
            else -> "GET"
        }
    }

    private fun isRequired(param: PsiParameter): String {
        val requestParam = param.getAnnotation("org.springframework.web.bind.annotation.RequestParam")
        return if (requestParam?.findAttributeValue("required")?.text == "false") "否" else "是"
    }

    private fun generateResponseTable(type: PsiType, sb: StringBuilder) {
        val psiClass = PsiTypesUtil.getPsiClass(type)
        if (psiClass != null && !psiClass.isEnum && !type.canonicalText.startsWith("java.")) {
             psiClass.allFields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }.forEach { field ->
                 sb.append("| ${field.name} | ${field.type.presentableText} | ${extractFieldComment(field)} |\n")
             }
        } else {
            sb.append("| result | ${type.presentableText} | |\n")
        }
    }

    private fun isSimpleType(type: PsiType) = when {
        type is PsiPrimitiveType -> true
        type.canonicalText in listOf(
            "java.lang.String", "java.lang.Integer", "java.lang.Long",
            "java.lang.Double", "java.lang.Float", "java.lang.Boolean",
            "java.lang.Byte", "java.lang.Short", "java.lang.Character"
        ) -> true
        type.canonicalText.startsWith("java.math.") -> true
        type.canonicalText.startsWith("java.time.") -> true
        type.canonicalText.startsWith("java.util.Date") -> true
        type.canonicalText.startsWith("java.sql.") -> true
        type.canonicalText.startsWith("java.util.List") -> true
        type.canonicalText.startsWith("java.util.Set") -> true
        type.canonicalText.startsWith("java.util.Map") -> true
        type.canonicalText.startsWith("java.util.Collection") -> true
        else -> false
    }

    private fun extractComment(param: PsiParameter): String {
        val method = param.declarationScope as? PsiMethod ?: return ""
        val docComment = method.docComment ?: return ""
        return docComment.findTagsByName("param").firstOrNull { 
            it.valueElement?.text == param.name 
        }?.dataElements?.joinToString(" ") { it.text }?.trim() ?: ""
    }

    private fun extractFieldComment(field: PsiField): String {
        field.docComment?.let {
            val desc = it.descriptionElements.joinToString(" ") { el -> el.text }
                .trim().replace("\n", " ").replace("\\s+".toRegex(), " ")
            if (desc.isNotEmpty()) return desc
        }
        
        field.children.find { it is PsiComment }?.let {
            return it.text.removePrefix("//").removePrefix("/*").removeSuffix("*/").trim()
        }
        
        return ""
    }

    private fun extractLengthConstraint(param: PsiParameter) = 
        extractLengthFromAnnotations(param.annotations)

    private fun extractFieldLengthConstraint(field: PsiField) = 
        extractLengthFromAnnotations(field.annotations)

    private fun extractLengthFromAnnotations(annotations: Array<PsiAnnotation>): String {
        val constraints = mutableListOf<String>()

        for (annotation in annotations) {
            when {
                annotation.qualifiedName?.endsWith(".Size") == true ||
                annotation.qualifiedName?.endsWith(".Length") == true -> {
                    val min = annotation.findAttributeValue("min")?.text ?: "0"
                    val max = annotation.findAttributeValue("max")?.text ?: ""
                    when {
                        max.isNotEmpty() && max != "2147483647" -> constraints.add("$min-$max")
                        min != "0" -> constraints.add("≥$min")
                    }
                }
                annotation.qualifiedName?.endsWith(".Min") == true -> {
                    annotation.findAttributeValue("value")?.text?.let { constraints.add("≥$it") }
                }
                annotation.qualifiedName?.endsWith(".Max") == true -> {
                    annotation.findAttributeValue("value")?.text?.let { constraints.add("≤$it") }
                }
                annotation.qualifiedName?.endsWith(".DecimalMin") == true -> {
                    annotation.findAttributeValue("value")?.text?.replace("\"", "")?.let { 
                        if (it.isNotEmpty()) constraints.add("≥$it") 
                    }
                }
                annotation.qualifiedName?.endsWith(".DecimalMax") == true -> {
                    annotation.findAttributeValue("value")?.text?.replace("\"", "")?.let { 
                        if (it.isNotEmpty()) constraints.add("≤$it") 
                    }
                }
                annotation.qualifiedName?.endsWith(".Pattern") == true -> {
                    annotation.findAttributeValue("regexp")?.text?.replace("\"", "")?.let { 
                        if (it.isNotEmpty() && it.length < 30) constraints.add("格式:$it") 
                    }
                }
            }
        }

        return constraints.joinToString(", ")
    }

    /**
     * 为嵌套类型生成独立表格
     */
    private fun generateNestedTypeTable(
        psiClass: PsiClass, 
        sb: StringBuilder, 
        processedTypes: MutableSet<String>,
        nestedTypes: MutableSet<PsiClass>
    ) {
        val className = psiClass.qualifiedName ?: psiClass.name ?: return
        
        // 避免重复处理同一类型
        if (processedTypes.contains(className)) return
        processedTypes.add(className)
        
        // 添加类型标题
        sb.append("**${psiClass.name}**\n\n")
        
        // 生成字段表格
        sb.append("| 字段名称 | 类型 | 必填 | 长度 | 说明 |\n")
        sb.append("| --- | --- | --- | --- | --- |\n")
        
        val fields = psiClass.allFields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }
        fields.forEach { field ->
            val fieldName = field.name
            val fieldType = field.type.presentableText
            val fieldRequired = "否"
            val fieldLength = extractFieldLengthConstraint(field)
            val fieldDesc = extractFieldComment(field)
            
            sb.append("| $fieldName | $fieldType | $fieldRequired | $fieldLength | $fieldDesc |\n")
            
            // 检查字段类型是否也是自定义类型
            val fieldPsiClass = PsiTypesUtil.getPsiClass(field.type)
            if (fieldPsiClass != null && 
                !fieldPsiClass.isEnum && 
                !field.type.canonicalText.startsWith("java.") && 
                !isSimpleType(field.type) &&
                !processedTypes.contains(fieldPsiClass.qualifiedName ?: fieldPsiClass.name ?: "")) {
                nestedTypes.add(fieldPsiClass)
            }
        }
        
        sb.append("\n")
    }
}
