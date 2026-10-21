package li.klass.walkingpad2hc.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object PasswordCipher {
    private const val KEY_ALIAS = "walkingpad2hc"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc1:"
    private const val IV_LENGTH = 12

    fun encodeFrame(iv: ByteArray, ciphertext: ByteArray): String =
        PREFIX + Base64.getEncoder().encodeToString(iv + ciphertext)

    fun decodeFrame(stored: String): Pair<ByteArray, ByteArray>? {
        if (!stored.startsWith(PREFIX)) return null
        val data = try {
            Base64.getDecoder().decode(stored.removePrefix(PREFIX))
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (data.size <= IV_LENGTH) return null
        return data.copyOf(IV_LENGTH) to data.copyOfRange(IV_LENGTH, data.size)
    }

    fun encrypt(plain: String): String? = try {
        if (plain.isBlank()) ""
        else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            encodeFrame(cipher.iv, cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
        }
    } catch (_: Throwable) {
        null
    }

    fun decrypt(stored: String): String = try {
        val frame = decodeFrame(stored) ?: return stored
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, frame.first))
        String(cipher.doFinal(frame.second), Charsets.UTF_8)
    } catch (_: Throwable) {
        stored
    }

    fun isCorrupt(stored: String): Boolean =
        stored.startsWith(PREFIX) && decrypt(stored) == stored

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
