package dev.kona.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.kona.core.Config
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM string encrypter (Laravel Encrypter shape) keyed by APP_KEY / `app.key`.
 *
 * Payload is base64(JSON `{iv,value,tag}`) where iv/value/tag are base64 fields.
 */
class Encrypter(
    key: ByteArray,
    private val mapper: ObjectMapper = ObjectMapper(),
) {
    private val keyBytes: ByteArray = normalizeKey(key)
    private val secret = SecretKeySpec(keyBytes, "AES")
    private val random = SecureRandom()

    /** Encrypt any JSON-serializable value (Laravel `encrypt($value)`). */
    fun encrypt(value: Any?): String = encryptString(mapper.writeValueAsString(value))

    /** Decrypt a payload previously produced by [encrypt] into [type]. */
    fun <T : Any> decrypt(payload: String, type: Class<T>): T =
        mapper.readValue(decryptString(payload), type)

    fun encryptString(value: String): String {
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val tagOffset = cipherText.size - TAG_LENGTH
        val encrypted = cipherText.copyOfRange(0, tagOffset)
        val tag = cipherText.copyOfRange(tagOffset, cipherText.size)
        val node = mapper.createObjectNode()
        node.put("iv", Base64.getEncoder().encodeToString(iv))
        node.put("value", Base64.getEncoder().encodeToString(encrypted))
        node.put("tag", Base64.getEncoder().encodeToString(tag))
        return Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(node))
    }

    fun decryptString(payload: String): String {
        try {
            val json = String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8)
            val node = mapper.readTree(json) as? ObjectNode
                ?: throw EncryptionException("The payload is invalid.")
            val ivText = textOrNull(node, "iv") ?: throw EncryptionException("The payload is invalid.")
            val valueText = textOrNull(node, "value") ?: throw EncryptionException("The payload is invalid.")
            val tagText = textOrNull(node, "tag") ?: throw EncryptionException("The payload is invalid.")
            val iv = Base64.getDecoder().decode(ivText)
            val value = Base64.getDecoder().decode(valueText)
            val tag = Base64.getDecoder().decode(tagText)
            val combined = value + tag
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, iv))
            val plain = cipher.doFinal(combined)
            return String(plain, StandardCharsets.UTF_8)
        } catch (ex: EncryptionException) {
            throw ex
        } catch (ex: GeneralSecurityException) {
            throw EncryptionException("Could not decrypt the data.", ex)
        } catch (ex: IllegalArgumentException) {
            throw EncryptionException("Could not decrypt the data.", ex)
        }
    }

    companion object {
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
        private const val TAG_LENGTH = TAG_BITS / 8
        private const val KEY_LENGTH = 32

        @JvmStatic
        fun fromBase64Key(key: String): Encrypter {
            val trimmed = key.removePrefix("base64:")
            val decoded = try {
                Base64.getDecoder().decode(trimmed)
            } catch (ex: IllegalArgumentException) {
                throw EncryptionException("Unsupported cipher or incorrect key length.", ex)
            }
            return Encrypter(decoded)
        }

        @JvmStatic
        fun fromConfig(config: Config): Encrypter {
            val key = config.getString("app.key")
            if (key == null) {
                throw EncryptionException("No application encryption key has been specified.")
            }
            return fromBase64Key(key)
        }

        @JvmStatic
        fun generateKey(): String {
            val bytes = ByteArray(KEY_LENGTH).also { SecureRandom().nextBytes(it) }
            return "base64:" + Base64.getEncoder().encodeToString(bytes)
        }

        private fun normalizeKey(key: ByteArray): ByteArray {
            if (key.size == KEY_LENGTH) {
                return key.copyOf()
            }
            throw EncryptionException("Unsupported cipher or incorrect key length.")
        }

        private fun textOrNull(node: ObjectNode, field: String): String? {
            if (!node.hasNonNull(field)) {
                return null
            }
            val text = node.get(field).asText()
            return text.ifEmpty { null }
        }
    }
}
