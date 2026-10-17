package org.konaframework.crema

import org.konaframework.http.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Crema view factory and renderer.
 *
 * Prefer the app-bound helpers [view] / [Views] in controllers; use this type when you need a
 * local engine (tests, custom finders, or multiple roots).
 */
class CremaEngine(
    private val finder: ViewFinder,
) {
    /** Custom directives / tag prefixes (e.g. packages registering `@brew` and `<brew:…>`). */
    val extensions: CremaExtensions = CremaExtensions()

    private val runtime = CremaRuntime(finder, extensions)

    private val sharedData = ConcurrentHashMap<String, Any?>()

    /** Data merged into every [make] / [render] (per-view keys win). */
    fun share(key: String, value: Any?): CremaEngine {
        sharedData[key] = value
        return this
    }

    fun share(values: Map<String, Any?>): CremaEngine {
        sharedData.putAll(values)
        return this
    }

    fun shared(): Map<String, Any?> = LinkedHashMap(sharedData)

    fun flushShared() {
        sharedData.clear()
    }

    @JvmOverloads
    fun make(name: String, data: Map<String, Any?> = emptyMap()): View =
        View(normalizeViewName(name), LinkedHashMap(data), this)

    /**
     * First resolvable name in [names]. Throws [ViewNotFoundException] when none exist.
     */
    @JvmOverloads
    fun first(names: Collection<String>, data: Map<String, Any?> = emptyMap()): View {
        val match = names.firstOrNull { exists(it) }
        if (match == null) {
            val listed =
                if (names.isEmpty()) {
                    "(empty)"
                } else {
                    names.joinToString(", ") { normalizeViewName(it) }
                }
            throw ViewNotFoundException(listed)
        }
        return make(match, data)
    }

    @JvmOverloads
    fun render(name: String, data: Map<String, Any?> = emptyMap()): String =
        runtime.render(normalizeViewName(name), mergeShared(data))

    /** Render [name] with a caller-built [context] (flags such as [CremaContext.MORPH_MARKERS]). */
    fun render(name: String, context: CremaContext): String =
        runtime.render(normalizeViewName(name), withShared(context))

    @JvmOverloads
    fun renderSource(source: String, data: Map<String, Any?> = emptyMap()): String =
        runtime.renderSource(source, CremaContext().merge(mergeShared(data)))

    fun renderSource(source: String, context: CremaContext): String =
        runtime.renderSource(source, withShared(context))

    /** Whether a view named [name] can be resolved by this engine's finder. */
    fun exists(name: String): Boolean =
        try {
            finder.get(normalizeViewName(name))
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

    private fun mergeShared(data: Map<String, Any?>): Map<String, Any?> {
        if (sharedData.isEmpty()) {
            return data
        }
        val merged = LinkedHashMap<String, Any?>(sharedData.size + data.size)
        merged.putAll(sharedData)
        merged.putAll(data)
        return merged
    }

    /** Shared keys fill gaps; existing context keys win. Sections/stacks stay on the child. */
    private fun withShared(context: CremaContext): CremaContext {
        if (sharedData.isEmpty()) {
            return context
        }
        val present = context.snapshot()
        val child = context.child()
        for ((key, value) in sharedData) {
            if (!present.containsKey(key)) {
                child.set(key, value)
            }
        }
        return child
    }
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

    /** Snapshot of data bound to this view (does not include later [CremaEngine.share] keys). */
    fun data(): Map<String, Any?> = LinkedHashMap(data)

    fun render(): String = engine.render(name, data)

    @JvmOverloads
    fun toResponse(status: Int = 200): Response = Response.html(render(), status)
}

object Crema {
    @JvmStatic
    @JvmOverloads
    fun view(engine: CremaEngine, name: String, data: Map<String, Any?> = emptyMap(), status: Int = 200): Response =
        engine.make(name, data).toResponse(status)

    /** HTML response using the app-bound [Views] engine. */
    @JvmStatic
    @JvmOverloads
    fun view(name: String, data: Map<String, Any?> = emptyMap(), status: Int = 200): Response =
        Views.response(name, data, status)
}
