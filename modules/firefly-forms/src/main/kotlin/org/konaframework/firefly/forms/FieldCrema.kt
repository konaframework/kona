package org.konaframework.firefly.forms

import org.konaframework.crema.CremaEngine
import org.konaframework.firefly.support.ClasspathViewFinder

/**
 * Bundled Crema engine for built-in field chrome (Phase D).
 *
 * Label, hint, prefix, and suffix wrappers live in `views/firefly/fields/`.
 * The control itself stays a Kotlin snippet.
 */
internal object FieldCrema {
    const val WRAPPER: String = "firefly.fields.wrapper"
    const val LABEL: String = "firefly.fields.label"

    private val engine: CremaEngine by lazy {
        CremaEngine(ClasspathViewFinder(FieldCrema::class.java.classLoader))
    }

    fun render(name: String, data: Map<String, Any?>): String = engine.render(name, data)
}
