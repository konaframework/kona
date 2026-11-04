package org.konaframework.grind

import org.konaframework.core.Application
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path

/**
 * Programmatic grind entry — call commands from application code or tests.
 *
 * ```kotlin
 * Grind.call("list")
 * Grind.call("mail:send", listOf("1", "--queue=default"), registry)
 * Grind.callSilently("schedule:run", listOf("--base=$root"))
 * Grind.call("user:password", listOf("ada@example.com"), app = app)
 * ```
 */
object Grind {
    @JvmStatic
    fun call(name: String): Int =
        call(name, emptyList(), null, Path.of(".").toAbsolutePath().normalize(), Grind::class.java.classLoader, null)

    @JvmStatic
    fun call(name: String, args: List<String>): Int =
        call(name, args, null, Path.of(".").toAbsolutePath().normalize(), Grind::class.java.classLoader, null)

    @JvmStatic
    fun call(name: String, args: List<String>, registry: CommandRegistry?): Int =
        call(name, args, registry, Path.of(".").toAbsolutePath().normalize(), Grind::class.java.classLoader, null)

    @JvmStatic
    fun call(
        name: String,
        args: List<String>,
        registry: CommandRegistry?,
        basePath: Path,
    ): Int = call(name, args, registry, basePath, Grind::class.java.classLoader, null)

    @JvmStatic
    fun call(
        name: String,
        args: List<String>,
        registry: CommandRegistry?,
        basePath: Path,
        loader: ClassLoader,
    ): Int = call(name, args, registry, basePath, loader, null)

    /**
     * When [app] is set, runs the custom command against that booted application (no AppLoader).
     * When [registry] already knows [name], runs via [AppCommandRunner] (boots when a project root exists).
     * Otherwise delegates to [GrindApplication] (built-ins + META-INF discovery).
     */
    @JvmStatic
    fun call(
        name: String,
        args: List<String>,
        registry: CommandRegistry?,
        basePath: Path,
        loader: ClassLoader,
        app: Application?,
    ): Int {
        val merged = CommandRegistry()
        if (registry != null) {
            merged.merge(registry)
        }
        CommandDiscovery.registerAll(merged, loader)
        if (app != null) {
            return AppCommandRunner.runWithApp(app, name, args, merged)
        }
        if (merged.has(name)) {
            val options = linkedMapOf("base" to basePath.toAbsolutePath().normalize().toString())
            val io = GrindIO(color = GrindIO.detectColor())
            return AppCommandRunner.run(name, args, options, merged, basePath, io)
        }
        return GrindApplication(merged, basePath, loader).run((listOf(name) + args).toTypedArray())
    }

    /** Like [call] but suppresses stdout (stderr still surfaces errors). */
    @JvmStatic
    fun callSilently(name: String): Int =
        callSilently(name, emptyList(), null, Path.of(".").toAbsolutePath().normalize(), Grind::class.java.classLoader)

    @JvmStatic
    fun callSilently(
        name: String,
        args: List<String>,
        registry: CommandRegistry?,
        basePath: Path,
        loader: ClassLoader,
    ): Int {
        val originalOut = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        return try {
            call(name, args, registry, basePath, loader, null)
        } finally {
            System.setOut(originalOut)
        }
    }

    @JvmStatic
    fun callSilently(name: String, args: List<String>): Int =
        callSilently(name, args, null, Path.of(".").toAbsolutePath().normalize(), Grind::class.java.classLoader)

    @JvmStatic
    fun callSilently(name: String, args: List<String>, registry: CommandRegistry?): Int =
        callSilently(name, args, registry, Path.of(".").toAbsolutePath().normalize(), Grind::class.java.classLoader)

    @JvmStatic
    fun callSilently(name: String, args: List<String>, registry: CommandRegistry?, basePath: Path): Int =
        callSilently(name, args, registry, basePath, Grind::class.java.classLoader)
}
