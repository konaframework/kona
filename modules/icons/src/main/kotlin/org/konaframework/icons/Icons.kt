package org.konaframework.icons

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Central icon factory (Blade Icons–inspired).
 *
 * Heroicons ship on the classpath under prefix `heroicon`
 * (`heroicon-o-bell`, `heroicon-s-home`, `heroicon-m-check`).
 * Register more sets with [add] from classpath directories or filesystem paths.
 */
object Icons {
    private val sets = ConcurrentHashMap<String, IconSet>()
    private val prefixIndex = ConcurrentHashMap<String, String>()
    private val contentCache = ConcurrentHashMap<String, String>()
    private val lock = Any()

    @Volatile
    private var defaultSet: String? = null

    @Volatile
    private var globalFallback: String? = null

    /** Ensure the bundled Heroicons set is registered (idempotent). */
    @JvmStatic
    fun ensureDefaults() {
        synchronized(lock) {
            if (!sets.containsKey("heroicons")) {
                add(
                    "heroicons",
                    IconSetOptions(
                        prefix = "heroicon",
                        classpath = "org/konaframework/icons/heroicons",
                    ),
                )
            }
        }
    }

    /**
     * Register an icon set. [options.prefix] must be unique across sets.
     * At least one filesystem path or classpath root is required.
     */
    @JvmStatic
    fun add(name: String, options: IconSetOptions): Icons {
        val prefix = options.prefix.trim()
        if (prefix.isEmpty()) {
            throw CannotRegisterIconSetException("Icon set [$name] is missing a prefix")
        }
        val paths = linkedSetOf<Path>().apply {
            options.path?.let { add(it) }
            addAll(options.paths)
        }.map { if (it.toString() == "/") it else it.toAbsolutePath().normalize() }
        val classpaths = linkedSetOf<String>().apply {
            options.classpath?.let { add(normalizeClasspath(it)) }
            options.classpaths.forEach { add(normalizeClasspath(it)) }
        }.toList()
        if (paths.isEmpty() && classpaths.isEmpty()) {
            throw CannotRegisterIconSetException("Icon set [$name] is missing paths or classpath roots")
        }
        for (path in paths) {
            if (path.toString() != "/" && !Files.isDirectory(path)) {
                throw CannotRegisterIconSetException("Icon set [$name] path does not exist [$path]")
            }
        }
        synchronized(lock) {
            val colliding = prefixIndex[prefix]
            if (colliding != null && colliding != name) {
                throw CannotRegisterIconSetException(
                    "Icon set [$name] prefix [$prefix] collides with set [$colliding]",
                )
            }
            val set = IconSet(
                name = name,
                prefix = prefix,
                paths = paths,
                classpaths = classpaths,
                className = options.className,
                attributes = options.attributes,
                fallback = optionalName(options.fallback),
            )
            sets[name] = set
            prefixIndex[prefix] = name
            contentCache.keys.filter { it.startsWith("$name:") }.forEach { contentCache.remove(it) }
            if (defaultSet == null) {
                defaultSet = name
            }
        }
        return this
    }

    /** Name of the set used when an icon name has no prefix. */
    @JvmStatic
    fun defaultSet(): String? {
        ensureDefaults()
        return defaultSet
    }

    @JvmStatic
    fun setDefault(name: String): Icons {
        ensureDefaults()
        if (!sets.containsKey(name)) {
            throw CannotRegisterIconSetException("Unknown icon set [$name]")
        }
        defaultSet = name
        return this
    }

    /** Global fallback icon name (with or without prefix) when a set-local fallback fails. */
    @JvmStatic
    fun setFallback(name: String?): Icons {
        globalFallback = optionalName(name)
        return this
    }

    @JvmStatic
    fun fallback(): String? = globalFallback

    @JvmStatic
    fun all(): Map<String, IconSet> {
        ensureDefaults()
        return sets.toMap()
    }

    @JvmStatic
    @JvmOverloads
    fun svg(name: String, className: String = "", attributes: Map<String, String> = emptyMap()): Svg {
        ensureDefaults()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            throw IconNotFoundException(name)
        }
        return try {
            resolveSvg(trimmed, className, attributes)
        } catch (ex: IconNotFoundException) {
            val global = globalFallback
            if (global != null && global != trimmed) {
                resolveSvg(global, className, attributes)
            } else {
                throw ex
            }
        }
    }

    /** True when [name] resolves without throwing. */
    @JvmStatic
    fun has(name: String): Boolean {
        ensureDefaults()
        return try {
            svg(name)
            true
        } catch (_: IconNotFoundException) {
            false
        }
    }

    /** Clear registrations (tests). Re-call [ensureDefaults] or [svg] to restore Heroicons. */
    @JvmStatic
    fun reset() {
        synchronized(lock) {
            sets.clear()
            prefixIndex.clear()
            contentCache.clear()
            defaultSet = null
            globalFallback = null
        }
    }

    private fun resolveSvg(name: String, className: String, attributes: Map<String, String>): Svg {
        val (set, icon) = splitSetAndName(name)
        val contents = loadContents(set, icon)
            ?: set.fallback?.let { loadContents(set, it) }
            ?: throw IconNotFoundException(name)
        val attrs = LinkedHashMap<String, String>()
        attrs.putAll(set.attributes)
        if (!set.className.isNullOrBlank()) {
            attrs["class"] = set.className
        }
        attrs.putAll(attributes)
        val svg = Svg(name, contents, attrs)
        if (className.isNotBlank()) {
            svg.className(className)
        }
        return svg
    }

    private fun splitSetAndName(name: String): Pair<IconSet, String> {
        val byPrefix = prefixIndex.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { name == it.key || name.startsWith(it.key + "-") }
        if (byPrefix != null) {
            val set = sets[byPrefix.value]!!
            val icon = if (name == set.prefix) {
                ""
            } else {
                name.removePrefix(set.prefix + "-")
            }
            if (icon.isEmpty()) {
                throw IconNotFoundException(name)
            }
            return set to icon
        }
        // ensureDefaults() always registers a default before resolve
        return sets.getValue(defaultSet!!) to name
    }

    private fun loadContents(set: IconSet, icon: String): String? {
        val cacheKey = "${set.name}:$icon"
        contentCache[cacheKey]?.let { return it }
        val fileName = if (icon.endsWith(".svg")) icon else "$icon.svg"
        for (root in set.classpaths) {
            val resource = "/$root/$fileName"
            val stream = Icons::class.java.getResourceAsStream(resource) ?: continue
            val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
            contentCache[cacheKey] = text
            return text
        }
        for (root in set.paths) {
            val file = root.resolve(fileName)
            if (Files.isRegularFile(file)) {
                val text = Files.readString(file)
                contentCache[cacheKey] = text
                return text
            }
        }
        return null
    }

    private fun normalizeClasspath(value: String): String =
        value.trim().trim('/').replace('\\', '/')
}

internal fun optionalName(value: String?): String? {
    if (value == null) return null
    val trimmed = value.trim()
    return if (trimmed.isEmpty()) null else trimmed
}
