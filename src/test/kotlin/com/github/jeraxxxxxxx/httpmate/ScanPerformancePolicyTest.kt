package com.github.jeraxxxxxxx.httpmate

import com.github.jeraxxxxxxx.httpmate.services.FallbackScanPolicy
import com.github.jeraxxxxxxx.httpmate.services.RestMappingResolver
import com.github.jeraxxxxxxx.httpmate.services.RouteBudget
import com.github.jeraxxxxxxx.httpmate.services.ScanLimitExceeded
import com.github.jeraxxxxxxx.httpmate.services.ScanResultLimit
import org.junit.Assert.*
import org.junit.Test

class ScanPerformancePolicyTest {
    @Test
    fun fallbackPrefilterPreservesAliasesQualifiedAnnotationsAndUnicodeEscapes() {
        assertTrue(FallbackScanPolicy.mightContainRoutes("import org.springframework.web.bind.annotation.GetMapping as Route\nclass C { @Route fun get() {} }"))
        assertTrue(FallbackScanPolicy.mightContainRoutes("class C { @jakarta.ws.rs.GET void get() {} }"))
        assertTrue(FallbackScanPolicy.mightContainRoutes("class C { \\u0040GetMapping void get() {} }"))
        assertFalse(FallbackScanPolicy.mightContainRoutes("class C { String get() { return \"GetMapping\"; } }"))
        assertFalse(FallbackScanPolicy.mightContainRoutes("@Deprecated class C {}"))
    }

    @Test
    fun operationAndResultLimitsAbortRatherThanTruncate() {
        val budget = RouteBudget(2)
        budget.step()
        budget.step()
        assertThrows(ScanLimitExceeded::class.java) { budget.step() }
        ScanResultLimit.checkSize(100_000)
        assertThrows(ScanLimitExceeded::class.java) { ScanResultLimit.checkSize(100_001) }
    }

    @Test
    fun largePathCartesianProductFailsExplicitly() {
        val mapping = RestMappingResolver.Mapping((1..400).map { "/$it" }, listOf("GET"), true)
        assertThrows(ScanLimitExceeded::class.java) {
            RestMappingResolver.fullPaths(mapping, (1..400).map { "/base$it" })
        }
        assertEquals(listOf("/api/users"), RestMappingResolver.fullPaths(
            RestMappingResolver.Mapping(listOf("/users", "/users"), listOf("GET"), true), listOf("/api", "/api")))
    }
}
