package org.konaframework.grind

import org.konaframework.http.PreventRequestsDuringMaintenance
import java.nio.file.Files
import java.nio.file.Path

/**
 * `grind down` — put the application into maintenance mode (Laravel `artisan down`).
 *
 * Writes `storage/framework/down` so [PreventRequestsDuringMaintenance] returns 503.
 */
class DownCommand @JvmOverloads constructor(
    private val workingDirectory: Path = Path.of(".").toAbsolutePath().normalize(),
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        val base = if (options.containsKey("base")) {
            Path.of(options.getValue("base")).toAbsolutePath().normalize()
        } else {
            workingDirectory
        }
        val down = PreventRequestsDuringMaintenance.downFile(base)
        Files.createDirectories(down.parent)
        Files.writeString(down, "{}\n")
        io.success("Application is now in maintenance mode.")
        return 0
    }
}

/**
 * `grind up` — bring the application out of maintenance mode (Laravel `artisan up`).
 */
class UpCommand @JvmOverloads constructor(
    private val workingDirectory: Path = Path.of(".").toAbsolutePath().normalize(),
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        val base = if (options.containsKey("base")) {
            Path.of(options.getValue("base")).toAbsolutePath().normalize()
        } else {
            workingDirectory
        }
        val down = PreventRequestsDuringMaintenance.downFile(base)
        if (Files.deleteIfExists(down)) {
            io.success("Application is now live.")
        } else {
            io.info("Application is already live.")
        }
        return 0
    }
}
