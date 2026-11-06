package cl.iot.iotlink

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Utilidades criptográficas (ISO/IEC 27001 A.8.24, ISO/IEC 27400, IEC 62443 FR1/FR3/FR4). */
object Security {
    private const val ITER = 120_000
    private const val ALGO = "PBKDF2WithHmacSHA256"
    private val rnd = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(16).also { rnd.nextBytes(it) }

    /** Hash de contraseña con sal (nunca se guarda la contraseña en claro). */
    fun hashPassword(pw: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pw, salt, ITER, 256)
        try {
            return SecretKeyFactory.getInstance(ALGO).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)

    /** Deriva la clave AES-256 del enlace a partir de la clave compartida ingresada en ambos teléfonos. */
    fun linkKey(passphrase: String): SecretKeySpec {
        val salt = "TI3042-IoTLink-v1".toByteArray()
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITER, 256)
        val bytes = SecretKeyFactory.getInstance(ALGO).generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    /** AES-256-GCM: confidencialidad + integridad/autenticidad. Trama = Base64(IV || cifrado+tag). */
    fun encrypt(key: SecretKeySpec, plain: String): String {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(key: SecretKeySpec, frame: String): String? {
        return try {
            val raw = Base64.decode(frame, Base64.NO_WRAP)
            if (raw.size < 12 + 16) return null
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}

object Session {
    @Volatile var user: String? = null
    val loggedIn get() = user != null
    fun clear() { user = null }
}
