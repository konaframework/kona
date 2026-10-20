package org.konaframework.grind

import org.konaframework.http.Router
import org.konaframework.support.json.Json
import java.nio.file.Files

/**
 * `grind route:list`, `route:cache`, `route:clear`.
 *
 * `route:list` boots the application in-process and prints the live [Router] table
 * (`--json`, `--method=`, `--name=`, `--path=`, `--except-path=`, `--except-vendor`,
 * `--only-vendor`, `--reverse`, `--sort=`, `--columns=`, `--compact`).
 *
 * `route:cache` writes a JSON snapshot to [DEFAULT_CACHE_PATH] (HTTP dispatch still uses the
 * live router — JVM class files already compile route registration). `route:clear` removes that file.
 */
class RouteCommand(private val action: Action) {
    enum class Action { LIST, CACHE, CLEAR }

    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        return when (action) {
            Action.LIST -> listRoutes(options, io)
            Action.CACHE -> cacheRoutes(options, io)
            Action.CLEAR -> clearCache(options, io)
        }
    }

    private fun listRoutes(options: Map<String, String>, io: GrindIO): Int {
        val rows = loadRows(options)
        if (MigrateCommand.wantsFlag(options, "json")) {
            io.writeln(RouteListing.toJson(rows))
            return 0
        }
        if (rows.isEmpty()) {
            io.warn(emptyMessage(options))
            return 0
        }
        val columns = RouteListing.columns(options)
        io.title("Routes")
        io.table(
            columns.map { RouteListing.header(it) },
            rows.map { row ->
                columns.map { column ->
                    if (column == "method") RouteListing.paintMethods(io, row.method) else row.column(column)
                }
            },
        )
        io.outro("Showing ${rows.size} route(s)")
        return 0
    }

    private fun cacheRoutes(options: Map<String, String>, io: GrindIO): Int {
        val start = Names.base(options)
        val root = ServeCommand.findProjectRoot(start)
            ?: throw GrindException("No Kona application found at $start. Create one with: kona new")
        val rows = loadRows(options)
        val dest = root.resolve(DEFAULT_CACHE_PATH)
        Files.createDirectories(dest.parent)
        val json = Json.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows.map { it.toMap() })
        Files.writeString(dest, json + "\n")
        io.info("Routes cached successfully.")
        return 0
    }

    private fun clearCache(options: Map<String, String>, io: GrindIO): Int {
        val start = Names.base(options)
        val root = ServeCommand.findProjectRoot(start)
            ?: throw GrindException("No Kona application found at $start. Create one with: kona new")
        Files.deleteIfExists(root.resolve(DEFAULT_CACHE_PATH))
        io.info("Route cache cleared successfully.")
        return 0
    }

    private fun loadRows(options: Map<String, String>): List<RouteListing.Row> {
        var table = emptyList<RouteListing.Row>()
        MigrateCommand.withApplication(options) { app, _ ->
            if (!app.container.bound(Router::class.java)) {
                throw GrindException(
                    "No Router bound. Register HttpServiceProvider " +
                        "(app.register(HttpServiceProvider()) or include it in Kona.create().providers()).",
                )
            }
            val router = app.resolve(Router::class.java)
            table = RouteListing.sort(RouteListing.filter(RouteListing.rows(router), options), options)
        }
        return table
    }

    private fun emptyMessage(options: Map<String, String>): String {
        val filtered = RouteListing.csv(options, "method").isNotEmpty() ||
            RouteListing.csv(options, "name").isNotEmpty() ||
            RouteListing.csv(options, "path").isNotEmpty() ||
            RouteListing.csv(options, "except-path").isNotEmpty() ||
            MigrateCommand.wantsFlag(options, "except-vendor") ||
            MigrateCommand.wantsFlag(options, "only-vendor")
        return if (filtered) {
            "Your application doesn't have any routes matching the given criteria."
        } else {
            "Your application doesn't have any routes."
        }
    }

    companion object {
        const val DEFAULT_CACHE_PATH = "bootstrap/cache/routes.json"
    }
}
