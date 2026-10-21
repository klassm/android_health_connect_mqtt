package li.klass.walkingpad2hc.sync

import android.content.Context
import android.net.wifi.WifiManager

class WifiSsidSource(private val context: Context) {
    @Suppress("DEPRECATION")
    fun currentSsid(): String? = try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        WifiSsidScanner.normalizeSsid(wifiManager.connectionInfo?.ssid)
    } catch (_: Exception) {
        null
    }
}
