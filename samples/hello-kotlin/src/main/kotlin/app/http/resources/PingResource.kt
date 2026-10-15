package app.http.resources

import dev.kona.http.JsonResource
import dev.kona.http.Request

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
