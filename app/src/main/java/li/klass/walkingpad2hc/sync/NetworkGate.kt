package li.klass.walkingpad2hc.sync

class NetworkGate(
    private val hasLocationPermission: () -> Boolean,
    private val isLocationEnabled: () -> Boolean = { true },
) {
    fun reason(isWifi: Boolean, currentSsid: String?, configuredSsid: String): String? {
        if (!isWifi) return "not on Wi-Fi"
        val configured = configuredSsid.trim()
        if (configured.isEmpty()) return null
        if (!hasLocationPermission()) return REASON_LOCATION
        if (!isLocationEnabled()) return REASON_LOCATION_OFF
        val current = currentSsid?.removeSurrounding("\"") ?: return "Wi-Fi SSID unavailable"
        if (!current.equals(configured, ignoreCase = true)) return "different Wi-Fi network"
        return null
    }

    companion object {
        const val REASON_LOCATION = "location permission missing for SSID check"
        const val REASON_LOCATION_OFF = "Location services are off — needed for SSID check"
    }
}
