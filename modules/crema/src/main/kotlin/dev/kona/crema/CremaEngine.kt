package dev.kona.crema

import dev.kona.http.Response

/**
 * Crema view factory and renderer.
 */
class CremaEngine(
    private val finder: ViewFinder,
) {
    private val runtime = CremaRuntime(finder)

    @JvmOverloads
    fun make(name: String, data: Map<String, Any?> = emptyMap()): View =
        View(name, LinkedHashMap(data), this)

    @JvmOverloads
    fun render(name: String, data: Map<String, Any?> = emptyMap()): String =
        runtime.render(name, data)

    @JvmOverloads
    fun renderSource(source: String, data: Map<String, Any?> = emptyMap()): String =
        runtime.renderSource(source, CremaContext().merge(data))

    internal fun runtime(): CremaRuntime = runtime
}

class View internal constructor(
    val name: String,
    private val data: MutableMap<String, Any?>,
    private val engine: CremaEngine,
) {
    fun with(key: String, value: Any?): View {
        data[key] = value
        return this
    }

    fun with(values: Map<String, Any?>): View {
        data.putAll(values)
        return this
    }

    fun render(): String = engine.render(name, data)

    @JvmOverloads
    fun toResponse(status: Int = 200): Response = Response.html(render(), status)
}

object Crema {
    @JvmStatic
    @JvmOverloads
    fun view(engine: CremaEngine, name: String, data: Map<String, Any?> = emptyMap(), status: Int = 200): Response =
        engine.make(name, data).toResponse(status)
}
