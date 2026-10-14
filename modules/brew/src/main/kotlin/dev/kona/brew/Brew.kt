package dev.kona.brew

import dev.kona.crema.CremaEngine

/**
 * Facade for mounting Brew components and rendering Crema views from components.
 */
object Brew {
    @Volatile
    private var manager: BrewManager? = null

    @JvmStatic
    fun setManager(value: BrewManager) {
        manager = value
    }

    @JvmStatic
    fun manager(): BrewManager =
        manager ?: throw BrewException("BrewManager is not set; register BrewServiceProvider")

    @JvmStatic
    fun reset() {
        manager = null
    }

    /** Embed a registered component’s HTML (including Brew root wrapper). */
    @JvmStatic
    fun mount(name: String): String = manager().mount(name)

    /** Render a Crema view with data (helper for component [Component.render]). */
    @JvmStatic
    fun view(engine: CremaEngine, name: String, data: Map<String, Any?>): String =
        engine.render(name, data)

    @JvmStatic
    fun view(engine: CremaEngine, name: String): String =
        view(engine, name, emptyMap())
}
