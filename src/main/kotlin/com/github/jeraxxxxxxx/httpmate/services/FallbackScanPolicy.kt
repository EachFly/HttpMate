package com.github.jeraxxxxxxx.httpmate.services

import com.github.jeraxxxxxxx.httpmate.constants.RestAnnotations
import com.intellij.openapi.progress.ProgressManager

internal object FallbackScanPolicy {
    const val MAX_FILE_CHARS = 2_000_000
    private val names = RestAnnotations.ALL.map { it.substringAfterLast('.') }.toSet()

    // Includes import aliases: the original name still occurs in the import.
    // False positives are harmless; parsing remains authoritative.
    fun mightContainRoutes(text: CharSequence): Boolean {
        ProgressManager.checkCanceled()
        if (text.contains("\\u")) return true
        if (!text.contains('@')) return false
        return names.any { name ->
            ProgressManager.checkCanceled()
            text.contains(name)
        }
    }
}
