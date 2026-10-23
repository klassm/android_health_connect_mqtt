package li.klass.walkingpad2hc.sync

import java.time.LocalDate

/**
 * Pure decision rules for the persisted sync state, extracted so the
 * anti-double-counting invariants stay unit-testable.
 */
object SyncStatePolicy {

    /**
     * A pending record is redundant when Health Connect already received
     * everything up to its end counter — e.g. because a catch-up write on an
     * earlier restart already covered the pending range, or because the
     * process died between the successful write and clearing the pending
     * marker. Re-writing it would duplicate the interval in Health Connect.
     */
    fun isCoveredByLastWritten(lastWrittenEndSteps: Long?, pendingEndSteps: Long): Boolean =
        lastWrittenEndSteps != null && lastWrittenEndSteps >= pendingEndSteps

    /**
     * The persisted lastWritten marker must never regress within a counter
     * epoch (same day): a regressed value makes the next restart's catch-up
     * write duplicate an already-written range. Crossing into a new day
     * starts a new epoch, so the value may restart from the day's counter.
     */
    fun shouldPersistLastWritten(
        currentEndSteps: Long?,
        currentDay: LocalDate?,
        newEndSteps: Long,
        newDay: LocalDate,
    ): Boolean =
        currentEndSteps == null || currentDay == null || newDay != currentDay || newEndSteps > currentEndSteps
}
