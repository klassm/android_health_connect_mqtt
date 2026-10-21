package li.klass.walkingpad2hc.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSettingsValidationTest {

    @Test
    fun `valid host port and topic have no error`() {
        assertNull(AppSettings(host = "home.local", port = 1883, topic = "home/sensor").validationError())
    }

    @Test
    fun `port one and port max are valid`() {
        assertNull(AppSettings(host = "home.local", port = 1, topic = "home/sensor").validationError())
        assertNull(AppSettings(host = "home.local", port = 65535, topic = "home/sensor").validationError())
    }

    @Test
    fun `blank host reported`() {
        assertEquals("MQTT host is empty", AppSettings(host = "", port = 1883, topic = "home/sensor").validationError())
        assertEquals("MQTT host is empty", AppSettings(host = "   ", port = 1883, topic = "home/sensor").validationError())
    }

    @Test
    fun `port zero reported`() {
        val error = AppSettings(host = "home.local", port = 0, topic = "home/sensor").validationError()
        assertEquals("MQTT port must be between 1 and 65535", error)
    }

    @Test
    fun `port above range reported`() {
        assertEquals("MQTT port must be between 1 and 65535", AppSettings(host = "home.local", port = 65536, topic = "home/sensor").validationError())
    }

    @Test
    fun `negative port reported`() {
        assertEquals("MQTT port must be between 1 and 65535", AppSettings(host = "home.local", port = -1, topic = "home/sensor").validationError())
    }

    @Test
    fun `blank topic reported`() {
        assertEquals("MQTT topic is empty", AppSettings(host = "home.local", port = 1883, topic = "").validationError())
        assertEquals("MQTT topic is empty", AppSettings(host = "home.local", port = 1883, topic = "   ").validationError())
    }
}
