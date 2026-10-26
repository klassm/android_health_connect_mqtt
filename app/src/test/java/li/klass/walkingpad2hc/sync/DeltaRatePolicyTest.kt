package li.klass.walkingpad2hc.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeltaRatePolicyTest {

    @Test
    fun `treadmill reboot jump - 11295 steps in 32 seconds - implausible`() {
        assertTrue(DeltaRatePolicy.isImplausible(count = 11_295, durationMs = 32_716))
    }

    @Test
    fun `counter jump after idle - 10269 steps in 37 minutes - implausible`() {
        assertTrue(DeltaRatePolicy.isImplausible(count = 10_269, durationMs = 2_226_082))
    }

    @Test
    fun `real walking delta - 60 steps in 32 seconds - plausible`() {
        assertFalse(DeltaRatePolicy.isImplausible(count = 60, durationMs = 32_716))
    }

    @Test
    fun `sustained fast jogging - 100 steps in 30 seconds - plausible`() {
        assertFalse(DeltaRatePolicy.isImplausible(count = 100, durationMs = 30_000))
    }

    @Test
    fun `normal message delta - 50 steps in 30 seconds - plausible`() {
        assertFalse(DeltaRatePolicy.isImplausible(count = 50, durationMs = 30_000))
    }

    @Test
    fun `boundary - exactly max rate - plausible`() {
        assertFalse(DeltaRatePolicy.isImplausible(count = 400, durationMs = 100_000))
    }

    @Test
    fun `just above max rate - implausible`() {
        assertTrue(DeltaRatePolicy.isImplausible(count = 401, durationMs = 100_000))
    }

    @Test
    fun `tiny window is floored to one second - small burst deltas plausible`() {
        assertFalse(DeltaRatePolicy.isImplausible(count = 2, durationMs = 1))
        assertFalse(DeltaRatePolicy.isImplausible(count = 4, durationMs = 250))
    }

    @Test
    fun `tiny window with huge count - implausible`() {
        assertTrue(DeltaRatePolicy.isImplausible(count = 100, durationMs = 1))
    }

    @Test
    fun `zero or negative count - never implausible`() {
        assertFalse(DeltaRatePolicy.isImplausible(count = 0, durationMs = 1000))
        assertFalse(DeltaRatePolicy.isImplausible(count = -5, durationMs = 1000))
    }
}
