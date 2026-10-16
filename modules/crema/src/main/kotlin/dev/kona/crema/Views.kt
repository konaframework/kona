package dev.kona.crema

import dev.kona.http.Response

/**
 * Global view factory holder (same idea as [dev.kona.http.Url] / [dev.kona.support.lang.Lang]).
 *
 * [CremaServiceProvider] wires the app's [CremaEngine] at boot. Kotlin callers typically use the
 * top-level [view] helper; Java callers use the static methods on this object.
 */
object Views {
    @Volatile
    private var engine: CremaEngine? = null

    @JvmStatic
    fun setEngine(engine: CremaEngine) {
        this.engine = engine
    }

    /** Clears the global engine (tests). */
    @JvmStatic
    fun clearEngine() {
        engine = null
    }

    @JvmStatic
    fun engine(): CremaEngine =
        engine ?: throw IllegalStateException("View engine not configured")

    @JvmStatic
    fun configured(): Boolean = engine != null

    @JvmStatic
    @JvmOverloads
    fun make(name: String, data: Map<String, Any?> = emptyMap()): View =
        engine().make(name, data)

    /**
     * First resolvable name in [names], or [ViewNotFoundException] listing every candidate.
     */
    @JvmStatic
    @JvmOverloads
    fun first(names: Collection<String>, data: Map<String, Any?> = emptyMap()): View =
        engine().first(names, data)

    @JvmStatic
    fun exists(name: String): Boolean = engine().exists(name)

    @JvmStatic
    @JvmOverloads
    fun render(name: String, data: Map<String, Any?> = emptyMap()): String =
        engine().render(name, data)

    /** HTML [Response] for [name] (status defaults to 200). */
    @JvmStatic
    @JvmOverloads
    fun response(name: String, data: Map<String, Any?> = emptyMap(), status: Int = 200): Response =
        make(name, data).toResponse(status)

    /** Share [key] with every subsequent [make] / [render] on the bound engine. */
    @JvmStatic
    fun share(key: String, value: Any?): Views {
        engine().share(key, value)
        return this
    }

    @JvmStatic
    fun share(values: Map<String, Any?>): Views {
        engine().share(values)
        return this
    }

    @JvmStatic
    fun shared(): Map<String, Any?> = engine().shared()

    @JvmStatic
    fun flushShared() {
        engine().flushShared()
    }
}

/** Top-level Kotlin helper: make a [View] from the app-bound Crema engine. */
@JvmOverloads
fun view(name: String, data: Map<String, Any?> = emptyMap()): View = Views.make(name, data)
