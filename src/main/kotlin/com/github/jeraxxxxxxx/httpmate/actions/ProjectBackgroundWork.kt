package com.github.jeraxxxxxxx.httpmate.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Background work and queued UI results must not outlive their project or cancellation. */
internal object ProjectBackgroundWork {
    fun checkActive(project: Project, indicator: ProgressIndicator) {
        indicator.checkCanceled()
        if (project.isDisposed) throw ProcessCanceledException()
    }

    fun <T> read(project: Project, action: () -> T): T =
        ReadAction.nonBlocking<T> { action() }.inSmartMode(project).expireWith(project).executeSynchronously()

    fun later(project: Project, indicator: ProgressIndicator, action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(
            { if (!project.isDisposed && !indicator.isCanceled) action() },
            ModalityState.nonModal(),
            Condition<Any?> { project.isDisposed || indicator.isCanceled }
        )
    }

    fun writeText(project: Project, indicator: ProgressIndicator, target: File, content: String) {
        checkActive(project, indicator)
        Files.createDirectories(target.parentFile.toPath())
        checkActive(project, indicator)
        val temporary = Files.createTempFile(target.parentFile.toPath(), ".httpmate-", ".tmp")
        try {
            Files.newBufferedWriter(temporary).use { writer ->
                var offset = 0
                while (offset < content.length) {
                    checkActive(project, indicator)
                    val length = minOf(8192, content.length - offset)
                    writer.write(content, offset, length)
                    offset += length
                }
            }
            checkActive(project, indicator)
            try {
                Files.move(temporary, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                checkActive(project, indicator)
                Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
