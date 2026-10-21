package li.klass.walkingpad2hc.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkGateTest {

    private fun gate(hasPermission: Boolean = true, locationEnabled: Boolean = true) =
        NetworkGate(hasLocationPermission = { hasPermission }, isLocationEnabled = { locationEnabled })

    @Test
    fun `any wifi allowed when ssid blank`() {
        assertNull(gate().reason(isWifi = true, currentSsid = null, configuredSsid = ""))
    }

    @Test
    fun `no wifi never allowed`() {
        assertEquals("not on Wi-Fi", gate().reason(isWifi = false, currentSsid = null, configuredSsid = ""))
        assertEquals("not on Wi-Fi", gate().reason(isWifi = false, currentSsid = "MyWifi", configuredSsid = "MyWifi"))
    }

    @Test
    fun `matching ssid allowed case-insensitive`() {
        assertNull(gate().reason(isWifi = true, currentSsid = "MyWifi", configuredSsid = "mywifi"))
    }

    @Test
    fun `quoted current ssid matches configured`() {
        assertNull(gate().reason(isWifi = true, currentSsid = "\"MyWifi\"", configuredSsid = "MyWifi"))
    }

    @Test
    fun `different ssid blocked`() {
        assertEquals("different Wi-Fi network", gate().reason(isWifi = true, currentSsid = "OtherWifi", configuredSsid = "MyWifi"))
    }

    @Test
    fun `configured ssid with unknown current ssid blocked`() {
        assertEquals("Wi-Fi SSID unavailable", gate().reason(isWifi = true, currentSsid = null, configuredSsid = "MyWifi"))
    }

    @Test
    fun `reason permission missing without location permission`() {
        assertEquals(NetworkGate.REASON_LOCATION, gate(hasPermission = false).reason(isWifi = true, currentSsid = "MyWifi", configuredSsid = "MyWifi"))
    }

    @Test
    fun `reason location services off when disabled`() {
        assertEquals(NetworkGate.REASON_LOCATION_OFF, gate(locationEnabled = false).reason(isWifi = true, currentSsid = "MyWifi", configuredSsid = "MyWifi"))
    }
}
