package org.konaframework.firefly

import org.konaframework.core.Application
import org.konaframework.http.Router
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/**
 * Registers panels and mounts them when the application boots.
 *
 * Panels under `app/panels` are discovered. Call [register] for a panel you build by hand.
 */
object Firefly {
    private val manual = ArrayList<Supplier<Panel>>()
    private val mounted = AtomicBoolean(false)

    /** Keeps [panel] and mounts it on the next [mount]. The same instance is used. */
    @JvmStatic
    fun register(panel: Panel): Firefly = apply {
        manual.add(Supplier { panel })
    }

    /** Builds a panel when the application mounts. Invoked once. */
    @JvmStatic
    fun register(provider: Supplier<Panel>): Firefly = apply {
        manual.add(provider)
    }

    /** Drops registrations. The next [mount] runs again. */
    @JvmStatic
    fun reset() {
        manual.clear()
        mounted.set(false)
    }

    /**
     * Mounts hand-registered panels, then every panel class under `app/panels`
     * and [FireflyConfig.discovery]. A second call does nothing.
     * When the router is not bound yet, this returns and a later call can mount.
     */
    @JvmStatic
    fun mount(app: Application) {
        if (!mounted.compareAndSet(false, true)) return
        if (!app.container.bound(Router::class.java)) {
            mounted.set(false)
            return
        }
        val router = app.resolve(Router::class.java)
        val hands = ArrayList<Panel>()
        val seen = HashSet<String>()
        for (supplier in manual) {
            val panel = supplier.get()
            hands.add(panel)
            seen.add(panel.javaClass.name)
        }
        for (panel in hands) {
            panel.mount(router)
        }
        for (panel in discover(app)) {
            if (seen.add(panel.javaClass.name)) {
                panel.mount(router)
            }
        }
    }

    private fun discover(app: Application): List<Panel> {
        val config = FireflyConfig.discovery(app.config)
        val found = ArrayList<Panel>()
        val seen = HashSet<String>()
        for (root in roots(app, config)) {
            for (panel in PanelDiscovery.panels(root)) {
                if (seen.add(panel.javaClass.name)) found.add(panel)
            }
        }
        return found
    }

    private fun roots(app: Application, config: List<String>): List<Path> {
        val base = app.basePath()
        val roots = ArrayList<Path>()
        roots.add(base.resolve("src/main/kotlin/app/panels"))
        roots.add(base.resolve("src/main/java/app/panels"))
        for (extra in config) {
            val path = Path.of(extra)
            roots.add(if (path.isAbsolute) path else base.resolve(path))
        }
        return roots
    }
}
