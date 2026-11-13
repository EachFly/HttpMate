package com.github.jeraxxxxxxx.httpmate.services

/** Abort explicitly instead of silently returning an incomplete route set. */
internal class ScanLimitExceeded(message: String) : RuntimeException(message)

internal object ScanResultLimit {
    fun checkSize(size: Long) {
        if (size > 100_000) throw ScanLimitExceeded(
            "Scan exceeded 100000 routes. Results are incomplete; narrow the project scope and retry."
        )
    }
}

internal class RouteBudget(private val maximum: Int = 100_000) {
    private var remaining = maximum
    fun step() {
        com.intellij.openapi.progress.ProgressManager.checkCanceled()
        if (--remaining < 0) throw ScanLimitExceeded(
            "Route expansion exceeded $maximum operations. Results are incomplete; narrow the route graph and retry."
        )
    }
}
