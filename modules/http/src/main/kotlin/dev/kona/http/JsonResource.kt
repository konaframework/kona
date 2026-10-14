package dev.kona.http

/**
 * API Resource transformer (Laravel JsonResource shape).
 */
abstract class JsonResource(
    protected val resource: Any?,
) {
    abstract fun toArray(request: Request): Map<String, Any?>

    fun toResponse(request: Request): Response = Response.json(toArray(request))

    fun resolve(request: Request): Map<String, Any?> = toArray(request)

    companion object {
        @JvmStatic
        fun collection(resources: Iterable<JsonResource>): ResourceCollection =
            ResourceCollection(resources.toList())
    }
}

/**
 * Collection of [JsonResource] items with optional wrapper key.
 */
class ResourceCollection(
    private val resources: List<JsonResource>,
    private var wrap: String? = "data",
) {
    fun wrap(key: String?): ResourceCollection {
        wrap = key
        return this
    }

    fun toArray(request: Request): Any {
        val items = resources.map { it.toArray(request) }
        val key = wrap
        return if (key == null) items else mapOf(key to items)
    }

    fun toResponse(request: Request): Response = Response.json(toArray(request))
}
