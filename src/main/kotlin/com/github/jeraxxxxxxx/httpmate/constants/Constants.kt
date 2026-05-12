package com.github.jeraxxxxxxx.httpmate.constants

import com.intellij.psi.PsiAnnotation

/**
 * REST API 相关注解常量
 */
object RestAnnotations {

    // ========== Spring Boot 注解 ==========

    private val SPRING_MAPPINGS = listOf(
        "org.springframework.web.bind.annotation.RequestMapping",
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping"
    )

    // ========== JAX-RS 注解 (javax) ==========

    private val JAXRS_JAVAX = listOf(
        "javax.ws.rs.Path",
        "javax.ws.rs.GET",
        "javax.ws.rs.POST",
        "javax.ws.rs.PUT",
        "javax.ws.rs.DELETE",
        "javax.ws.rs.PATCH",
        "javax.ws.rs.HEAD",
        "javax.ws.rs.OPTIONS"
    )

    // ========== JAX-RS 注解 (jakarta) ==========

    private val JAXRS_JAKARTA = listOf(
        "jakarta.ws.rs.Path",
        "jakarta.ws.rs.GET",
        "jakarta.ws.rs.POST",
        "jakarta.ws.rs.PUT",
        "jakarta.ws.rs.DELETE",
        "jakarta.ws.rs.PATCH",
        "jakarta.ws.rs.HEAD",
        "jakarta.ws.rs.OPTIONS"
    )

    // ========== 所有注解 ==========

    val ALL = SPRING_MAPPINGS + JAXRS_JAVAX + JAXRS_JAKARTA

    private val SIMPLE_NAMES = ALL.map { it.substringAfterLast('.') }.toSet()

    // ========== 类级别路径注解 ==========

    val CLASS_PATH_ANNOTATIONS = listOf(
        "org.springframework.web.bind.annotation.RequestMapping",
        "javax.ws.rs.Path",
        "jakarta.ws.rs.Path"
    )

    fun matches(annotation: PsiAnnotation): Boolean {
        val qualifiedName = annotation.qualifiedName
        if (qualifiedName != null && qualifiedName in ALL) {
            return true
        }

        val shortName = annotation.nameReferenceElement?.referenceName
        return shortName != null && shortName in SIMPLE_NAMES
    }
}

/**
 * 应用程序常量
 */
object AppConstants {
    /** 搜索结果最大显示数量 */
    const val MAX_SEARCH_RESULTS = 50

    /** JSON 生成最大递归深度 */
    const val MAX_JSON_RECURSION_DEPTH = 5

    /** 嵌套类型最大深度 */
    const val MAX_NESTED_TYPE_DEPTH = 3

    /** 搜索防抖延迟 (毫秒) */
    const val SEARCH_DEBOUNCE_MS = 300
}
