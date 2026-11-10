package org.konaframework.firefly.support

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Uncompiled theme stylesheets live under `src/main/resources`.
 * [asset] names one. When `public/` has the compiled file, the render links
 * that URL. When it does not, the render feeds [uncompiledCss] to the Tailwind
 * runtime instead of failing. [ThemeCssCompiler] writes the compiled file.
 */
object ThemeAssets {
    const val PREFIX: String = "ff-asset:"
    const val COMMAND: String = "./grind firefly:theme-css"

    /** App root that contains `public/`. Tests point this at a temp directory. */
    @Volatile
    @JvmStatic
    var root: Path = Path.of("").toAbsolutePath()

    /** Token [FireflyTheme.stylesheet] returns for an uncompiled resources path. */
    @JvmStatic
    fun asset(path: String): String = PREFIX + normalize(path)

    @JvmStatic
    fun isCompiledAsset(stylesheet: String?): Boolean {
        if (stylesheet == null) return false
        return stylesheet.trim().startsWith(PREFIX)
    }

    /** True when `public/` already has the compiled file for an [asset] token. */
    @JvmStatic
    fun hasCompiledFile(stylesheet: String): Boolean =
        Files.isRegularFile(compiledPath(stylesheet))

    /**
     * Public URL for an [asset] token (`/css/foo.css`).
     * Does not check that the compiled file exists. The head resolver links this
     * only after [hasCompiledFile].
     */
    @JvmStatic
    fun publicUrl(stylesheet: String): String = "/${normalize(stylesheet)}"

    /**
     * Uncompiled source for an [asset] token.
     * `css/foo.css` is `src/main/resources/css/foo.css`. Empty when that file is absent.
     */
    @JvmStatic
    fun uncompiledCss(stylesheet: String): String {
        val relative = normalize(stylesheet)
        val file = root.toAbsolutePath().normalize().resolve("src/main/resources").resolve(relative)
        if (!Files.isRegularFile(file)) return ""
        return Files.readString(file)
    }

    private fun compiledPath(stylesheet: String): Path {
        val relative = normalize(stylesheet)
        return root.toAbsolutePath().normalize().resolve("public").resolve(relative)
    }

    internal fun normalize(path: String): String {
        var value = path.trim()
        if (value.startsWith(PREFIX)) value = value.removePrefix(PREFIX).trim()
        value = value.trimStart('/')
        if (value.startsWith("src/main/resources/")) {
            value = value.removePrefix("src/main/resources/").trimStart('/')
        }
        if (value.isEmpty() || value.contains("..") || value.contains('\\') || value.contains("://")) {
            throw FireflyException("Theme asset [$path] is not a stylesheet path under resources")
        }
        return value
    }
}

/**
 * Compiles theme stylesheets with the Tailwind CLI that ships next to
 * `modules/firefly-support/tailwind` (`@tailwindcss/cli` 4.3.5, the same
 * version as the browser runtime). One invocation, not a per-request build.
 */
object ThemeCssCompiler {
    const val VERSION: String = "4.3.5"
    const val MARKER: String = "firefly-theme"

    @Volatile
    @JvmStatic
    var nodeBinary: String = "node"

    @Volatile
    @JvmStatic
    var npmBinary: String = "npm"

    /** When set, this directory is `node_modules` and discovery stops. */
    @Volatile
    @JvmStatic
    var moduleOverride: Path? = null

    @Volatile
    @JvmStatic
    var cacheDir: Path = Path.of(System.getProperty("user.home"), ".cache", "kona", "tailwind-$VERSION")

    @JvmStatic
    fun reset() {
        nodeBinary = "node"
        npmBinary = "npm"
        moduleOverride = null
        cacheDir = Path.of(System.getProperty("user.home"), ".cache", "kona", "tailwind-$VERSION")
    }

    /** Compiles every marked theme stylesheet, or the one [name] selects. */
    @JvmStatic
    fun compileThemes(base: Path, name: String?): List<Path> {
        val sources = themeSources(base, name)
        val written = ArrayList<Path>()
        for (source in sources) {
            val output = base.resolve("public/css").resolve(source.fileName.toString())
            compileSource(source, output)
            written.add(output)
        }
        return written
    }

    internal fun themeSources(base: Path, name: String?): List<Path> {
        val dir = base.resolve("src/main/resources/css")
        if (name != null) {
            val slug = themeSlug(name)
            val file = dir.resolve("$slug.css")
            if (!Files.isRegularFile(file)) {
                throw FireflyException(
                    "Missing theme stylesheet src/main/resources/css/$slug.css. " +
                        "Run ./grind make:firefly-theme, then ${ThemeAssets.COMMAND}",
                )
            }
            return listOf(file)
        }
        if (!Files.isDirectory(dir)) {
            throw FireflyException(
                "No Firefly theme stylesheets under src/main/resources/css. " +
                    "Run ./grind make:firefly-theme, then ${ThemeAssets.COMMAND}",
            )
        }
        val found = ArrayList<Path>()
        Files.list(dir).use { stream ->
            stream.forEach { path ->
                val filename = path.fileName.toString()
                if (!Files.isRegularFile(path) || !filename.endsWith(".css")) return@forEach
                if (Files.readString(path).contains(MARKER)) found.add(path)
            }
        }
        found.sortBy { it.fileName.toString() }
        if (found.isEmpty()) {
            throw FireflyException(
                "No Firefly theme stylesheets under src/main/resources/css. " +
                    "Mark the file with /* $MARKER */ or run ./grind make:firefly-theme, then ${ThemeAssets.COMMAND}",
            )
        }
        return found
    }

    internal fun themeSlug(name: String): String {
        var value = name.trim()
        if (value.endsWith(".css", ignoreCase = true)) value = value.dropLast(4)
        value = value.lowercase()
        if (!SLUG.matches(value)) {
            throw FireflyException(
                "Theme stylesheet name [$name] is not a file slug. " +
                    "Run ${ThemeAssets.COMMAND} with the theme id, for example midnighttheme",
            )
        }
        return value
    }

    internal fun compileSource(source: Path, output: Path) {
        compileSource(source, output, ::runNode)
    }

    internal fun compileSource(source: Path, output: Path, run: (List<String>, Path) -> Unit) {
        val modules = tailwindModules()
        val cli = modules.resolve("@tailwindcss/cli/dist/index.mjs")
        if (!Files.isRegularFile(cli)) {
            throw FireflyException(
                "Tailwind CLI $VERSION is missing at $cli. Run ${ThemeAssets.COMMAND} once Node.js can install it",
            )
        }
        val home = modules.toAbsolutePath().resolve("..").normalize()
        val input = Files.createTempFile(home, "theme-input-", ".css")
        val compiled = Files.createTempFile(home, "theme-output-", ".css")
        try {
            Files.writeString(input, themeCompileSource(Files.readString(source)))
            run(
                listOf(
                    nodeBinary,
                    cli.toString(),
                    "--input",
                    input.toString(),
                    "--output",
                    compiled.toString(),
                    "--cwd",
                    home.toString(),
                    "--silent",
                ),
                home,
            )
            if (!Files.isRegularFile(compiled)) {
                throw FireflyException(
                    "Theme CSS compile wrote no output for ${source.fileName}. Run ${ThemeAssets.COMMAND}",
                )
            }
            val directory = output.toAbsolutePath().parent
            if (directory == null) {
                throw FireflyException("Theme CSS output [$output] has no directory. Run ${ThemeAssets.COMMAND}")
            }
            Files.createDirectories(directory)
            Files.writeString(output, finishCompiledCss(Files.readString(compiled)))
        } finally {
            Files.deleteIfExists(input)
            Files.deleteIfExists(compiled)
        }
    }

    internal fun tailwindModules(): Path =
        locateTailwindModules(moduleOverride, Path.of("").toAbsolutePath(), cacheDir, ::npmInstall)

    internal fun locateTailwindModules(
        overrideDir: Path?,
        walkFrom: Path?,
        cache: Path,
        install: (Path) -> Unit,
    ): Path {
        if (overrideDir != null) {
            if (validModules(overrideDir)) return overrideDir
            throw FireflyException(
                "Tailwind modules [$overrideDir] do not contain @tailwindcss/cli@$VERSION. Run ${ThemeAssets.COMMAND}",
            )
        }
        if (walkFrom != null) {
            var dir: Path? = walkFrom.toAbsolutePath()
            var steps = 0
            while (dir != null && steps < 8) {
                val candidate = dir.resolve("modules/firefly-support/tailwind/node_modules")
                if (validModules(candidate)) return candidate
                dir = dir.parent
                steps++
            }
        }
        val cached = cache.resolve("node_modules")
        if (validModules(cached)) return cached
        try {
            install(cache)
        } catch (ex: FireflyException) {
            throw FireflyException(
                "Could not install @tailwindcss/cli@$VERSION. ${ex.message} Run ${ThemeAssets.COMMAND} when Node.js is available",
            )
        }
        if (!validModules(cached)) {
            throw FireflyException(
                "Could not install @tailwindcss/cli@$VERSION. Run ${ThemeAssets.COMMAND} when Node.js is available",
            )
        }
        return cached
    }

    internal fun npmInstall(cache: Path) {
        Files.createDirectories(cache)
        runChecked(
            listOf(npmBinary, "install", "--prefix", cache.toAbsolutePath().toString(), "@tailwindcss/cli@$VERSION"),
            cache,
            180,
            "Tailwind install failed",
        )
    }

    internal fun runNode(command: List<String>, cwd: Path) {
        runChecked(command, cwd, 60, "Theme CSS compile failed")
    }

    internal fun runChecked(command: List<String>, cwd: Path, timeoutSeconds: Long, failure: String) {
        val log = Files.createTempFile("kona-theme-css", ".log")
        try {
            val proc = try {
                ProcessBuilder(command)
                    .directory(cwd.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                    .start()
            } catch (ex: IOException) {
                throw FireflyException("$failure. Node.js is required for Tailwind $VERSION. ${ex.message}")
            }
            val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                proc.waitFor(5, TimeUnit.SECONDS)
                throw FireflyException("$failure timed out. Run ${ThemeAssets.COMMAND}")
            }
            val code = proc.exitValue()
            if (code != 0) {
                val detail = Files.readString(log).trim()
                throw FireflyException("$failure ($code). ${detail.take(500)}")
            }
        } finally {
            Files.deleteIfExists(log)
        }
    }

    private fun validModules(dir: Path): Boolean = Files.isDirectory(dir.resolve("@tailwindcss/cli"))

    private val SLUG = Regex("^[a-z][a-z0-9_-]*$")
}
