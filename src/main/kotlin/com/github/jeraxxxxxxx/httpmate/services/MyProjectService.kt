package com.github.jeraxxxxxxx.httpmate.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class HttpMateProjectService(private val project: Project) {

    // ========== 配置项 ==========
    
    /**
     * 文档输出目录,相对于项目根目录
     */
    var docOutputDir: String = "http-mate/docs"
    
    /**
     * 生成文档后是否自动打开
     */
    var autoOpenAfterGeneration: Boolean = false
    
    /**
     * 是否包含私有方法
     */
    var includePrivateMethods: Boolean = false
    
    // ========== 统计信息 ==========
    
    /**
     * 最后一次生成文档的时间戳
     */
    var lastGenerationTime: Long = 0
    
    /**
     * 总共生成的文档数量
     */
    var totalDocsGenerated: Int = 0
    
    /**
     * 已生成的文档文件列表
     */
    private val generatedFiles = mutableSetOf<String>()
    
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
     * 记录文档生成
     */
    fun recordGeneration(fileName: String) {
        totalDocsGenerated++
        lastGenerationTime = System.currentTimeMillis()
        generatedFiles.add(fileName)
        thisLogger().info("Document generated: $fileName (Total: $totalDocsGenerated)")
    }
    
    /**
     * 获取已生成的文档列表
     */
    fun getGeneratedFiles(): List<String> = generatedFiles.toList()
    
    /**
     * 清除历史记录
     */
    fun clearHistory() {
        totalDocsGenerated = 0
        lastGenerationTime = 0
        generatedFiles.clear()
        thisLogger().info("Generation history cleared")
    }
    
    /**
     * 获取上次生成距今的时间(毫秒)
     */
    fun getTimeSinceLastGeneration(): Long {
        return if (lastGenerationTime > 0) {
            System.currentTimeMillis() - lastGenerationTime
        } else {
            -1
        }
    }
    
    /**
     * 检查是否最近刚生成过文档(1分钟内)
     */
    fun isRecentlyGenerated(): Boolean {
        val timeSince = getTimeSinceLastGeneration()
        return timeSince in 0..60000 // 1分钟 = 60000毫秒
    }
}
