package com.github.jeraxxxxxxx.httpmate.generator

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal object JsonTemporalValues {
    private val legacyTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun generate(type: String, now: ZonedDateTime = ZonedDateTime.now()): String? {
        val value = when (type) {
            "java.time.LocalDate", "java.sql.Date" -> now.toLocalDate().toString()
            "java.time.LocalTime", "java.sql.Time" -> now.format(time)
            "java.time.LocalDateTime" -> now.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            "java.time.ZonedDateTime" -> now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
            "java.time.OffsetDateTime" -> now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            "java.time.OffsetTime" -> now.toOffsetDateTime().toOffsetTime().format(DateTimeFormatter.ISO_OFFSET_TIME)
            "java.time.Instant" -> now.toInstant().toString()
            "java.util.Date", "java.sql.Timestamp" -> now.format(legacyTimestamp)
            else -> return null
        }
        return "\"$value\""
    }
}
