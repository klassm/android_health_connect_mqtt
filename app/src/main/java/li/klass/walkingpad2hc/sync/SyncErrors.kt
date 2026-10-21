package li.klass.walkingpad2hc.sync

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object SyncErrors {
    fun classify(t: Throwable): String {
        val message = t.message?.lowercase() ?: ""
        return when {
            t is SocketTimeoutException || "timed out" in message || "timeout" in message ->
                "Connection timed out — broker unreachable (check host/port and that phone and broker are on the same network)"
            t is ConnectException || "refused" in message ->
                "Connection refused — is the broker running and reachable at the configured host:port?"
            t is UnknownHostException || "unknownhostexception" in message || "unknowhost" in message ->
                "Unknown host — check the MQTT host name or IP address"
            "not authorized" in message || "notauthorised" in message || "not_auth" in message || "bad user name" in message ->
                "Broker rejected login — check username and password"
            t is SSLException || "ssl" in message || "not acceptable protocol" in message ->
                "TLS handshake failed — the broker may not support TLS on this port"
            "no route to host" in message || "unreachable" in message ->
                "No route to broker — check the Wi-Fi network"
            else -> "${t.javaClass.simpleName}: ${t.message ?: "unknown error"}"
        }
    }
}
