package org.konaframework.firefly.support

import org.konaframework.crema.CremaEngine
import org.konaframework.crema.ViewFinder
import org.konaframework.crema.ViewNotFoundException
import org.konaframework.crema.normalizeViewName

/**
 * Loads Crema templates (`.crema.html` / `.crema.kt`) from a [ClassLoader] views root.
 *
 * Used by Firefly chrome so package layouts ship inside `:kona-firefly-support`
 * without requiring an app `resources/views` tree.
 */
class ClasspathViewFinder(
    private val classLoader: ClassLoader,
    private val root: String = "views",
    private val extensions: List<String> = listOf(".crema.html", ".crema.kt"),
) : ViewFinder {
    override fun get(name: String): String {
        val canonical = normalizeViewName(name)
        val relative = canonical.replace('.', '/')
        val base = root.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        for (ext in extensions) {
            val path = "$base$relative$ext"
            val stream = classLoader.getResourceAsStream(path) ?: continue
            val text = stream.readAllBytes().toString(Charsets.UTF_8)
            stream.close()
            return text
        }
        throw ViewNotFoundException(canonical)
    }
}

/**
 * Bundled Crema engine for Firefly support layouts.
 *
 * Phase A: [DefaultLayout] renders [PANEL] (`views/firefly/layouts/panel.crema.html`).
 * A full Wire page-host component is deferred; Kotlin remains the [PanelLayout] adapter.
 */
object FireflyCrema {
    const val PANEL: String = "firefly.layouts.panel"

    private val defaultEngine: CremaEngine by lazy {
        CremaEngine(ClasspathViewFinder(FireflyCrema::class.java.classLoader))
    }

    @JvmStatic
    fun engine(): CremaEngine = defaultEngine

    @JvmStatic
    fun renderPanel(data: Map<String, Any?>): String = defaultEngine.render(PANEL, data)
}
