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
 *
 * Soft reload (`grind serve` watch) builds a new [Application] while this object stays in the
 * parent class loader. [prepare] / [mount] clear the prior mount so panels bind to the new router.
 */
object Firefly {
    private val manual = ArrayList<Supplier<Panel>>()
    private val mounted = AtomicBoolean(false)

    @Volatile
    private var mountedApp: Application? = null

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
        mountedApp = null
    }

    /**
     * Clears a stale mount when [app] is a new [Application] (grind soft reload).
     * Call from [FireflyServiceProvider.register] before hand [register] calls so new
     * manuals are not wiped by a later remount.
     */
    @JvmStatic
    fun prepare(app: Application) {
        val previous = mountedApp
        if (previous != null && previous !== app) {
            reset()
        }
    }

    /**
     * Mounts hand-registered panels, then every panel class under `app/panels`
     * and [FireflyConfig.discovery]. A second call for the same [app] does nothing.
     * When the router is not bound yet, this returns and a later call can mount.
     */
    @JvmStatic
    fun mount(app: Application) {
        prepare(app)
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
        mountedApp = app
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
