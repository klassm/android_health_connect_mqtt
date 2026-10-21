package li.klass.walkingpad2hc.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepMessageParserTest {

    @Test
    fun `parses int value from HA sensor payload`() {
        val payload = """{"speedKmh":5.2,"stepsTotal":1234}"""
        assertEquals(1234L, StepMessageParser.parse(payload, "stepsTotal"))
    }

    @Test
    fun `parses decimal value`() {
        assertEquals(1234L, StepMessageParser.parse("""{"stepsTotal":1234.0}""", "stepsTotal"))
        assertEquals(1235L, StepMessageParser.parse("""{"stepsTotal":1234.9}""", "stepsTotal"))
    }

    @Test
    fun `parses string value`() {
        assertEquals(1234L, StepMessageParser.parse("""{"stepsTotal":"1234"}""", "stepsTotal"))
    }

    @Test
    fun `missing key returns null`() {
        assertNull(StepMessageParser.parse("""{"speedKmh":5.2}""", "stepsTotal"))
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(StepMessageParser.parse("not json", "stepsTotal"))
    }

    @Test
    fun `non-numeric value returns null`() {
        assertNull(StepMessageParser.parse("""{"stepsTotal":"abc"}""", "stepsTotal"))
    }

    @Test
    fun `boolean value returns null`() {
        assertNull(StepMessageParser.parse("""{"stepsTotal":true}""", "stepsTotal"))
    }

    @Test
    fun `null value returns null`() {
        assertNull(StepMessageParser.parse("""{"stepsTotal":null}""", "stepsTotal"))
    }

    @Test
    fun `oversized payload returns null`() {
        val payload = """{"stepsTotal":1234,"pad":"""" + "a".repeat(10_001) + "\"}"
        assertNull(StepMessageParser.parse(payload, "stepsTotal"))
    }

    @Test
    fun `json array payload returns null`() {
        assertNull(StepMessageParser.parse("""[1,2,3]""", "stepsTotal"))
    }
}
