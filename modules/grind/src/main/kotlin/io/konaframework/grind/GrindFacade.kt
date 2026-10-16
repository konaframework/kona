package io.konaframework.grind

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
 * ```
 */
object Grind {
    @JvmStatic
    fun call(name: String): Int =
        call(name, emptyList(), null, Path.of(".").toAbsolutePath().normalize(), Grind::class.java.classLoader)

    @JvmStatic
    fun call(name: String, args: List<String>): Int =
        call(name, args, null, Path.of(".").toAbsolutePath().normalize(), Grind::class.java.classLoader)

    @JvmStatic
    fun call(name: String, args: List<String>, registry: CommandRegistry?): Int =
        call(name, args, registry, Path.of(".").toAbsolutePath().normalize(), Grind::class.java.classLoader)

    @JvmStatic
    fun call(
        name: String,
        args: List<String>,
        registry: CommandRegistry?,
        basePath: Path,
    ): Int = call(name, args, registry, basePath, Grind::class.java.classLoader)

    @JvmStatic
    fun call(
        name: String,
        args: List<String>,
        registry: CommandRegistry?,
        basePath: Path,
        loader: ClassLoader,
    ): Int {
        val merged = CommandRegistry()
        if (registry != null) {
            merged.merge(registry)
        }
        CommandDiscovery.registerAll(merged, loader)
        if (merged.has(name)) {
            val command = merged.create(name)!!
            return CommandExecutor(basePath).run(command, args)
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
            call(name, args, registry, basePath, loader)
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
