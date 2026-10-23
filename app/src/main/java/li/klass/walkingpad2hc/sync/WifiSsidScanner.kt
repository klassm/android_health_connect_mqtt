package li.klass.walkingpad2hc.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

object WifiSsidScanner {
    private const val TAG = "MqttSync"

    fun normalizeSsid(raw: String?): String? =
        raw?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }

    @Suppress("DEPRECATION")
    fun startScan(context: Context): Boolean = try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.startScan()
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    fun latestResults(context: Context): List<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.scanResults
                .map { normalizeSsid(it.SSID) }
                .filterNotNull()
                .distinct()
                .sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun currentWifiState(context: Context): Pair<Boolean, String?> = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var isWifi = false
        var ssid: String? = null
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) continue
            isWifi = true
            if (ssid == null && Build.VERSION.SDK_INT >= 29) {
                val raw = (caps.transportInfo as? WifiInfo)?.ssid
                Log.d(TAG, "wifi caps: network=$network rawSsid=$raw transportInfo=${caps.transportInfo?.javaClass?.simpleName}")
                ssid = normalizeSsid(raw)
            }
        }
        if (ssid == null) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val raw = wifiManager.connectionInfo?.ssid
            Log.d(TAG, "connectionInfo fallback: rawSsid=$raw")
            ssid = normalizeSsid(raw)
        }
        Log.d(TAG, "currentWifiState: isWifi=$isWifi ssid=$ssid")
        isWifi to ssid
    } catch (e: Exception) {
        Log.w(TAG, "currentWifiState failed", e)
        false to null
    }

    fun isLocationEnabled(context: Context): Boolean = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (_: Exception) {
        false
    }
}
