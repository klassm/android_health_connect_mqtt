package li.klass.walkingpad2hc.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import li.klass.walkingpad2hc.settings.LastSeen
import li.klass.walkingpad2hc.settings.LastWritten

/**
 * Pure decision rules for the catch-up write performed when the first MQTT
 * message after a service start establishes the baseline.
 *
 * Invariant: Health Connect must never receive a step range twice. Where the
 * available state is ambiguous (e.g. the treadmill counter may or may not
 * have reset across a restart, or the persisted markers may lag behind
 * reality because the process died between a successful write and
 * persisting the marker), the policy prefers UNDER-counting over
 * duplicating: duplicates are permanent, while missed steps are covered by
 * subsequent delta records.
 *
 * The authoritative source for "what has already been posted" is the app's
 * own Health Connect records for today ([OwnStepsSummary]); the persisted
 * `lastWritten` / `lastSeen` markers are only hints.
 */
object CatchUpPolicy {

    sealed interface Decision {
        /** Nothing to do (e.g. zero steps). */
        data object None : Decision

        /** Health Connect already holds everything — align the marker, no write. */
        data object Covered : Decision

        /** Reconciled write: own records were read and the count is the verified gap. */
        data class Write(val start: Long, val end: Long, val count: Long) : Decision

        /**
         * A write is needed, but reconciliation failed (own records unreadable).
         * Carries the would-be record so the caller can park it as pending;
         * the retry path re-checks coverage before writing.
         */
        data class Defer(val start: Long, val end: Long, val count: Long) : Decision
    }

    data class Input(
        val steps: Long,
        val now: Long,
        val zone: ZoneId,
        val lastWritten: LastWritten?,
        val lastSeen: LastSeen?,
        val own: OwnStepsSummary?,
    )

    fun decide(input: Input): Decision {
        val steps = input.steps
        if (steps <= 0) return Decision.None
        val now = input.now
        val todayStart = HealthConnectWriter.startOfTodayMillis(now, input.zone)
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), input.zone)
        val lastWritten = input.lastWritten
        val lastWrittenDay = lastWritten?.let {
            LocalDate.ofInstant(Instant.ofEpochMilli(it.endTime), input.zone)
        }

        val decision = if (lastWritten != null && lastWrittenDay == today) {
            decideSameDay(steps, now, todayStart, lastWritten, input.own)
        } else {
            decideAcrossDays(steps, now, todayStart, lastWritten, input.lastSeen, input.own)
        }
        // Defense in depth: never write without a successful reconciliation
        // read — an unreadable own-record summary must not be treated as an
        // empty Health Connect.
        return if (decision is Decision.Write && input.own == null) {
            Decision.Defer(decision.start, decision.end, decision.count)
        } else {
            decision
        }
    }

    /**
     * Restart on the same day the marker was written. The marker may lag
     * reality, so the gap is computed against the higher of the marker and
     * the app's own records for today.
     */
    private fun decideSameDay(
        steps: Long,
        now: Long,
        todayStart: Long,
        lastWritten: LastWritten,
        own: OwnStepsSummary?,
    ): Decision {
        if (steps <= lastWritten.endSteps) return Decision.Covered
        val ownTotal = own?.total ?: return Decision.Defer(
            start = clampStart(lastWritten.endTime, todayStart, now),
            end = now,
            count = steps - lastWritten.endSteps,
        )
        val covered = maxOf(lastWritten.endSteps, ownTotal)
        if (steps <= covered) return Decision.Covered
        return Decision.Write(
            start = clampStart(lastWritten.endTime, todayStart, now),
            end = now,
            count = steps - covered,
        )
    }

    /**
     * Fresh install (no marker) or first sync of a new day. Distinguishes a
     * counter that kept counting across the day boundary from one that was
     * reset (treadmill power-cycled): if the incoming value exceeds every
     * counter value seen or written before, the counter continued and only
     * the excess over the highest known value is new; otherwise the counter
     * restarted from zero and everything it reports — minus what this app
     * already wrote today — is new.
     */
    private fun decideAcrossDays(
        steps: Long,
        now: Long,
        todayStart: Long,
        lastWritten: LastWritten?,
        lastSeen: LastSeen?,
        own: OwnStepsSummary?,
    ): Decision {
        val lastWrittenSteps = lastWritten?.endSteps
        val lastSeenSteps = lastSeen?.steps
        val knownHigh = maxOf(
            lastWrittenSteps ?: Long.MIN_VALUE,
            lastSeenSteps ?: Long.MIN_VALUE,
        )
        return if (steps >= knownHigh) {
            // Counter continued across the day boundary (or nothing was ever
            // seen before). Everything up to the highest known counter value
            // was already posted on a previous day. An incoming value exactly
            // equal to the highest known value means no new steps — never a
            // full re-post.
            val covered = maxOf(
                if (knownHigh == Long.MIN_VALUE) 0L else knownHigh,
                own?.total ?: 0L,
            )
            if (steps <= covered) {
                Decision.Covered
            } else {
                Decision.Write(
                    start = clampStart(own?.latestEndTime ?: todayStart, todayStart, now),
                    end = now,
                    count = steps - covered,
                )
            }
        } else {
            // Counter reset: today's epoch started at zero.
            val ownTotal = own?.total
            if (ownTotal != null && steps <= ownTotal) return Decision.Covered
            val start = clampStart(own?.latestEndTime ?: todayStart, todayStart, now)
            if (ownTotal == null) {
                Decision.Defer(start = start, end = now, count = steps)
            } else {
                Decision.Write(start = start, end = now, count = steps - ownTotal)
            }
        }
    }

    private fun clampStart(start: Long, todayStart: Long, now: Long): Long =
        minOf(maxOf(start, todayStart), now - 1)
}
