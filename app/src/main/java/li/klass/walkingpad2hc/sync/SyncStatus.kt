package li.klass.walkingpad2hc.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class SyncPhase {
    OFF,
    WAITING_FOR_NETWORK,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}

fun SyncPhase.displayText(detail: String?): String = when (this) {
    SyncPhase.OFF -> "Idle"
    SyncPhase.WAITING_FOR_NETWORK -> detail ?: "Waiting for network"
    SyncPhase.CONNECTING -> "Connecting…"
    SyncPhase.CONNECTED -> "Connected — syncing"
    SyncPhase.DISCONNECTED -> "Disconnected — retrying"
}

data class SyncStatus(
    val serviceRunning: Boolean = false,
    val phase: SyncPhase = SyncPhase.OFF,
    val phaseDetail: String? = null,
    val lastMessageAt: Long? = null,
    val mqttSteps: Long? = null,
    val lastError: String? = null,
    val errorAt: Long? = null,
)

object SyncStatusHolder {
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status

    fun update(transform: (SyncStatus) -> SyncStatus) {
        _status.update(transform)
    }

    fun setPhase(phase: SyncPhase, detail: String? = null) {
        update { it.copy(phase = phase, phaseDetail = detail) }
    }

    fun setError(message: String?) {
        update {
            it.copy(
                lastError = message,
                errorAt = if (message != null) System.currentTimeMillis() else null,
            )
        }
    }
}
