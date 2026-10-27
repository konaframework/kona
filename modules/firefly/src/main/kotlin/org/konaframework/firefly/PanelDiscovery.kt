package org.konaframework.firefly

import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.widgets.Widget
import org.konaframework.firefly.widgets.WidgetDiscovery
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads pages, resources, and widgets from a panel directory.
 * A missing folder is empty. Explicit registration still works.
 *
 * Panel scanning walks [directory] but never descends into [PANEL_CHILD_FOLDERS]
 * (`pages/`, `resources/`, `widgets/`) — those are loaded only via [pages],
 * [resources], and [widgets] after a panel adopts its home directory.
 */
object PanelDiscovery {
    /** Child folders of a panel home; never treated as panel class locations. */
    private val PANEL_CHILD_FOLDERS = setOf("pages", "resources", "widgets")

    private val PACKAGE_LINE = Regex("""(?:^|\n)\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
    private val CLASS_LINE = Regex(
        """(?:^|\n)\s*(?:public\s+|open\s+|internal\s+|final\s+|abstract\s+)*class\s+([A-Za-z_][A-Za-z0-9_]*)""",
    )

    @JvmStatic
    fun pages(panelDirectory: Path): List<PanelPage> = pages(panelDirectory, Thread.currentThread().contextClassLoader)

    @JvmStatic
    fun pages(panelDirectory: Path, loader: ClassLoader): List<PanelPage> =
        load(panelDirectory, "pages", loader, PanelPage::class.java)

    @JvmStatic
    fun resources(panelDirectory: Path): List<Resource> =
        resources(panelDirectory, Thread.currentThread().contextClassLoader)

    @JvmStatic
    fun resources(panelDirectory: Path, loader: ClassLoader): List<Resource> =
        load(panelDirectory, "resources", loader, Resource::class.java)

    @JvmStatic
    fun widgets(panelDirectory: Path): List<Widget> = WidgetDiscovery.discover(panelDirectory)

    /**
     * Panel classes under [directory], including nested packages.
     * Skips `pages/`, `resources/`, and `widgets/` so resource/page/widget
     * sources are never instantiated as panels.
     */
    @JvmStatic
    fun panels(directory: Path): List<Panel> = panels(directory, Thread.currentThread().contextClassLoader)

    @JvmStatic
    fun panels(directory: Path, loader: ClassLoader): List<Panel> {
        if (!Files.exists(directory)) return emptyList()
        return loadDirectory(directory, "panels", loader, Panel::class.java, skipPanelChildFolders = true)
    }

    private fun <T> load(panelDirectory: Path, folder: String, loader: ClassLoader, type: Class<T>): List<T> {
        val dir = panelDirectory.resolve(folder)
        if (!Files.exists(dir)) return emptyList()
        return loadDirectory(dir, folder, loader, type, skipPanelChildFolders = false)
    }

    private fun <T> loadDirectory(
        dir: Path,
        folder: String,
        loader: ClassLoader,
        type: Class<T>,
        skipPanelChildFolders: Boolean,
    ): List<T> {
        if (!Files.isDirectory(dir)) throw FireflyException("Panel $folder path is not a directory [$dir]")
        val found = ArrayList<T>()
        Files.walk(dir).use { stream ->
            val files = stream
                .filter { Files.isRegularFile(it) }
                .filter { !skipPanelChildFolders || !underPanelChildFolder(dir, it) }
                .sorted()
                .toList()
            for (file in files) {
                val filename = file.fileName.toString()
                if (!filename.endsWith(".kt") && !filename.endsWith(".java")) continue
                val created = loadFile(file, filename, folder, loader, type)
                if (created != null) found.add(created)
            }
        }
        return found
    }

    /** True when [file] lives under a reserved panel child (`pages` / `resources` / `widgets`). */
    private fun underPanelChildFolder(root: Path, file: Path): Boolean {
        val relative = root.relativize(file)
        val depth = relative.nameCount
        if (depth < 2) return false
        for (i in 0 until depth - 1) {
            if (relative.getName(i).toString() in PANEL_CHILD_FOLDERS) return true
        }
        return false
    }

    private fun <T> loadFile(file: Path, filename: String, folder: String, loader: ClassLoader, type: Class<T>): T? {
        val text = Files.readString(file)
        val stem = filename.substringBeforeLast('.')
        val packageMatch = PACKAGE_LINE.find(text)
        if (packageMatch == null) throw FireflyException("Panel $folder file [$filename] has no package")
        val classMatch = CLASS_LINE.find(text)
        if (classMatch == null) throw FireflyException("Panel $folder file [$filename] has no class")
        val className = classMatch.groupValues[1]
        if (className != stem) throw FireflyException("Panel $folder file [$filename] does not declare [$stem]")
        val qualified = packageMatch.groupValues[1] + "." + className
        val loaded = try {
            Class.forName(qualified, true, loader)
        } catch (ex: ClassNotFoundException) {
            throw FireflyException("Panel $folder file [$filename] cannot load [$qualified]")
        }
        if (!type.isAssignableFrom(loaded)) return null
        if (Modifier.isAbstract(loaded.modifiers)) return null
        try {
            val ctor = loaded.getDeclaredConstructor()
            ctor.isAccessible = true
            val created = ctor.newInstance()
            if (created is Panel) created.adoptHome(file.parent)
            return type.cast(created)
        } catch (ex: NoSuchMethodException) {
            throw FireflyException("Panel $folder [$qualified] has no no-arg constructor")
        } catch (ex: InvocationTargetException) {
            throw FireflyException("Panel $folder [$qualified] failed to start: ${ex.targetException}")
        }
    }
}
