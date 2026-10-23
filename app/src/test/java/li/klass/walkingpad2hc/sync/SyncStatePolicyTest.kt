package li.klass.walkingpad2hc.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyncStatePolicyTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 23)
    private val yesterday: LocalDate = LocalDate.of(2026, 10, 22)

    @Test
    fun `pending is covered when lastWritten reached the same counter value`() {
        assertTrue(SyncStatePolicy.isCoveredByLastWritten(lastWrittenEndSteps = 3500, pendingEndSteps = 3500))
    }

    @Test
    fun `pending is covered when lastWritten went past the pending counter`() {
        assertTrue(SyncStatePolicy.isCoveredByLastWritten(lastWrittenEndSteps = 3550, pendingEndSteps = 3500))
    }

    @Test
    fun `pending is not covered when lastWritten is behind or missing`() {
        assertFalse(SyncStatePolicy.isCoveredByLastWritten(lastWrittenEndSteps = 3499, pendingEndSteps = 3500))
        assertFalse(SyncStatePolicy.isCoveredByLastWritten(lastWrittenEndSteps = null, pendingEndSteps = 0))
    }

    @Test
    fun `lastWritten persists when there is no current value`() {
        assertTrue(SyncStatePolicy.shouldPersistLastWritten(null, null, newEndSteps = 10, newDay = today))
    }

    @Test
    fun `lastWritten persists when it advances within the same day`() {
        assertTrue(SyncStatePolicy.shouldPersistLastWritten(1000, today, newEndSteps = 3500, newDay = today))
    }

    @Test
    fun `lastWritten never regresses within the same day`() {
        assertFalse(SyncStatePolicy.shouldPersistLastWritten(3500, today, newEndSteps = 1000, newDay = today))
    }

    @Test
    fun `lastWritten may restart on a new day`() {
        assertTrue(SyncStatePolicy.shouldPersistLastWritten(20000, yesterday, newEndSteps = 50, newDay = today))
    }
}
