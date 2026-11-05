package org.konaframework.broadcasting

import org.konaframework.auth.Auth
import org.konaframework.auth.Authenticatable
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.support.json.Json

/**
 * Builds Pusher-compatible `/broadcasting/auth` responses from [ChannelManager] callbacks.
 */
class BroadcastAuth(
    private val channels: ChannelManager,
    private val key: String,
    private val secret: String,
    private val userResolver: () -> Any? = { resolveUser() },
) {
    fun handle(request: Request): Response {
        val socketId = request.input("socket_id")?.trim().orEmpty()
        val channelName = request.input("channel_name")?.trim().orEmpty()
        if (socketId.isEmpty() || channelName.isEmpty()) {
            return Response.json(mapOf("message" to "socket_id and channel_name are required"), 422)
        }
        val user = userResolver()
        val result = channels.authorize(channelName, user)
        if (result == false || result == null) {
            return Response.text("Forbidden", 403)
        }
        if (channelName.startsWith("presence-")) {
            val channelData = presenceChannelData(user, result)
            val auth = PusherSigner.authString(key, secret, socketId, channelName, channelData)
            return Response.json(mapOf("auth" to auth, "channel_data" to channelData))
        }
        val auth = PusherSigner.authString(key, secret, socketId, channelName)
        return Response.json(mapOf("auth" to auth))
    }

    private fun presenceChannelData(user: Any?, result: Any): String {
        if (result is Map<*, *>) {
            return Json.stringify(result)
        }
        if (result is Authenticatable) {
            return Json.stringify(mapOf("user_id" to result.getAuthIdentifier().toString(), "user_info" to emptyMap<String, Any?>()))
        }
        if (user is Authenticatable) {
            return Json.stringify(
                mapOf(
                    "user_id" to user.getAuthIdentifier().toString(),
                    "user_info" to emptyMap<String, Any?>(),
                ),
            )
        }
        return Json.stringify(mapOf("user_id" to result.toString(), "user_info" to emptyMap<String, Any?>()))
    }

    companion object {
        private fun resolveUser(): Any? =
            try {
                if (Auth.check()) Auth.user() else null
            } catch (_: IllegalStateException) {
                null
            }
    }
}
