package app.firefly

import org.konaframework.core.Kona

/** Desk panel. Pages, widgets, and resources are discovered from this directory. */
class Panel : org.konaframework.firefly.Panel() {
    init {
        id("desk")
        path("admin")
        brand("Desk")
        authenticate(false)
        discover(Kona.app().basePath("src/main/kotlin/app/firefly"))
    }
}
