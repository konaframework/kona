package org.konaframework.brew

import org.konaframework.core.Container
import java.io.File
import java.net.JarURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

/**
 * Component name ↔ class registry (`Livewire::component`, `addNamespace`, `addLocation`, discovery).
 *
 * Names are kebab-case with dots for nesting: `posts.create-post` ↔ `<location>.posts.CreatePost`.
 * Namespaced names use `ns::name` (e.g. `pages::dashboard`).
 */
class ComponentRegistry @JvmOverloads constructor(private val container: Container? = null) {
    private val factories = ConcurrentHashMap<String, () -> Component>()
    private val classes = ConcurrentHashMap<String, Class<out Component>>()
    private val names = ConcurrentHashMap<Class<*>, String>()
    private val namespaces = ConcurrentHashMap<String, String>()
    private val locations = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    /**
     * Preferred loader for convention resolution (`app.brew.Counter`, …).
     * Set during [BrewServiceProvider.boot] so `grind serve --in-process` (URLClassLoader apps)
     * still resolves components when request threads do not inherit the app TCCL.
     */
    @Volatile
    var classLoader: ClassLoader = ComponentRegistry::class.java.classLoader

    /** Register [type] under [name]. */
    fun component(name: String, type: Class<out Component>): ComponentRegistry {
        classes[name] = type
        names[type] = name
        misses.remove(name)
        return this
    }

    /** Register [type] under its `@BrewComponent` name or derived kebab name. */
    fun component(type: Class<out Component>): ComponentRegistry = component(nameFor(type, null), type)

    /** Register a factory (constructor injection, closures). */
    @JvmSynthetic
    fun component(name: String, factory: () -> Component): ComponentRegistry {
        factories[name] = factory
        misses.remove(name)
        return this
    }

    fun component(name: String, factory: Supplier<out Component>): ComponentRegistry = component(name) { factory.get() }

    /** `ns::name` resolves to classes under [packageName]. */
    fun addNamespace(namespace: String, packageName: String): ComponentRegistry {
        namespaces[namespace] = packageName
        misses.clear()
        return this
    }

    /** Extra package searched for un-namespaced convention names. */
    fun addLocation(packageName: String): ComponentRegistry {
        if (packageName !in locations) locations += packageName
        misses.clear()
        return this
    }

    /** Scan [packageName] on the classpath and register every concrete [Component] subclass. Returns the names. */
    @JvmOverloads
    fun discover(packageName: String, loader: ClassLoader = Thread.currentThread().contextClassLoader): List<String> {
        val found = mutableListOf<String>()
        for (className in ClassScanner.classNames(packageName, loader)) {
            val type = try {
                Class.forName(className, false, loader)
            } catch (_: Throwable) {
                continue
            }
            if (!Component::class.java.isAssignableFrom(type) || java.lang.reflect.Modifier.isAbstract(type.modifiers)) continue
            @Suppress("UNCHECKED_CAST")
            val component = type as Class<out Component>
            val name = nameFor(component, packageName)
            component(name, component)
            found += name
        }
        return found
    }

    fun has(name: String): Boolean = factories.containsKey(name) || classes.containsKey(name) || lookup(name) != null

    fun names(): Set<String> = factories.keys + classes.keys

    /** Name registered (or derivable) for [type]. */
    fun nameOf(type: Class<*>): String = names[type] ?: nameFor(type, null)

    /** Class for [name] when class-registered or convention-resolvable (null for factory-only names). */
    fun classOf(name: String): Class<out Component>? = classes[name] ?: lookup(name)

    /** New, unattached instance for [name]. */
    fun create(name: String): Component {
        factories[name]?.let { return it() }
        val type = classes[name] ?: lookup(name) ?: throw ComponentNotFoundException(name)
        return instantiate(type)
    }

    /** Instantiate via the container (constructor injection) or the no-arg constructor. */
    fun instantiate(type: Class<out Component>): Component {
        val c = container
        if (c != null) return c.make(type)
        val ctor = type.getDeclaredConstructor()
        ctor.isAccessible = true
        return ctor.newInstance()
    }

    private fun lookup(name: String): Class<out Component>? {
        if (name in misses) return null
        val resolved = candidates(name).firstNotNullOfOrNull { load(it) }
        if (resolved == null) {
            misses += name
        } else {
            classes[name] = resolved
            names.putIfAbsent(resolved, name)
        }
        return resolved
    }

    private fun candidates(name: String): List<String> {
        val ns = name.substringBefore("::", "")
        val local = name.substringAfter("::")
        val roots = if (ns.isNotEmpty()) listOfNotNull(namespaces[ns]) else locations
        val parts = local.split('.')
        val pkg = parts.dropLast(1).joinToString(".") { it.replace('-', '_') }
        val simple = studly(parts.last())
        return roots.map { root -> listOf(root, pkg, simple).filter { it.isNotEmpty() }.joinToString(".") }
    }

    private fun load(className: String): Class<out Component>? {
        val loaders = linkedSetOf(classLoader)
        Thread.currentThread().contextClassLoader?.let { loaders += it }
        loaders += ComponentRegistry::class.java.classLoader
        for (loader in loaders) {
            val type = try {
                Class.forName(className, false, loader)
            } catch (_: ClassNotFoundException) {
                continue
            } catch (_: NoClassDefFoundError) {
                continue
            }
            @Suppress("UNCHECKED_CAST")
            if (Component::class.java.isAssignableFrom(type)) return type as Class<out Component>
        }
        return null
    }

    companion object {
        /**
         * `ShowPosts` → `show-posts`; with [root] `app.brew` and class `app.brew.admin_area.ShowPosts` →
         * `admin-area.show-posts`. `@BrewComponent` wins.
         */
        @JvmStatic
        fun nameFor(type: Class<*>, root: String?): String {
            type.getAnnotation(BrewComponent::class.java)?.let { return it.value }
            val simple = kebab(type.simpleName)
            val pkg = type.packageName
            if (root.isNullOrEmpty() || !pkg.startsWith(root) || pkg == root) return simple
            return pkg.removePrefix("$root.").replace('_', '-') + "." + simple
        }

        @JvmStatic
        fun kebab(value: String): String =
            value.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1-$2").lowercase()

        @JvmStatic
        fun studly(value: String): String =
            value.split('-', '_').filter { it.isNotEmpty() }.joinToString("") { it.substring(0, 1).uppercase() + it.substring(1) }
    }
}

/** Minimal classpath scanner for directories and jars. */
internal object ClassScanner {
    fun classNames(packageName: String, loader: ClassLoader): List<String> {
        val path = packageName.replace('.', '/')
        val out = linkedSetOf<String>()
        for (url in loader.getResources(path).toList()) {
            when (url.protocol) {
                "file" -> scanDirectory(File(url.toURI()), packageName, out)
                "jar" -> scanJar(url.openConnection() as JarURLConnection, path, out)
            }
        }
        return out.toList()
    }

    private fun scanDirectory(dir: File, packageName: String, out: MutableSet<String>) {
        for (file in dir.listFiles().orEmpty().sortedBy { it.name }) {
            if (file.isDirectory) {
                scanDirectory(file, "$packageName.${file.name}", out)
            } else if (file.name.endsWith(".class") && !file.name.contains('$')) {
                out += "$packageName.${file.name.removeSuffix(".class")}"
            }
        }
    }

    private fun scanJar(connection: JarURLConnection, path: String, out: MutableSet<String>) {
        connection.useCaches = false
        connection.jarFile.use { jar ->
            for (entry in jar.entries()) {
                val name = entry.name
                if (name.startsWith("$path/") && name.endsWith(".class") && !name.contains('$')) {
                    out += name.removeSuffix(".class").replace('/', '.')
                }
            }
        }
    }
}
