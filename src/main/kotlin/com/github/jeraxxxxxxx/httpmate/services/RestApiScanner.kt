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
            "jakarta.ws.rs.Path"
        )

        val logger = com.intellij.openapi.diagnostic.Logger.getInstance(RestApiScanner::class.java)
        val processedMethods = mutableSetOf<com.intellij.psi.PsiMethod>()

        for (annotationName in mappingAnnotations) {
            val annotationClass = javaPsiFacade.findClass(annotationName, scope)
            if (annotationClass == null) {
                logger.debug("Annotation class not found: $annotationName")
                continue
            }
            
            val annotatedElements = AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope)
            
            for (method in annotatedElements) {
                if (!processedMethods.add(method)) continue // Avoid duplicates

                val annotation = method.getAnnotation(annotationName) ?: continue
                val methodPath = extractPath(annotation)
                val classPath = extractClassPath(method.containingClass)
                val fullPath = combinePaths(classPath, methodPath)
                
                val httpMethod = extractMethod(annotationName, annotation)
                
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
                return extractPath(annotation)
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

    private fun extractPath(annotation: PsiAnnotation): String {
        // Try to find 'value' or 'path' attribute
        val valueAttr = annotation.findAttributeValue("value") ?: annotation.findAttributeValue("path")
        var path = valueAttr?.text?.replace("\"", "") ?: ""
        
        // Handle array syntax like {"/api"} -> /api
        if (path.startsWith("{") && path.endsWith("}")) {
            path = path.substring(1, path.length - 1).trim()
        }
        return path
    }

    private fun extractMethod(annotationName: String, annotation: PsiAnnotation): String {
        return when (annotationName) {
            "org.springframework.web.bind.annotation.GetMapping" -> "GET"
            "org.springframework.web.bind.annotation.PostMapping" -> "POST"
            "org.springframework.web.bind.annotation.PutMapping" -> "PUT"
            "org.springframework.web.bind.annotation.DeleteMapping" -> "DELETE"
            "org.springframework.web.bind.annotation.PatchMapping" -> "PATCH"
            "org.springframework.web.bind.annotation.RequestMapping" -> {
                 val methodAttr = annotation.findAttributeValue("method")
                 // Handle arrays: {RequestMethod.GET, RequestMethod.POST} or just RequestMethod.GET
                 // Also handle static imports: GET
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
            "javax.ws.rs.Path", "jakarta.ws.rs.Path" -> "ALL"
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
        // This is a bit complex to do in one regex, so we'll do a second pass for the method if needed.
        val requestMappingRegex = Regex("@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|Path)\\s*\\(\\s*(?:[a-zA-Z0-9_]+\\s*=\\s*)?[\"']([^\"']+)[\"']")
        val classMappingRegex = Regex("@(RequestMapping|Path)\\s*\\(\\s*(?:[a-zA-Z0-9_]+\\s*=\\s*)?[\"']([^\"']+)[\"']")
        val methodAttributeRegex = Regex("method\\s*=\\s*(?:RequestMethod\\.)?([A-Z]+)")

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
                
                // If it's RequestMapping, try to find the method attribute in the full match text
                if (annotationType == "RequestMapping") {
                    // We need to look at the context of the match. 
                    // The regex above only captured the path. 
                    // Let's look at a larger window around the match or re-parse the annotation content.
                    // A simple approximation: look for "method =" in the vicinity of the match index.
                    // Better: extract the full annotation text.
                    
                    // Let's try to match the method attribute within the line or nearby lines? 
                    // Or just use a more comprehensive regex for RequestMapping specifically.
                    
                    // Quick fix: Check if the text immediately following the match contains "method ="
                    // This is tricky with regex.
                    
                    // Alternative: Parse the annotation content more robustly.
                    // Let's try to find "method = RequestMethod.XYZ" inside the parentheses of this annotation.
                    // We can find the closing parenthesis after the match.
                    
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
