package org.konaframework.grind

/**
 * `grind list` — print available commands (built-ins + registered custom commands).
 */
class ListCommand(
    private val registry: CommandRegistry = CommandRegistry(),
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        io.intro("grind")
        io.title("Available commands")
        val seen = linkedSetOf<String>()
        (BUILTIN + registry.summaries()).forEach { (name, summary) ->
            if (seen.add(name)) {
                io.twoColumn(name, summary)
            }
        }
        io.outro("grind <command> --help")
        return 0
    }

    companion object {
        /** Built-in grind commands shown in `list` / help. */
        val BUILTIN: List<Pair<String, String>> = listOf(
            "install" to "Add a package dependency and register providers",
            "serve" to "Run the HTTP server (watch; --no-watch for Gradle run)",
            "route:list" to "List registered routes [--json] [--method=] [--name=] [--path=]",
            "route:cache" to "Write a JSON snapshot of registered routes",
            "route:clear" to "Remove the route cache file",
            "make:command" to "Generate a console command",
            "make:controller" to "Generate a controller (--resource / --api / --requests)",
            "make:resource" to "Generate an API resource (--collection)",
            "make:middleware" to "Generate HTTP middleware",
            "make:policy" to "Generate an authorization policy",
            "make:request" to "Generate a form request",
            "make:view" to "Generate a Crema view",
            "make:firefly-layout" to "Generate a Firefly layout component",
            "make:firefly-action" to "Generate a Firefly action",
            "make:firefly-entry" to "Generate a Firefly infolist entry",
            "make:firefly-column" to "Generate a Firefly table column",
            "make:firefly-filter" to "Generate a Firefly table filter",
            "make:firefly-field" to "Generate a Firefly form field",
            "make:firefly-widget" to "Generate a Firefly widget",
            "make:firefly-panel" to "Generate a Firefly panel and its pages, widgets, and resources folders",
            "make:firefly-page" to "Generate a Firefly page",
            "make:firefly-theme" to "Generate a Firefly theme",
            "make:firefly-resource" to "Generate a Firefly resource (--simple for one Manage page)",
            "firefly:install" to "Generate a Firefly panel tree",
            "firefly:publish" to "Publish the Firefly sidebar partial",
            "make:model" to "Generate a Cadence model (-mcrfspaR companions)",
            "make:migration" to "Generate a migration (auto-registers)",
            "make:factory" to "Generate a factory",
            "make:seeder" to "Generate a seeder",
            "migrate" to "Run pending migrations (--seed)",
            "migrate:fresh" to "Drop all tables and re-run migrations (--seed)",
            "migrate:refresh" to "Reset and re-run migrations (--seed)",
            "migrate:rollback" to "Rollback migrations",
            "migrate:reset" to "Reset all migrations",
            "migrate:install" to "Create the migration bookkeeping table",
            "migrate:status" to "Show migration status",
            "db:wipe" to "Drop all tables (--drop-views)",
            "db:seed" to "Run database seeders",
            "db:show" to "Show connection info and tables",
            "db:table" to "Show columns for a table",
            "schema:dump" to "Write CREATE TABLE SQL to database/schema.sql",
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
            "deploy:systemd" to "Build a systemd unit, env file, and install.sh",
        ) + GradleAliasCommand.LIST_ENTRIES + listOf(
            "down" to "Put the application into maintenance mode",
            "up" to "Bring the application out of maintenance mode",
            "key:generate" to "Set the application key (APP_KEY) [--show] [--force]",
            "ide:lsp" to "Print kona-lsp (Crema language server) setup help",
            "list" to "List grind commands",
            "help" to "Show usage",
        )

        /** Alias for existing tests. */
        val COMMANDS: List<Pair<String, String>> = BUILTIN
    }
}
