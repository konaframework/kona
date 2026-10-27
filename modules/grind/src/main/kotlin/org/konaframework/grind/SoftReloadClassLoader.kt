package org.konaframework.grind

import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.util.Collections
import java.util.Enumeration

/**
 * Child-first class loader for `grind serve` soft reload.
 *
 * `./grind` launches with the full app `konaClasspath`, which includes
 * `build/classes` and `build/resources`. A plain [URLClassLoader] is parent-first,
 * so after `gradlew classes` the child URLs see new bytecode but
 * `Class.forName(…, child)` still returns the **stale** app classes already loaded
 * on the parent. Panel / page / resource edits then look like a silent no-op
 * (or Reloaded while the browser still gets old HTML).
 *
 * This loader prefers its own URLs for application types and for resources, while
 * keeping `org.konaframework.*` (and JDK / Kotlin) on the parent so SoftReload
 * facades (`Firefly` / `Wire` / `Inertia`) stay a single process-wide identity.
 */
internal class SoftReloadClassLoader(
    urls: Array<URL>,
    parent: ClassLoader?,
) : URLClassLoader(urls, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!preferChildClass(name)) {
            return super.loadClass(name, resolve)
        }
        synchronized(getClassLoadingLock(name)) {
            var loaded = findLoadedClass(name)
            if (loaded == null) {
                // Only child-first for exploded app outputs. Dependency jars on konaClasspath
                // must stay parent-first — otherwise in-process serve hits LinkageError when
                // the grind JVM already loaded the same library types.
                val classFile = name.replace('.', '/') + ".class"
                val url = findResource(classFile)
                if (url != null && isExplodedClassUrl(url)) {
                    loaded = try {
                        findClass(name)
                    } catch (_: ClassNotFoundException) {
                        null
                    }
                }
            }
            if (loaded == null) {
                return super.loadClass(name, resolve)
            }
            // Link app classes from child URLs so soft-reload identity is stable across loads.
            resolveClass(loaded)
            return loaded
        }
    }

    override fun getResource(name: String): URL? {
        return findResource(name) ?: getParent()?.getResource(name)
    }

    @Throws(IOException::class)
    override fun getResources(name: String): Enumeration<URL> {
        val child = findResources(name).toList()
        val parentLoader = getParent()
        val fromParent = if (parentLoader == null) {
            emptyList()
        } else {
            parentLoader.getResources(name).toList()
        }
        if (child.isEmpty()) {
            return Collections.enumeration(fromParent)
        }
        if (fromParent.isEmpty()) {
            return Collections.enumeration(child)
        }
        val merged = ArrayList<URL>(child.size + fromParent.size)
        merged.addAll(child)
        for (url in fromParent) {
            if (url !in merged) {
                merged.add(url)
            }
        }
        return Collections.enumeration(merged)
    }

    companion object {
        /**
         * Application / bootstrap / config types reload from child URLs.
         * Framework + platform types stay parent-first.
         */
        fun preferChildClass(name: String): Boolean {
            if (name.startsWith("org.konaframework.")) {
                return false
            }
            if (name.startsWith("java.") ||
                name.startsWith("javax.") ||
                name.startsWith("jakarta.") ||
                name.startsWith("jdk.") ||
                name.startsWith("sun.") ||
                name.startsWith("com.sun.") ||
                name.startsWith("kotlin.") ||
                name.startsWith("kotlinx.") ||
                name.startsWith("org.jetbrains.")
            ) {
                return false
            }
            return true
        }

        /** True when [url] points at a `.class` under an exploded directory, not inside a jar. */
        fun isExplodedClassUrl(url: URL): Boolean {
            val protocol = url.protocol
            if (protocol == "jar") {
                return false
            }
            if (protocol != "file") {
                return false
            }
            val path = url.path
            return !path.contains(".jar!") && !path.contains(".jar/")
        }
    }
}
