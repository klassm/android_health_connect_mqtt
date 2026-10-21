package li.klass.walkingpad2hc.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class HealthConnectWriterTest {

    @Test
    fun `start of today computed in given zone`() {
        val now = Instant.parse("2026-10-20T15:30:00Z").toEpochMilli()
        val start = HealthConnectWriter.startOfTodayMillis(now, ZoneId.of("UTC"))
        assertEquals(Instant.parse("2026-10-20T00:00:00Z").toEpochMilli(), start)
    }

    @Test
    fun `start of today crosses midnight for offset zone`() {
        val now = Instant.parse("2026-10-20T15:30:00Z").toEpochMilli()
        val start = HealthConnectWriter.startOfTodayMillis(now, ZoneId.of("Australia/Sydney"))
        assertEquals(Instant.parse("2026-10-20T13:00:00Z").toEpochMilli(), start)
    }
}
