package li.klass.walkingpad2hc.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> =
        context.dataStore.data.map { prefs ->
            AppSettings.fromPreferences(prefs).copy(password = PasswordCipher.decrypt(prefs[AppSettings.PASSWORD] ?: ""))
        }

    suspend fun save(settings: AppSettings): Boolean {
        val storedPassword = if (settings.password.isBlank()) ""
        else PasswordCipher.encrypt(settings.password) ?: return false
        context.dataStore.edit { prefs ->
            prefs[AppSettings.HOST] = settings.host
            prefs[AppSettings.PORT] = settings.port
            prefs[AppSettings.USERNAME] = settings.username
            prefs[AppSettings.PASSWORD] = storedPassword
            prefs[AppSettings.TOPIC] = settings.topic
            prefs[AppSettings.STEPS_KEY] = settings.stepsKey
            prefs[AppSettings.SSID] = settings.ssid
            prefs[AppSettings.TLS] = settings.tls
        }
        return true
    }

    suspend fun savePending(record: PendingRecord?) {
        context.dataStore.edit { prefs ->
            if (record == null) {
                prefs.remove(PENDING_START)
                prefs.remove(PENDING_END)
                prefs.remove(PENDING_COUNT)
                prefs.remove(PENDING_END_STEPS)
            } else {
                prefs[PENDING_START] = record.startTime
                prefs[PENDING_END] = record.endTime
                prefs[PENDING_COUNT] = record.count
                prefs[PENDING_END_STEPS] = record.endSteps
            }
        }
    }

    suspend fun loadPending(): PendingRecord? {
        val prefs = context.dataStore.data.first()
        val start = prefs[PENDING_START] ?: return null
        val end = prefs[PENDING_END] ?: return null
        val count = prefs[PENDING_COUNT] ?: return null
        val endSteps = prefs[PENDING_END_STEPS] ?: return null
        return PendingRecord(start, end, count, endSteps)
    }

    suspend fun saveLastWritten(endSteps: Long, endTime: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_WRITTEN_END_STEPS] = endSteps
            prefs[LAST_WRITTEN_END_TIME] = endTime
        }
    }

    suspend fun loadLastWritten(): LastWritten? {
        val prefs = context.dataStore.data.first()
        val endSteps = prefs[LAST_WRITTEN_END_STEPS] ?: return null
        val endTime = prefs[LAST_WRITTEN_END_TIME] ?: return null
        return LastWritten(endSteps, endTime)
    }

    companion object {
        val LAST_WRITTEN_END_STEPS = longPreferencesKey("lastWrittenEndSteps")
        val LAST_WRITTEN_END_TIME = longPreferencesKey("lastWrittenEndTime")
        val PENDING_START = longPreferencesKey("pendingStart")
        val PENDING_END = longPreferencesKey("pendingEnd")
        val PENDING_COUNT = longPreferencesKey("pendingCount")
        val PENDING_END_STEPS = longPreferencesKey("pendingEndSteps")
    }
}

data class PendingRecord(
    val startTime: Long,
    val endTime: Long,
    val count: Long,
    val endSteps: Long,
)

data class LastWritten(
    val endSteps: Long,
    val endTime: Long,
)
