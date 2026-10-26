package li.klass.walkingpad2hc.sync

/**
 * Plausibility gate for counter deltas on the in-session record path.
 *
 * The treadmill publishes a cumulative counter (`stepsTotal`) that can jump
 * by thousands within seconds — e.g. after a device reboot it resumes at the
 * accumulated total while the app's in-memory baseline still sits near zero
 * (the broker's retained "stepsTotal: 0" message sets that baseline). Such
 * jumps must never be trusted as walking activity; they are rerouted to the
 * baseline/catch-up reconciliation instead of being written blindly.
 *
 * The bound is a sustained human cadence: even fast jogging stays below
 * ~3.5 steps/second and message windows are far longer than a second, so
 * legitimate deltas never come close to the limit. Windows shorter than a
 * second are floored to one second so bursts of small deltas (queued
 * messages processed back-to-back) are not flagged.
 */
object DeltaRatePolicy {
    const val MAX_STEPS_PER_SECOND = 4.0

    /**
     * True if [count] steps over [durationMs] imply a cadence no human can
     * sustain — the signature of a counter jump rather than real walking.
     */
    fun isImplausible(count: Long, durationMs: Long): Boolean {
        if (count <= 0) return false
        val seconds = (durationMs / 1000.0).coerceAtLeast(1.0)
        return count / seconds > MAX_STEPS_PER_SECOND
    }
}
