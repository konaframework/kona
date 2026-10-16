package app.http.resources

import io.konaframework.http.JsonResource
import io.konaframework.http.Request

/** Tiny demo resource for `/api/pings`. */
class PingResource(
    private val id: Int,
    private val message: String,
) : JsonResource(message) {
    override fun toArray(request: Request): Map<String, Any?> =
        mapOf(
            "id" to id,
            "message" to message,
            "path" to request.path,
        )
}
