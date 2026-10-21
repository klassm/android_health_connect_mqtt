package li.klass.walkingpad2hc.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SyncErrorsTest {

    @Test
    fun `connection refused is classified`() {
        val message = SyncErrors.classify(ConnectException("Connection refused"))
        assertTrue(message.contains("refused"))
        assertTrue(message.contains("broker running"))
    }

    @Test
    fun `timeout is classified`() {
        val message = SyncErrors.classify(SocketTimeoutException("connect timed out"))
        assertTrue(message.contains("timed out"))
        assertTrue(message.contains("unreachable"))
    }

    @Test
    fun `unknown host is classified`() {
        val message = SyncErrors.classify(UnknownHostException("Unable to resolve host"))
        assertTrue(message.contains("Unknown host"))
    }

    @Test
    fun `not authorized via message is classified as login rejected`() {
        val message = SyncErrors.classify(IllegalStateException("NOT_AUTHORIZED"))
        assertTrue(message.contains("login"))
    }

    @Test
    fun `unroutable message is classified`() {
        val message = SyncErrors.classify(RuntimeException("No route to host"))
        assertTrue(message.contains("No route"))
    }

    @Test
    fun `fallback includes exception name and message`() {
        val message = SyncErrors.classify(IllegalStateException("something odd"))
        assertEquals("IllegalStateException: something odd", message)
    }
}
