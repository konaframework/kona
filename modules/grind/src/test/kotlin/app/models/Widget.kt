package app.models

import org.konaframework.cadence.Cadence

/** Test model for `make:firefly-resource --generate` against the widgets fixture table. */
class Widget : Cadence<Widget>() {
    override val fillable: Set<String> = setOf("name", "color")
}
