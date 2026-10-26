package li.klass.walkingpad2hc.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceTypePolicyTest {

    @Test
    fun `health type requires API 34 or newer`() {
        assertFalse(ForegroundServiceTypePolicy.shouldUseHealthType(33, true))
        assertTrue(ForegroundServiceTypePolicy.shouldUseHealthType(34, true))
        assertTrue(ForegroundServiceTypePolicy.shouldUseHealthType(36, true))
    }

    @Test
    fun `health type requires a qualifying permission`() {
        assertFalse(ForegroundServiceTypePolicy.shouldUseHealthType(34, false))
        assertFalse(ForegroundServiceTypePolicy.shouldUseHealthType(37, false))
    }

    @Test
    fun `falls back to dataSync without qualification on any API`() {
        assertFalse(ForegroundServiceTypePolicy.shouldUseHealthType(28, false))
        assertFalse(ForegroundServiceTypePolicy.shouldUseHealthType(29, false))
        assertFalse(ForegroundServiceTypePolicy.shouldUseHealthType(34, false))
    }
}
