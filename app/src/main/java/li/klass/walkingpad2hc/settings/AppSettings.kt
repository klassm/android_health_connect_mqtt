package li.klass.walkingpad2hc.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

data class AppSettings(
    val host: String = "",
    val port: Int = 1883,
    val username: String = "",
    val password: String = "",
    val topic: String = "",
    val stepsKey: String = "stepsTotal",
    val ssid: String = "",
    val tls: Boolean = false,
) {
    fun hostError(): String? = if (host.isBlank()) "MQTT host is empty" else null
    fun portError(): String? = if (port !in 1..65535) "MQTT port must be between 1 and 65535" else null
    fun topicError(): String? = if (topic.isBlank()) "MQTT topic is empty" else null

    fun validationError(): String? = hostError() ?: portError() ?: topicError()

    companion object {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val TOPIC = stringPreferencesKey("topic")
        val STEPS_KEY = stringPreferencesKey("stepsKey")
        val SSID = stringPreferencesKey("ssid")
        val TLS = booleanPreferencesKey("tls")

        fun fromPreferences(prefs: Preferences): AppSettings = AppSettings(
            host = prefs[HOST] ?: "",
            port = prefs[PORT] ?: 1883,
            username = prefs[USERNAME] ?: "",
            password = prefs[PASSWORD] ?: "",
            topic = prefs[TOPIC] ?: "",
            stepsKey = prefs[STEPS_KEY] ?: "stepsTotal",
            ssid = prefs[SSID] ?: "",
            tls = prefs[TLS] ?: false,
        )
    }
}
