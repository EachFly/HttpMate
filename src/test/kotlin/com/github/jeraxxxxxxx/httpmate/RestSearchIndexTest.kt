package com.github.jeraxxxxxxx.httpmate

import com.github.jeraxxxxxxx.httpmate.ui.RestSearchIndex
import junit.framework.TestCase

class RestSearchIndexTest : TestCase() {
    fun testStaleSearchStopsWithoutPublishingResults() {
        val index = RestSearchIndex((0 until 10_000).map { "/users/$it" }) { it }
        var checks = 0
        assertNull(index.search("users", 50) { ++checks < 10 })
        assertEquals(10, checks)
    }

    fun testTopMatchesPreserveRankingAndInputOrderAndCountAllMatches() {
        var normalizations = 0
        val paths = (0 until 10_000).map { "/users/$it" } + listOf("USERS", "users/prefix", "/u/s/e/r/s", "/else")
        val index = RestSearchIndex(paths) { normalizations++; it }
        val result = index.search(" Users ", 50)!!
        assertEquals(10_003, result.total)
        assertEquals(listOf("USERS", "users/prefix") + (0 until 48).map { "/users/$it" }, result.items)
        assertEquals(paths.take(50), index.search("", 50)!!.items)
        assertEquals(paths.size, normalizations)
    }
}
