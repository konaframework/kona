package org.konaframework.broadcasting

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** HMAC-SHA256 helpers for the Pusher Channels auth protocol. */
object PusherSigner {
    private const val ALGORITHM = "HmacSHA256"

    @JvmStatic
    fun sign(secret: String, payload: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM))
        return mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    @JvmStatic
    fun authString(key: String, secret: String, socketId: String, channel: String, channelData: String? = null): String {
        val toSign =
            if (channelData.isNullOrEmpty()) {
                "$socketId:$channel"
            } else {
                "$socketId:$channel:$channelData"
            }
        return "$key:${sign(secret, toSign)}"
    }

    @JvmStatic
    fun verify(
        key: String,
        secret: String,
        socketId: String,
        channel: String,
        auth: String?,
        channelData: String? = null,
    ): Boolean {
        if (auth.isNullOrBlank()) {
            return false
        }
        val expected = authString(key, secret, socketId, channel, channelData)
        return expected == auth
    }
}
