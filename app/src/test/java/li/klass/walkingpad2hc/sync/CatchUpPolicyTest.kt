package li.klass.walkingpad2hc.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import li.klass.walkingpad2hc.settings.LastSeen
import li.klass.walkingpad2hc.settings.LastWritten

/**
 * Use-case matrix for the baseline/catch-up write decision. The invariant
 * under test: Health Connect never receives a step range twice — ambiguous
 * states must degrade to under-counting (or deferral), never duplication.
 */
class CatchUpPolicyTest {

    private val zone = ZoneId.of("UTC")
    private val todayStart = Instant.parse("2026-03-15T00:00:00Z").toEpochMilli()
    private val now = Instant.parse("2026-03-15T10:00:00Z").toEpochMilli()
    private val todayMorning = Instant.parse("2026-03-15T08:00:00Z").toEpochMilli()
    private val yesterdayEvening = Instant.parse("2026-03-14T20:00:00Z").toEpochMilli()

    private fun decide(
        steps: Long,
        lastWritten: LastWritten? = null,
        lastSeen: LastSeen? = null,
        own: OwnStepsSummary? = null,
    ): CatchUpPolicy.Decision = CatchUpPolicy.decide(
        CatchUpPolicy.Input(steps, now, zone, lastWritten, lastSeen, own)
    )

    // ---- zero steps ------------------------------------------------------

    @Test
    fun `zero steps never writes`() {
        assertEquals(CatchUpPolicy.Decision.None, decide(0L, LastWritten(5_000, yesterdayEvening)))
    }

    // ---- fresh install (no marker) ----------------------------------------

    @Test
    fun `fresh install with empty HC writes full counter from start of day`() {
        val own = OwnStepsSummary(0L, null)
        assertEquals(
            CatchUpPolicy.Decision.Write(todayStart, now, 4_500),
            decide(4_500, own = own),
        )
    }

    @Test
    fun `fresh install continues from latest own record to avoid duplicating it`() {
        val ownLatest = todayStart + 90 * 60_000L
        val own = OwnStepsSummary(100L, ownLatest)
        assertEquals(
            CatchUpPolicy.Decision.Write(ownLatest, now, 4_400),
            decide(4_500, own = own),
        )
    }

    @Test
    fun `fresh install skips write when HC already holds the counter`() {
        val own = OwnStepsSummary(4_500L, todayMorning)
        assertEquals(CatchUpPolicy.Decision.Covered, decide(4_500, own = own))
    }

    @Test
    fun `fresh install with unreadable HC summary defers instead of writing the full counter`() {
        // Regression: this used to write the full day counter again.
        assertEquals(
            CatchUpPolicy.Decision.Defer(todayStart, now, 4_500),
            decide(4_500, own = null),
        )
    }

    // ---- same-day restart --------------------------------------------------

    @Test
    fun `same-day restart writes only the gap after the marker`() {
        val lastWritten = LastWritten(5_000, todayMorning)
        val own = OwnStepsSummary(5_000, todayMorning)
        assertEquals(
            CatchUpPolicy.Decision.Write(todayMorning, now, 1_000),
            decide(6_000, lastWritten, own = own),
        )
    }

    @Test
    fun `same-day restart with lagging marker is capped by own HC records`() {
        // Regression: process died between a successful HC write and
        // persisting the marker — the extra 800 steps must not be re-written.
        val lastWritten = LastWritten(5_000, todayMorning)
        val own = OwnStepsSummary(5_800, todayMorning)
        assertEquals(
            CatchUpPolicy.Decision.Write(todayMorning, now, 200),
            decide(6_000, lastWritten, own = own),
        )
    }

    @Test
    fun `same-day restart is covered when own HC records already hold the counter`() {
        val lastWritten = LastWritten(5_000, todayMorning)
        val own = OwnStepsSummary(6_000, now - 60_000)
        assertEquals(CatchUpPolicy.Decision.Covered, decide(6_000, lastWritten, own = own))
    }

    @Test
    fun `same-day restart below the marker is covered even without own summary`() {
        val lastWritten = LastWritten(6_000, todayMorning)
        assertEquals(CatchUpPolicy.Decision.Covered, decide(5_500, lastWritten))
    }

    @Test
    fun `same-day restart with unreadable HC summary defers the gap`() {
        val lastWritten = LastWritten(5_000, todayMorning)
        assertEquals(
            CatchUpPolicy.Decision.Defer(todayMorning, now, 1_000),
            decide(6_000, lastWritten, own = null),
        )
    }

    @Test
    fun `same-day start is clamped to before now`() {
        val lastWritten = LastWritten(5_000, now + 60_000)
        val own = OwnStepsSummary(5_000, now + 60_000)
        val decision = decide(6_000, lastWritten, own = own) as CatchUpPolicy.Decision.Write
        assertEquals(now - 1, decision.start)
    }

    // ---- first sync of a new day -------------------------------------------

    @Test
    fun `new day with reset counter writes the full counter`() {
        val lastWritten = LastWritten(8_000, yesterdayEvening)
        val lastSeen = LastSeen(8_000, yesterdayEvening)
        val own = OwnStepsSummary(0L, null)
        assertEquals(
            CatchUpPolicy.Decision.Write(todayStart, now, 3_000),
            decide(3_000, lastWritten, lastSeen, own),
        )
    }

    @Test
    fun `new day with reset counter is covered when HC already holds it`() {
        val lastWritten = LastWritten(8_000, yesterdayEvening)
        val lastSeen = LastSeen(8_000, yesterdayEvening)
        val own = OwnStepsSummary(4_000, todayMorning)
        assertEquals(CatchUpPolicy.Decision.Covered, decide(3_000, lastWritten, lastSeen, own))
    }

    @Test
    fun `new day with reset counter and unreadable HC summary defers`() {
        val lastWritten = LastWritten(8_000, yesterdayEvening)
        val lastSeen = LastSeen(8_000, yesterdayEvening)
        assertEquals(
            CatchUpPolicy.Decision.Defer(todayStart, now, 3_000),
            decide(3_000, lastWritten, lastSeen, own = null),
        )
    }

    @Test
    fun `new day with counter that kept counting writes only the excess over the last known value`() {
        // Regression: this used to re-post the full counter including
        // everything already written yesterday.
        val lastWritten = LastWritten(8_000, yesterdayEvening)
        val lastSeen = LastSeen(8_000, yesterdayEvening)
        val own = OwnStepsSummary(0L, null)
        assertEquals(
            CatchUpPolicy.Decision.Write(todayStart, now, 600),
            decide(8_600, lastWritten, lastSeen, own),
        )
    }

    @Test
    fun `new day with counter at the last known value is covered`() {
        val lastWritten = LastWritten(8_000, yesterdayEvening)
        val lastSeen = LastSeen(8_000, yesterdayEvening)
        val own = OwnStepsSummary(0L, null)
        assertEquals(CatchUpPolicy.Decision.Covered, decide(8_000, lastWritten, lastSeen, own))
    }

    @Test
    fun `new day continuation is also capped by own HC records`() {
        val lastWritten = LastWritten(8_000, yesterdayEvening)
        val lastSeen = LastSeen(8_000, yesterdayEvening)
        val ownLatest = now - 120_000
        val own = OwnStepsSummary(8_500, ownLatest)
        assertEquals(
            CatchUpPolicy.Decision.Write(ownLatest, now, 100),
            decide(8_600, lastWritten, lastSeen, own),
        )
    }

    @Test
    fun `new day without lastSeen falls back to the marker for continuation`() {
        val lastWritten = LastWritten(8_000, yesterdayEvening)
        val own = OwnStepsSummary(0L, null)
        assertEquals(
            CatchUpPolicy.Decision.Write(todayStart, now, 1_000),
            decide(9_000, lastWritten, lastSeen = null, own = own),
        )
    }

    @Test
    fun `no state at all with unreadable HC summary defers`() {
        assertEquals(
            CatchUpPolicy.Decision.Defer(todayStart, now, 4_500),
            decide(4_500, lastWritten = null, lastSeen = null, own = null),
        )
    }
}
