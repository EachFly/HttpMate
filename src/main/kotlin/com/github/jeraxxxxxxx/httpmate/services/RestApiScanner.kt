package com.github.jeraxxxxxxx.httpmate.services

import com.github.jeraxxxxxxx.httpmate.model.RestApiItem
import com.github.jeraxxxxxxx.httpmate.ui.RestApiIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

class RestApiScanner(private val project: Project) {
    fun scan(): List<RestApiItem> {
        val items = mutableListOf<RestApiItem>()
        val scope = GlobalSearchScope.projectScope(project)
        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        
        val mappingAnnotations = listOf(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "javax.ws.rs.Path",
            "jakarta.ws.rs.Path",
            "javax.ws.rs.GET", "jakarta.ws.rs.GET",
            "javax.ws.rs.POST", "jakarta.ws.rs.POST",
            "javax.ws.rs.PUT", "jakarta.ws.rs.PUT",
            "javax.ws.rs.DELETE", "jakarta.ws.rs.DELETE",
            "javax.ws.rs.PATCH", "jakarta.ws.rs.PATCH",
            "javax.ws.rs.HEAD", "jakarta.ws.rs.HEAD",
            "javax.ws.rs.OPTIONS", "jakarta.ws.rs.OPTIONS"
        )

        val logger = com.intellij.openapi.diagnostic.Logger.getInstance(RestApiScanner::class.java)
        val processedMethods = mutableSetOf<com.intellij.psi.PsiMethod>()

        for (annotationName in mappingAnnotations) {
            val annotationClass = javaPsiFacade.findClass(annotationName, scope)
            if (annotationClass == null) {
                continue
            }
            
            val annotatedElements = AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope)
            
            // Use findAll() instead of iterator() to avoid deprecation warning
            for (method in annotatedElements.findAll()) {
                if (!processedMethods.add(method)) continue // Avoid duplicates

                val annotation = method.getAnnotation(annotationName) ?: continue
                val methodPath = extractPath(method, annotation)
                val classPath = extractClassPath(method.containingClass)
                val fullPath = combinePaths(classPath, methodPath)
                
                val httpMethod = extractMethod(method, annotationName, annotation)
                
                items.add(RestApiItem(httpMethod, fullPath, method, RestApiIcons.getIcon(httpMethod)))
            }
        }
        return items
    }

    private fun extractClassPath(psiClass: com.intellij.psi.PsiClass?): String {
        if (psiClass == null) return ""
        
        val classAnnotations = listOf(
            "org.springframework.web.bind.annotation.RequestMapping",
            "javax.ws.rs.Path",
            "jakarta.ws.rs.Path"
        )

        for (annotationName in classAnnotations) {
            val annotation = psiClass.getAnnotation(annotationName)
            if (annotation != null) {
                // For class path, we just pass the annotation as is, context method is null (not needed for class path)
                return extractPathFromAnnotation(annotation)
            }
        }
        return ""
    }

    private fun combinePaths(classPath: String, methodPath: String): String {
        val p1 = if (classPath.startsWith("/")) classPath else "/$classPath"
        val p2 = if (methodPath.startsWith("/")) methodPath else "/$methodPath"
        
        if (p1 == "/" && p2 == "/") return "/"
        if (p1 == "/") return p2
        if (p2 == "/") return p1
        
        return p1 + p2
    }

    private fun extractPath(method: com.intellij.psi.PsiMethod, annotation: PsiAnnotation): String {
        // If it's a JAX-RS verb (GET, POST, etc.), it doesn't have a path. 
        // We need to look for @Path on the method.
        val qName = annotation.qualifiedName ?: ""
        if (isJaxRsVerb(qName)) {
            val pathAnnotation = method.getAnnotation("javax.ws.rs.Path") 
                ?: method.getAnnotation("jakarta.ws.rs.Path")
            return if (pathAnnotation != null) extractPathFromAnnotation(pathAnnotation) else ""
        }
        return extractPathFromAnnotation(annotation)
    }

    private fun extractPathFromAnnotation(annotation: PsiAnnotation): String {
        // Try to find 'value' or 'path' attribute
        val valueAttr = annotation.findAttributeValue("value") ?: annotation.findAttributeValue("path")
        var path = valueAttr?.text?.replace("\"", "") ?: ""
        
        // Handle array syntax like {"/api"} -> /api
        if (path.startsWith("{") && path.endsWith("}")) {
            path = path.substring(1, path.length - 1).trim()
        }
        return path
    }

    private fun isJaxRsVerb(qName: String): Boolean {
        return qName.endsWith(".GET") || qName.endsWith(".POST") || 
               qName.endsWith(".PUT") || qName.endsWith(".DELETE") || 
               qName.endsWith(".PATCH") || qName.endsWith(".HEAD") || 
               qName.endsWith(".OPTIONS")
    }

    private fun extractMethod(method: com.intellij.psi.PsiMethod, annotationName: String, annotation: PsiAnnotation): String {
        return when (annotationName) {
            "org.springframework.web.bind.annotation.GetMapping" -> "GET"
            "org.springframework.web.bind.annotation.PostMapping" -> "POST"
            "org.springframework.web.bind.annotation.PutMapping" -> "PUT"
            "org.springframework.web.bind.annotation.DeleteMapping" -> "DELETE"
            "org.springframework.web.bind.annotation.PatchMapping" -> "PATCH"
            "org.springframework.web.bind.annotation.RequestMapping" -> {
                 val methodAttr = annotation.findAttributeValue("method")
                 val text = methodAttr?.text?.replace("{", "")?.replace("}", "")?.trim() ?: ""
                 if (text.contains("RequestMethod.")) {
                     text.substringAfterLast("RequestMethod.").substringBefore(",")
                 } else if (text.isNotEmpty()) {
                     text.substringAfterLast(".").substringBefore(",")
                 } else {
                     "ALL"
                 }
            }
            "javax.ws.rs.GET", "jakarta.ws.rs.GET" -> "GET"
            "javax.ws.rs.POST", "jakarta.ws.rs.POST" -> "POST"
            "javax.ws.rs.PUT", "jakarta.ws.rs.PUT" -> "PUT"
            "javax.ws.rs.DELETE", "jakarta.ws.rs.DELETE" -> "DELETE"
            "javax.ws.rs.PATCH", "jakarta.ws.rs.PATCH" -> "PATCH"
            "javax.ws.rs.HEAD", "jakarta.ws.rs.HEAD" -> "HEAD"
            "javax.ws.rs.OPTIONS", "jakarta.ws.rs.OPTIONS" -> "OPTIONS"
            "javax.ws.rs.Path", "jakarta.ws.rs.Path" -> {
                // Check if the method has any verb annotation
                if (method.hasAnnotation("javax.ws.rs.GET") || method.hasAnnotation("jakarta.ws.rs.GET")) return "GET"
                if (method.hasAnnotation("javax.ws.rs.POST") || method.hasAnnotation("jakarta.ws.rs.POST")) return "POST"
                if (method.hasAnnotation("javax.ws.rs.PUT") || method.hasAnnotation("jakarta.ws.rs.PUT")) return "PUT"
                if (method.hasAnnotation("javax.ws.rs.DELETE") || method.hasAnnotation("jakarta.ws.rs.DELETE")) return "DELETE"
                if (method.hasAnnotation("javax.ws.rs.PATCH") || method.hasAnnotation("jakarta.ws.rs.PATCH")) return "PATCH"
                if (method.hasAnnotation("javax.ws.rs.HEAD") || method.hasAnnotation("jakarta.ws.rs.HEAD")) return "HEAD"
                if (method.hasAnnotation("javax.ws.rs.OPTIONS") || method.hasAnnotation("jakarta.ws.rs.OPTIONS")) return "OPTIONS"
                "ALL"
            }
            else -> "UNKNOWN"
        }
    }

    fun scanFallback(): List<RestApiItem> {
        val items = mutableListOf<RestApiItem>()
        val logger = com.intellij.openapi.diagnostic.Logger.getInstance(RestApiScanner::class.java)
        logger.info("Starting fallback text scan...")

        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = com.intellij.psi.PsiManager.getInstance(project)

        val javaFiles = com.intellij.psi.search.FilenameIndex.getAllFilesByExt(project, "java", scope)
        val ktFiles = com.intellij.psi.search.FilenameIndex.getAllFilesByExt(project, "kt", scope)
        val allFiles = javaFiles + ktFiles

        // Regex to capture annotation type, path, and optionally the method attribute
        val requestMappingRegex = Regex("@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|Path)\\s*\\(\\s*(?:[a-zA-Z0-9_]+\\s*=\\s*)?[\"']([^\"']+)[\"']")
        val classMappingRegex = Regex("@(RequestMapping|Path)\\s*\\(\\s*(?:[a-zA-Z0-9_]+\\s*=\\s*)?[\"']([^\"']+)[\"']")
        val methodAttributeRegex = Regex("method\\s*=\\s*(?:RequestMethod\\.)?([A-Z]+)")
        // Regex for JAX-RS verbs (simple names)
        val jaxRsVerbRegex = Regex("@(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)(?![a-zA-Z])")

        for (virtualFile in allFiles) {
            val file = psiManager.findFile(virtualFile) ?: continue
            val text = file.text
            // Simple heuristic: find class level mapping
            var classPath = ""
            val classMatch = classMappingRegex.find(text)
            if (classMatch != null) {
                classPath = classMatch.groupValues[2]
            }

            // Find method mappings
            val matches = requestMappingRegex.findAll(text)
            for (match in matches) {
                val annotationType = match.groupValues[1]
                val path = match.groupValues[2]
                
                // Skip if it looks like the class level mapping we just found (crude check)
                if (path == classPath && (annotationType == "RequestMapping" || annotationType == "Path")) continue

                val fullPath = combinePaths(classPath, path)
                var method = mapAnnotationToMethod(annotationType)
                
                // If it's RequestMapping, try to find the method attribute
                if (annotationType == "RequestMapping") {
                    val range = match.range
                    // Expand range to find closing ')'
                    var endIndex = range.last
                    var openCount = 1
                    while (endIndex < text.length && openCount > 0) {
                        endIndex++
                        if (endIndex < text.length) {
                            if (text[endIndex] == '(') openCount++
                            if (text[endIndex] == ')') openCount--
                        }
                    }
                    
                    if (endIndex < text.length) {
                        val annotationContent = text.substring(range.first, endIndex + 1)
                        val methodMatch = methodAttributeRegex.find(annotationContent)
                        if (methodMatch != null) {
                            method = methodMatch.groupValues[1]
                        }
                    }
                } else if (annotationType == "Path") {
                    // For JAX-RS @Path, look for @GET, @POST etc. in the vicinity
                    // We look 200 chars before and after the @Path match
                    val startSearch = (match.range.first - 200).coerceAtLeast(0)
                    val endSearch = (match.range.last + 200).coerceAtMost(text.length)
                    val vicinityText = text.substring(startSearch, endSearch)
                    
                    val verbMatch = jaxRsVerbRegex.find(vicinityText)
                    if (verbMatch != null) {
                        method = verbMatch.groupValues[1]
                    }
                }
                
                // Find the element offset to navigate to
                val offset = match.range.first
                val element = file.findElementAt(offset) ?: file
                
                items.add(RestApiItem(method, fullPath, element, RestApiIcons.getIcon(method)))
            }
        }
        logger.info("Fallback scan found ${items.size} items")
        return items
    }

    private fun mapAnnotationToMethod(annotation: String): String {
        return when (annotation) {
            "GetMapping" -> "GET"
            "PostMapping" -> "POST"
            "PutMapping" -> "PUT"
            "DeleteMapping" -> "DELETE"
            "PatchMapping" -> "PATCH"
            "RequestMapping", "Path" -> "ALL"
            else -> "UNKNOWN"
        }
    }
}
