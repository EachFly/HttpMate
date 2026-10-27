package com.github.jeraxxxxxxx.httpmate

import com.github.jeraxxxxxxx.httpmate.generator.JsonTemporalValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZonedDateTime

class JsonTemporalValuesTest {
    private val now = ZonedDateTime.parse("2026-10-27T13:14:15+08:00[Asia/Shanghai]")

    @Test
    fun temporalFormatsPreserveTheInformationOfEachType() {
        val expected = mapOf(
            "java.time.LocalDate" to "2026-10-27",
            "java.sql.Date" to "2026-10-27",
            "java.time.LocalTime" to "13:14:15",
            "java.sql.Time" to "13:14:15",
            "java.time.LocalDateTime" to "2026-10-27T13:14:15",
            "java.time.ZonedDateTime" to "2026-10-27T13:14:15+08:00[Asia/Shanghai]",
            "java.time.OffsetDateTime" to "2026-10-27T13:14:15+08:00",
            "java.time.OffsetTime" to "13:14:15+08:00",
            "java.time.Instant" to "2026-10-27T05:14:15Z",
            "java.util.Date" to "2026-10-27 13:14:15",
            "java.sql.Timestamp" to "2026-10-27 13:14:15"
        )
        expected.forEach { (type, value) -> assertEquals(type, "\"$value\"", JsonTemporalValues.generate(type, now)) }
        assertNull(JsonTemporalValues.generate("java.lang.String", now))
    }
}
