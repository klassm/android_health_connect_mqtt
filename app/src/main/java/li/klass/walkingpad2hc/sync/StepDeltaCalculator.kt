package li.klass.walkingpad2hc.sync

sealed interface DeltaResult {
    data object BaselineSet : DeltaResult
    data object Skipped : DeltaResult
    data class Record(val startTime: Long, val endTime: Long, val count: Long, val endSteps: Long) : DeltaResult
}

class StepDeltaCalculator {
    private var lastSteps: Long? = null
    private var lastTime: Long = 0L

    fun onMessage(steps: Long, nowMillis: Long): DeltaResult {
        val previous = lastSteps
        return when {
            previous == null || steps < previous -> {
                lastSteps = steps
                lastTime = nowMillis
                DeltaResult.BaselineSet
            }
            steps == previous -> DeltaResult.Skipped
            else -> {
                val endTime = nowMillis
                val startTime = if (lastTime >= endTime) endTime - 1 else lastTime
                lastSteps = steps
                lastTime = endTime
                DeltaResult.Record(startTime, endTime, steps - previous, steps)
            }
        }
    }

    fun revert(record: DeltaResult.Record) {
        lastSteps = record.endSteps - record.count
        lastTime = record.startTime
    }

    fun commit(record: DeltaResult.Record) {
        lastSteps = record.endSteps
        lastTime = record.endTime
    }

    fun currentLastSteps(): Long? = lastSteps

    fun reset() {
        lastSteps = null
        lastTime = 0L
    }
}
