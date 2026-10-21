package li.klass.walkingpad2hc.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class StepDeltaCalculatorTest {

    @Test
    fun `first message sets baseline without record`() {
        val calc = StepDeltaCalculator()
        assertEquals(DeltaResult.BaselineSet, calc.onMessage(100L, 1_000L))
        assertEquals(100L, calc.currentLastSteps())
    }

    @Test
    fun `delta produces record spanning previous message to now`() {
        val calc = StepDeltaCalculator()
        calc.onMessage(100L, 1_000L)
        assertEquals(DeltaResult.Record(1_000L, 2_500L, 50L, 150L), calc.onMessage(150L, 2_500L))
    }

    @Test
    fun `equal value is skipped`() {
        val calc = StepDeltaCalculator()
        calc.onMessage(100L, 1_000L)
        assertEquals(DeltaResult.Skipped, calc.onMessage(100L, 2_000L))
    }

    @Test
    fun `counter reset sets new baseline without record`() {
        val calc = StepDeltaCalculator()
        calc.onMessage(100L, 1_000L)
        assertEquals(DeltaResult.BaselineSet, calc.onMessage(50L, 3_000L))
        assertEquals(DeltaResult.Record(3_000L, 4_000L, 25L, 75L), calc.onMessage(75L, 4_000L))
    }

    @Test
    fun `rapid message with same timestamp guards non-decreasing interval`() {
        val calc = StepDeltaCalculator()
        calc.onMessage(100L, 1_000L)
        assertEquals(DeltaResult.Record(999L, 1_000L, 50L, 150L), calc.onMessage(150L, 1_000L))
    }

    @Test
    fun `revert restores state so next message recomputes accumulated delta`() {
        val calc = StepDeltaCalculator()
        calc.onMessage(100L, 1_000L)
        val record = calc.onMessage(150L, 2_500L) as DeltaResult.Record
        calc.revert(record)
        assertEquals(DeltaResult.Record(1_000L, 3_000L, 100L, 200L), calc.onMessage(200L, 3_000L))
    }

    @Test
    fun `reset clears state so next message becomes baseline`() {
        val calc = StepDeltaCalculator()
        calc.onMessage(100L, 1_000L)
        calc.reset()
        assertEquals(DeltaResult.BaselineSet, calc.onMessage(200L, 5_000L))
    }
}
