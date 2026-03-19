package com.github.jeraxxxxxxx.httpmate.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * HttpMate 项目级服务
 * 管理文档生成配置和统计信息
 * 所有统计操作均为线程安全
 */
@Service(Service.Level.PROJECT)
class HttpMateProjectService(private val project: Project) {

    // ========== 配置项 ==========
    
    /** 文档输出目录,相对于项目根目录 */
    var docOutputDir: String = "http-mate/docs"
    
    // ========== 统计信息 ==========
    
    /** 最后一次生成文档的时间戳 */
    @Volatile
    var lastGenerationTime: Long = 0
        private set
    
    /** 总共生成的文档数量 */
    private val _totalDocsGenerated = AtomicInteger(0)
    val totalDocsGenerated: Int get() = _totalDocsGenerated.get()
    
    /** 已生成的文档文件列表 */
    private val generatedFiles = ConcurrentHashMap.newKeySet<String>()
    
    // ========== 初始化 ==========
    
    init {
        thisLogger().info("HttpMate project service initialized for project: ${project.name}")
    }
    
    // ========== 工具方法 ==========
    
    /**
     * 获取文档输出的完整绝对路径
     */
    fun getDocOutputPath(): String {
        val basePath = project.basePath ?: ""
        return File(basePath, docOutputDir).absolutePath
    }
    
    /**
     * 记录文档生成（线程安全）
     */
    fun recordGeneration(fileName: String) {
        _totalDocsGenerated.incrementAndGet()
        lastGenerationTime = System.currentTimeMillis()
        generatedFiles.add(fileName)
        thisLogger().info("Document generated: $fileName (Total: ${_totalDocsGenerated.get()})")
    }
    
    /**
     * 获取已生成的文档列表
     */
    fun getGeneratedFiles(): List<String> = generatedFiles.toList()
}
