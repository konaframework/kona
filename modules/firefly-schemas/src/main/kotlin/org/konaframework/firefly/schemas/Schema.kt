package org.konaframework.firefly.schemas

import org.konaframework.crema.CremaEngine
import org.konaframework.firefly.support.FireflyException
import java.util.function.Consumer

class Schema {
    private val roots = ArrayList<Component>()
    private val store = StateStore()
    private var statePath: String = ""
    private var operation: String = ""
    private var record: Any? = null
    private var key: String? = null
    private var defer: Boolean = false
    private var loaded: Boolean = false
    private var headingLevel: Int = 2
    private var crema: CremaEngine? = null
    private var columnCount: Int? = null
    val partialKeys = ArrayList<String>()

    fun components(vararg components: Component): Schema = apply {
        roots.clear()
        for (component in components) {
            attach(component, null)
            roots.add(component)
        }
    }

    fun components(): List<Component> = roots.toList()

    /** Root grid columns for top-level components (Shamar / Filament schema columns). */
    fun columns(count: Int): Schema = apply {
        if (count < 1) throw FireflyException("Schema columns must be at least 1")
        columnCount = count
    }

    fun columns(): Int? = columnCount

    fun statePath(path: String): Schema = apply { statePath = path }

    fun operation(name: String): Schema = apply { operation = name }

    fun operation(): String = operation

    fun record(value: Any?): Schema = apply { record = value }

    fun record(): Any? = record

    fun key(value: String): Schema = apply { key = value }

    fun key(): String? = key

    fun deferLoading(on: Boolean): Schema = apply { defer = on }

    fun isLoadingDeferred(): Boolean = defer && !loaded

    fun loadDeferred(): Schema = apply { loaded = true }

    fun rootHeadingLevel(level: Int): Schema = apply { headingLevel = level }

    fun headingLevel(): Int = headingLevel

    fun crema(engine: CremaEngine?): Schema = apply { crema = engine }

    fun fill(values: Map<String, Any?>): Schema = apply {
        for ((path, value) in values) {
            store.write(path, value)
        }
    }

    fun get(path: String): Any? = store.read(path)

    fun set(path: String, value: Any?): List<String> = set(path, value, true)

    fun set(path: String, value: Any?, shouldCallUpdatedHooks: Boolean): List<String> {
        val old = if (store.contains(path)) store.read(path) else null
        store.write(path, value)
        partialKeys.clear()
        if (!shouldCallUpdatedHooks) return partialKeys
        val component = find(path)
        if (component == null) return partialKeys
        val hook = component.updatedHook()
        if (hook != null) hook.updated(value, old, getFor(component), setFor(component))
        if (component.skipsRenderAfterUpdate()) return partialKeys
        partialKeys.addAll(component.partialAfterUpdate(path))
        return partialKeys
    }

    fun hydrate(): Schema = apply {
        for (component in walk()) {
            val path = component.absolutePath()
            if (path == null) continue
            if (!store.contains(path)) {
                val provided = component.provideDefault(getFor(component))
                if (provided.first) store.write(path, provided.second)
            }
            val hook = component.hydratedHook()
            if (hook != null) hook.hydrated(store.read(path), getFor(component), setFor(component))
        }
    }

    /** Write [Component.defaultValue] / [Component.defaultUsing] for paths not yet in the store. */
    fun seedDefaults(): Schema = apply {
        for (component in walk()) {
            val path = component.absolutePath() ?: continue
            if (path.isBlank()) continue
            if (store.contains(path)) continue
            val provided = component.provideDefault(getFor(component))
            if (provided.first) store.write(path, provided.second)
        }
    }

    fun dehydrate(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (component in walk()) {
            val path = component.absolutePath()
            if (path == null) continue
            if (!component.isDehydrated()) continue
            if (component.isHidden(this) && !component.isDehydratedWhenHidden()) continue
            component.beforeDehydrated(getFor(component), setFor(component))
            out[path] = store.read(path)
        }
        return out
    }

    fun render(): String {
        if (defer && !loaded) {
            if (key == null) throw FireflyException("Deferred schema is missing a key")
            return """<div class="ff-schema" data-ff-defer="true" data-ff-key="${escapeHtml(key!!)}">Loading</div>"""
        }
        // Seed missing paths from defaultValue / defaultUsing (create forms).
        // Only writes absent keys; does not re-fire afterStateHydrated (call hydrate() for that).
        seedDefaults()
        val body = roots.joinToString("") { it.render(this) }
        val cols = columnCount
        val colAttr = if (cols == null) "" else """ data-ff-columns="$cols" style="--ff-cols: $cols""""
        return """<div class="ff-schema"$colAttr>$body</div>"""
    }

    fun renderPartial(componentKey: String): String {
        val match = walk().firstOrNull { it.key() == componentKey }
        if (match == null) throw FireflyException("Unknown schema component [$componentKey]")
        return match.render(this)
    }

    fun renderView(name: String, data: Map<String, Any?>): String? {
        val engine = crema
        if (engine == null) return null
        return engine.render(name, data)
    }

    internal fun statePrefix(): String = statePath

    internal fun attach(component: Component, parent: Component?) {
        component.bind(this, parent)
        for (child in component.allChildren()) {
            attach(child, component)
        }
    }

    internal fun walk(): List<Component> {
        val out = ArrayList<Component>()
        fun visit(component: Component) {
            out.add(component)
            for (child in component.allChildren()) visit(child)
        }
        for (root in roots) visit(root)
        return out
    }

    internal fun find(path: String): Component? {
        for (component in walk()) {
            if (component.absolutePath() == path) return component
        }
        return null
    }

    internal fun getFor(component: Component): Get = Get { relative ->
        if (relative.isEmpty() || relative == "." || relative == "..") return@Get null
        val resolved = resolve(component, relative)
        if (resolved.isEmpty()) return@Get null
        store.read(resolved)
    }

    internal fun setFor(component: Component): Set = Set { relative, value, hooks ->
        val resolved = resolve(component, relative)
        if (resolved.isEmpty()) throw FireflyException("State path is empty")
        set(resolved, value, hooks)
    }

    internal fun resolve(component: Component, relative: String): String {
        if (relative.startsWith("/")) return relative.substring(1)
        val base = component.containerPath()
        val floor = prefixDepth()
        val parts = ArrayList<String>()
        if (base.isNotEmpty()) {
            for (part in base.split('.')) parts.add(part)
        }
        for (part in relative.split('/')) {
            if (part == "..") {
                if (parts.size > floor) parts.removeAt(parts.size - 1)
            } else if (part != "." && part.isNotEmpty()) {
                parts.add(part)
            }
        }
        return parts.joinToString(".")
    }

    private fun prefixDepth(): Int {
        if (statePath.isEmpty()) return 0
        return statePath.split('.').size
    }

    companion object {
        @JvmStatic
        fun make(): Schema = Schema()
    }
}

object ComponentConfig {
    private val configurers = HashMap<Class<*>, Consumer<Component>>()

    @JvmStatic
    fun set(type: Class<out Component>, block: Consumer<Component>?) {
        if (block == null) configurers.remove(type) else configurers[type] = block
    }

    fun apply(component: Component) {
        val block = configurers[component.javaClass]
        if (block != null) block.accept(component)
    }
}
