package dev.kona.grind

/**
 * `grind list` — print available commands (console depth / help parity).
 */
class ListCommand {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        io.title("grind commands")
        COMMANDS.forEach { (name, summary) ->
            io.twoColumn(name, summary)
        }
        return 0
    }

    companion object {
        val COMMANDS: List<Pair<String, String>> = listOf(
            "serve" to "Run the HTTP server",
            "make:controller" to "Generate a controller (--resource for CRUD stubs)",
            "make:middleware" to "Generate HTTP middleware",
            "make:request" to "Generate a form request",
            "make:view" to "Generate a Crema view",
            "make:model" to "Generate an Extract model",
            "make:migration" to "Generate a migration",
            "make:factory" to "Generate a factory",
            "make:seeder" to "Generate a seeder",
            "migrate" to "Run pending migrations",
            "migrate:rollback" to "Rollback migrations",
            "migrate:reset" to "Reset all migrations",
            "migrate:status" to "Show migration status",
            "db:seed" to "Run database seeders",
            "queue:work" to "Process queued jobs",
            "queue:failed" to "List failed jobs",
            "queue:retry" to "Forget/retry failed jobs",
            "queue:flush" to "Clear failed jobs",
            "schedule:run" to "Run due scheduled tasks",
            "schedule:list" to "List scheduled tasks",
            "steam" to "Interactive REPL",
            "assets:vite" to "Print Vite asset bundling setup help",
            "storage:link" to "Symlink public/storage to storage/app/public",
            "key:generate" to "Set the application key (APP_KEY) [--show] [--force]",
            "ide:lsp" to "Print kona-lsp (Crema language server) setup help",
            "list" to "List grind commands",
            "help" to "Show usage",
        )
    }
}
