package org.konaframework.http

import org.konaframework.core.Application
import java.nio.file.Files
import java.nio.file.Path

/**
 * Returns 503 when the application is in maintenance mode (Laravel
 * `PreventRequestsDuringMaintenance`).
 *
 * The `file` driver (`app.maintenance.driver`) looks for
 * `{base}/storage/framework/down`, written by `./grind down`.
 */
class PreventRequestsDuringMaintenance(
    private val basePath: Path,
    private val driver: String = "file",
) : Middleware {
    constructor(app: Application) : this(
        app.basePath,
        app.config.getString("app.maintenance.driver", "file"),
    )

    override fun handle(request: Request, next: Handler): Response {
        if (!active()) {
            return next.handle(request)
        }
        return Response.html("<h1>Service Unavailable</h1>", 503)
            .header("Retry-After", "60")
    }

    internal fun active(): Boolean {
        if (!driver.equals("file", ignoreCase = true) && driver.isNotBlank()) {
            return false
        }
        return Files.isRegularFile(downFile(basePath))
    }

    companion object {
        fun downFile(basePath: Path): Path =
            basePath.resolve("storage").resolve("framework").resolve("down")
    }
}
