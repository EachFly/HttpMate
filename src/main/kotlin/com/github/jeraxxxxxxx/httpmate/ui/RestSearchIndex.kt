package com.github.jeraxxxxxxx.httpmate.ui

import java.util.PriorityQueue

/** Immutable search data, constructed and queried off the UI thread. */
internal class RestSearchIndex<T>(items: List<T>, path: (T) -> String) {
    private val entries = items.map { it to path(it).lowercase() }
    data class Result<T>(val items: List<T>, val total: Int)

    fun search(text: String, limit: Int, isCurrent: () -> Boolean = { true }): Result<T>? {
        require(limit > 0)
        val query = text.trim().lowercase()
        if (query.isEmpty()) {
            if (!isCurrent()) return null
            return Result(entries.take(limit).map { it.first }, entries.size)
        }
        // Worst retained match first; equal scores retain the original input order.
        val order = compareBy<Match> { it.score }.thenByDescending { it.index }
        val best = PriorityQueue(limit, order)
        var total = 0
        for ((index, entry) in entries.withIndex()) {
            if (!isCurrent()) return null
            val score = score(query, entry.second)
            if (score < 0) continue
            total++
            val match = Match(index, score)
            if (best.size < limit) best.add(match)
            else if (order.compare(match, best.peek()) > 0) {
                best.poll()
                best.add(match)
            }
        }
        if (!isCurrent()) return null
        return Result(best.sortedWith(order.reversed()).map { entries[it.index].first }, total)
    }

    private data class Match(val index: Int, val score: Int)

    private fun score(query: String, target: String): Int {
        if (query.isEmpty()) return 0
        if (target == query) return 100
        if (target.startsWith(query)) return 80
        val index = target.indexOf(query)
        if (index >= 0) return 70 - index.coerceAtMost(20)
        var matched = 0
        for (character in target) {
            if (character == query[matched]) matched++
            if (matched == query.length) return 20
        }
        return -1
    }
}
