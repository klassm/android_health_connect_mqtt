package li.klass.walkingpad2hc.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordCipherTest {

    @Test
    fun `frame roundtrip preserves iv and ciphertext`() {
        val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val ciphertext = byteArrayOf(13, 14, 15, 16, 17, 18)
        val frame = PasswordCipher.encodeFrame(iv, ciphertext)
        val decoded = PasswordCipher.decodeFrame(frame)
        assertNotNull(decoded)
        assertEquals(iv.toList(), decoded!!.first.toList())
        assertEquals(ciphertext.toList(), decoded.second.toList())
    }

    @Test
    fun `non-prefixed stored value yields no frame`() {
        assertNull(PasswordCipher.decodeFrame("plainpassword"))
    }

    @Test
    fun `corrupt base64 yields no frame`() {
        assertNull(PasswordCipher.decodeFrame("enc1:!!!not-base64!!!"))
    }

    @Test
    fun `truncated frame yields no frame`() {
        assertNull(PasswordCipher.decodeFrame("enc1:" + java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))))
    }
}
