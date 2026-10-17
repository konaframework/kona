package org.konaframework.http

/**
 * API Resource transformer: maps a domain object to a JSON-shaped map.
 *
 * Conditional helpers ([when], [mergeWhen], [whenLoaded]) return [MissingValue] for omitted keys;
 * [resolve] strips those values and flattens [MergeValue] entries before wrapping.
 */
abstract class JsonResource(
    protected val resource: Any?,
) {
    private var additionalData: Map<String, Any?> = emptyMap()

    abstract fun toArray(request: Request): Map<String, Any?>

    /** Extra top-level keys merged beside the wrapped payload (override or use [additional]). */
    open fun with(request: Request): Map<String, Any?> = emptyMap()

    fun additional(data: Map<String, Any?>): JsonResource {
        additionalData = data
        return this
    }

    fun toResponse(request: Request): Response = Response.json(resolve(request))

    fun resolve(request: Request): Map<String, Any?> {
        val data = filter(toArray(request))
        val wrapped = wrapData(data)
        val top = LinkedHashMap<String, Any?>()
        top.putAll(wrapped)
        top.putAll(filter(with(request)))
        top.putAll(filter(additionalData))
        return top
    }

    /**
     * Include [value] when [condition] is true; otherwise [default] (defaults to [MissingValue],
     * which removes the key from the resolved payload).
     */
    protected fun `when`(condition: Boolean, value: Any?, default: Any? = MissingValue): Any? =
        if (condition) value else default

    /** Merge [attributes] into the parent map when [condition] is true. */
    protected fun mergeWhen(condition: Boolean, attributes: Map<String, Any?>): Any =
        if (condition) MergeValue(attributes) else MissingValue

    /**
     * Include a relationship when it is already loaded on [resource].
     * Detects Cadence-style models via `relationLoaded` / `getRelation` (no hard Cadence dependency).
     *
     * When [value] is omitted, the loaded relation itself is returned.
     */
    @JvmOverloads
    protected fun whenLoaded(
        relationship: String,
        value: Any? = USE_RELATION,
        default: Any? = MissingValue,
    ): Any? {
        val model = resource ?: return default
        if (!isRelationLoaded(model, relationship)) {
            return default
        }
        return if (value === USE_RELATION) getRelationValue(model, relationship) else value
    }

    companion object {
        @Volatile
        private var wrapKey: String? = "data"

        private val USE_RELATION = Any()

        /** Outer wrapper key for single resources and collections (`"data"` by default). */
        @JvmStatic
        fun wrap(key: String?) {
            wrapKey = key
        }

        /** Disable the outer wrapper for single resources and collections. */
        @JvmStatic
        fun withoutWrapping() {
            wrapKey = null
        }

        @JvmStatic
        fun wrapping(): String? = wrapKey

        /** Restore default wrapping (`"data"`). Useful in tests. */
        @JvmStatic
        fun flushState() {
            wrapKey = "data"
        }

        @JvmStatic
        fun collection(resources: Iterable<JsonResource>): ResourceCollection =
            ResourceCollection(resources.toList())

        internal fun currentWrap(): String? = wrapKey

        internal fun filter(data: Map<String, Any?>): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            for ((key, value) in data) {
                when (value) {
                    is MissingValue -> Unit
                    is MergeValue -> out.putAll(filter(value.attributes))
                    else -> out[key] = value
                }
            }
            return out
        }

        private fun wrapData(data: Map<String, Any?>): Map<String, Any?> {
            val key = wrapKey
            return if (key == null) data else mapOf(key to data)
        }

        private fun isRelationLoaded(model: Any, relationship: String): Boolean {
            val method = findModelMethod(model, "relationLoaded") ?: return false
            return runCatching { method.invoke(model, relationship) as Boolean }.getOrDefault(false)
        }

        private fun getRelationValue(model: Any, relationship: String): Any? {
            val method = findModelMethod(model, "getRelation") ?: return null
            return runCatching { method.invoke(model, relationship) }.getOrNull()
        }

        private fun findModelMethod(model: Any, name: String): java.lang.reflect.Method? =
            try {
                model.javaClass.getMethod(name, String::class.java)
            } catch (_: NoSuchMethodException) {
                null
            }
    }
}

/** Sentinel omitted from the resolved JSON map. */
object MissingValue

/** Flattened into the parent map during [JsonResource.resolve]. */
class MergeValue(val attributes: Map<String, Any?>)

/**
 * Collection of [JsonResource] items with optional wrapper key (defaults to [JsonResource.wrapping]).
 */
class ResourceCollection(
    private val resources: List<JsonResource>,
) {
    private var customWrap: String? = null
    private var hasCustomWrap: Boolean = false

    fun wrap(key: String?): ResourceCollection {
        customWrap = key
        hasCustomWrap = true
        return this
    }

    fun toArray(request: Request): Any {
        val items = resources.map { JsonResource.filter(it.toArray(request)) }
        val key = if (hasCustomWrap) customWrap else JsonResource.currentWrap()
        return if (key == null) items else mapOf(key to items)
    }

    fun toResponse(request: Request): Response = Response.json(toArray(request))
}
