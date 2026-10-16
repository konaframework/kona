package dev.kona.grind

/**
 * `grind list` — print available commands (built-ins + registered custom commands).
 */
class ListCommand(
    private val registry: CommandRegistry = CommandRegistry(),
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        io.title("grind commands")
        val seen = linkedSetOf<String>()
        (BUILTIN + registry.summaries()).forEach { (name, summary) ->
            if (seen.add(name)) {
                io.twoColumn(name, summary)
            }
        }
        return 0
    }

    companion object {
        /** Built-in grind commands shown in `list` / help. */
        val BUILTIN: List<Pair<String, String>> = listOf(
            "serve" to "Run the HTTP server",
            "make:command" to "Generate a console command",
            "make:controller" to "Generate a controller (--resource for CRUD stubs)",
            "make:resource" to "Generate an API resource (--collection)",
            "make:middleware" to "Generate HTTP middleware",
            "make:policy" to "Generate an authorization policy",
            "make:request" to "Generate a form request",
            "make:view" to "Generate a Crema view",
            "make:model" to "Generate a Cadence model",
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
            "schedule:work" to "Run the scheduler in a loop",
            "steam" to "Interactive REPL",
            "assets:vite" to "Print Vite asset bundling setup help",
            "storage:link" to "Symlink public/storage to storage/app/public",
            "key:generate" to "Set the application key (APP_KEY) [--show] [--force]",
            "ide:lsp" to "Print kona-lsp (Crema language server) setup help",
            "list" to "List grind commands",
            "help" to "Show usage",
        )

        /** Alias for existing tests. */
        val COMMANDS: List<Pair<String, String>> = BUILTIN
    }
}
