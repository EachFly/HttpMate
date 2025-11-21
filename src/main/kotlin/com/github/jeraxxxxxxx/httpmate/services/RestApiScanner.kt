package com.github.jeraxxxxxxx.httpmate.services

import com.github.jeraxxxxxxx.httpmate.model.RestApiItem
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
            "org.springframework.web.bind.annotation.PatchMapping"
        )

        for (annotationName in mappingAnnotations) {
            val annotationClass = javaPsiFacade.findClass(annotationName, scope) ?: continue
            val annotatedElements = AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope)
            
            for (method in annotatedElements) {
                val annotation = method.getAnnotation(annotationName) ?: continue
                val path = extractPath(annotation)
                val httpMethod = extractMethod(annotationName, annotation)
                
                items.add(RestApiItem(httpMethod, path, method))
            }
        }
        return items
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
                 // Example: RequestMethod.GET -> GET
                 methodAttr?.text?.substringAfterLast(".") ?: "ALL"
            }
            else -> "UNKNOWN"
        }
    }
}
