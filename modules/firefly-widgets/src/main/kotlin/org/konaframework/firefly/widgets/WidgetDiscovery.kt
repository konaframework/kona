package org.konaframework.firefly.widgets

import org.konaframework.firefly.support.FireflyException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads widgets from a panel `widgets/` directory.
 *
 * Each `.kt` or `.java` file names a class already on the classpath. The file's package and
 * class declaration are the lookup key. Abstract classes and types that are not widgets are skipped.
 */
object WidgetDiscovery {
    private val PACKAGE_LINE = Regex("""(?:^|\n)\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
    private val CLASS_LINE = Regex(
        """(?:^|\n)\s*(?:public\s+|open\s+|internal\s+|final\s+|abstract\s+)*class\s+([A-Za-z_][A-Za-z0-9_]*)""",
    )

    @JvmStatic
    fun discover(panelDirectory: Path): List<Widget> =
        discover(panelDirectory, Thread.currentThread().contextClassLoader)

    @JvmStatic
    fun discover(panelDirectory: Path, loader: ClassLoader): List<Widget> {
        val dir = panelDirectory.resolve("widgets")
        if (!Files.exists(dir)) return emptyList()
        if (!Files.isDirectory(dir)) throw FireflyException("Widgets path is not a directory [$dir]")
        val found = ArrayList<Widget>()
        Files.walk(dir).use { stream ->
            val files = stream.filter { Files.isRegularFile(it) }.sorted().toList()
            for (file in files) {
                val filename = file.fileName.toString()
                if (!filename.endsWith(".kt") && !filename.endsWith(".java")) continue
                val widget = loadFile(file, filename, loader)
                if (widget != null) found.add(widget)
            }
        }
        return found
    }

    private fun loadFile(file: Path, filename: String, loader: ClassLoader): Widget? {
        val text = Files.readString(file)
        val stem = filename.substringBeforeLast('.')
        val packageMatch = PACKAGE_LINE.find(text)
        if (packageMatch == null) throw FireflyException("Widget file [$filename] has no package")
        val classMatch = CLASS_LINE.find(text)
        if (classMatch == null) throw FireflyException("Widget file [$filename] has no class")
        val className = classMatch.groupValues[1]
        if (className != stem) throw FireflyException("Widget file [$filename] does not declare [$stem]")
        val qualified = packageMatch.groupValues[1] + "." + className
        val type = try {
            Class.forName(qualified, true, loader)
        } catch (ex: ClassNotFoundException) {
            throw FireflyException("Widget file [$filename] cannot load [$qualified]")
        }
        if (!Widget::class.java.isAssignableFrom(type)) return null
        if (Modifier.isAbstract(type.modifiers)) return null
        try {
            val ctor = type.getDeclaredConstructor()
            ctor.isAccessible = true
            val created = ctor.newInstance()
            return created as Widget
        } catch (ex: NoSuchMethodException) {
            throw FireflyException("Widget [$qualified] has no no-arg constructor")
        } catch (ex: InvocationTargetException) {
            throw FireflyException("Widget [$qualified] failed to start: ${ex.targetException}")
        }
    }
}
