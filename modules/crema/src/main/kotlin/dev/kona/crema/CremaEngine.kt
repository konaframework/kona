package dev.kona.crema

import dev.kona.http.Response

/**
 * Crema view factory and renderer.
 */
class CremaEngine(
    private val finder: ViewFinder,
) {
    /** Custom directives / tag prefixes (e.g. packages registering `@brew` and `<brew:…>`). */
    val extensions: CremaExtensions = CremaExtensions()

    private val runtime = CremaRuntime(finder, extensions)

    @JvmOverloads
    fun make(name: String, data: Map<String, Any?> = emptyMap()): View =
        View(name, LinkedHashMap(data), this)

    @JvmOverloads
    fun render(name: String, data: Map<String, Any?> = emptyMap()): String =
        runtime.render(name, data)

    /** Render [name] with a caller-built [context] (flags such as [CremaContext.MORPH_MARKERS]). */
    fun render(name: String, context: CremaContext): String =
        runtime.render(name, context)

    @JvmOverloads
    fun renderSource(source: String, data: Map<String, Any?> = emptyMap()): String =
        runtime.renderSource(source, CremaContext().merge(data))

    fun renderSource(source: String, context: CremaContext): String =
        runtime.renderSource(source, context)

    /** Whether a view named [name] can be resolved by this engine's finder. */
    fun exists(name: String): Boolean =
        try {
            finder.get(name)
            true
        } catch (_: ViewNotFoundException) {
            false
        }

    /** Register `@name` / `@name(args)`. */
    fun directive(name: String, handler: CremaDirective): CremaEngine {
        extensions.directive(name, handler)
        return this
    }

    /** Register `@name(args) … @endname`. */
    fun blockDirective(name: String, handler: CremaBlockDirective): CremaEngine {
        extensions.blockDirective(name, handler)
        return this
    }

    /** Register `<prefix:name …/>` and `<prefix:name>…</prefix:name>`. */
    fun tag(prefix: String, handler: CremaTagHandler): CremaEngine {
        extensions.tag(prefix, handler)
        return this
    }

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
