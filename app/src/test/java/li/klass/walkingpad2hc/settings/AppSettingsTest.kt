package li.klass.walkingpad2hc.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `defaults applied for empty preferences`() {
        val empty: Preferences = emptyPreferences()
        assertEquals(AppSettings(host = "", port = 1883, username = "", password = "", topic = "", stepsKey = "stepsTotal", ssid = ""), AppSettings.fromPreferences(empty))
    }

    @Test
    fun `values read from preferences`() {
        val prefs = preferencesOf(
            AppSettings.HOST to "192.168.1.69",
            AppSettings.PORT to 1884,
            AppSettings.USERNAME to "xx",
            AppSettings.PASSWORD to "secret",
            AppSettings.TOPIC to "homeassistant/sensor/walkingpad",
            AppSettings.STEPS_KEY to "stepsTotal",
            AppSettings.SSID to "HomeWifi",
        )
        assertEquals(AppSettings(host = "192.168.1.69", port = 1884, username = "xx", password = "secret", topic = "homeassistant/sensor/walkingpad", stepsKey = "stepsTotal", ssid = "HomeWifi"), AppSettings.fromPreferences(prefs))
    }

    @Test
    fun `partial preferences fall back to defaults per key`() {
        val prefs = preferencesOf(intPreferencesKey("port") to 1884)
        assertEquals(AppSettings(port = 1884), AppSettings.fromPreferences(prefs))
    }
}
