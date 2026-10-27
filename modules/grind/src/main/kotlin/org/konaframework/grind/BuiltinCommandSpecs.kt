package org.konaframework.grind

/**
 * Signatures for built-in grind commands — used for `--help` / `-h` and to reject
 * unknown options / stray positionals before the command body runs.
 *
 * Gradle aliases are intentionally absent: they forward unknown flags to Gradle.
 */
internal object BuiltinCommandSpecs {
    private val DESCRIPTIONS: Map<String, String> =
        ListCommand.BUILTIN.associate { it.first to it.second }

    private val RAW: List<String> = listOf(
        "install {package} {--version=} {--frontend=} {--no-interactive} {--lang=}",
        "serve {--host=} {--port=} {--watch} {--no-watch} {--in-process} {--once} {--bootstrap=}",
        "route:list {--json} {--method=} {--name=} {--path=} {--except-path=} {--except-vendor} {--only-vendor} {--sort=} {--reverse} {--compact} {--columns=} {--bootstrap=}",
        "route:cache {--bootstrap=}",
        "route:clear",
        "make:command {name} {--command=} {--lang=}",
        "make:controller {name} {--resource} {--api} {--requests} {--model=} {--lang=}",
        "make:resource {name} {--collection} {--lang=}",
        "make:middleware {name} {--lang=}",
        "make:policy {name} {--model=} {--lang=}",
        "make:request {name} {--lang=}",
        "make:view {name}",
        "make:firefly-layout {name} {--lang=}",
        "make:firefly-action {name} {--lang=}",
        "make:firefly-entry {name} {--lang=}",
        "make:firefly-column {name} {--lang=}",
        "make:firefly-filter {name} {--lang=}",
        "make:firefly-field {name} {--lang=}",
        "make:firefly-widget {name} {--panel=} {--lang=}",
        "make:firefly-panel {name?} {--id=} {--path=} {--lang=}",
        "make:firefly-page {name} {--panel=} {--lang=}",
        "make:firefly-theme {name} {--lang=}",
        "make:firefly-resource {name} {--simple} {--panel=} {--lang=}",
        "firefly:install {--id=} {--path=} {--lang=}",
        "firefly:publish",
        "make:model {name} {--migration} {--controller} {--resource} {--api} {--requests} {--factory} {--seeder} {--seed} {--policy} {--all} {--lang=}",
        "make:migration {name} {--create=} {--table=} {--lang=}",
        "make:factory {name} {--model=} {--lang=}",
        "make:seeder {name} {--lang=}",
        "migrate {--database=} {--seed} {--seeder=} {--class=} {--force} {--bootstrap=}",
        "migrate:fresh {--database=} {--seed} {--seeder=} {--class=} {--force} {--drop-views} {--drop-types} {--bootstrap=}",
        "migrate:refresh {--database=} {--seed} {--seeder=} {--class=} {--force} {--step=} {--bootstrap=}",
        "migrate:rollback {--database=} {--seed} {--seeder=} {--class=} {--step=} {--force} {--bootstrap=}",
        "migrate:reset {--database=} {--seed} {--seeder=} {--class=} {--force} {--bootstrap=}",
        "migrate:install {--database=} {--bootstrap=}",
        "migrate:status {--database=} {--bootstrap=}",
        "db:wipe {--database=} {--drop-views} {--drop-types} {--force} {--bootstrap=}",
        "db:seed {--class=} {--database=} {--seed} {--seeder=} {--force} {--bootstrap=}",
        "db:show {--database=} {--bootstrap=}",
        "db:table {name} {--database=} {--bootstrap=}",
        "schema:dump {--path=} {--database=} {--bootstrap=}",
        "queue:work {--connection=} {--queue=} {--max=} {--once} {--stop-when-empty} {--timeout=} {--memory=} {--tries=} {--backoff=} {--sleep=} {--daemon} {--bootstrap=}",
        "queue:failed {--bootstrap=}",
        "queue:retry {id?} {--all} {--bootstrap=}",
        "queue:flush {--bootstrap=}",
        "schedule:run {--bootstrap=}",
        "schedule:list {--bootstrap=}",
        "schedule:work {--max=} {--sleep=} {--bootstrap=}",
        "steam {--bootstrap=}",
        "assets:vite",
        "storage:link",
        "deploy:systemd {--skip-build} {--force} {--output=} {--prefix=} {--user=} {--group=} {--host=} {--port=} {--url=} {--name=} {--java-opts=}",
        "down",
        "up",
        "key:generate {--show} {--force}",
        "ide:lsp",
        "list",
    )

    val ALL: Map<String, CommandSignature> =
        RAW.associate { raw ->
            val name = raw.substringBefore(' ')
            val parsed = SignatureParser.parse(raw, descriptionFor(name))
            parsed.name to parsed
        }

    /** Resolves the list-command blurb for a built-in; empty when unknown (tests cover that branch). */
    internal fun descriptionFor(name: String): String = DESCRIPTIONS[name].orEmpty()

    operator fun get(name: String): CommandSignature? = ALL[name]
}

/** Shared `--help` rendering for built-in and custom grind commands. */
internal object CommandHelp {
    fun print(io: GrindIO, signature: CommandSignature) {
        val heading = if (signature.description.isEmpty()) signature.name else signature.description
        io.intro("grind")
        io.box(signature.name, listOf(heading))
        io.title("Usage")
        val rendered = buildString {
            append("grind ")
            append(signature.name)
            signature.arguments.forEach { append(" {").append(formatArg(it)).append('}') }
            signature.options.forEach { append(" {").append(formatOpt(it)).append('}') }
        }
        io.writeln(rendered)
        signature.helpLines().forEach { io.writeln(it) }
        io.writeln()
        io.info("Global: --verbose|-v  --quiet|-q  --help|-h  --base=")
    }

    private fun formatArg(arg: ArgumentDefinition): String =
        buildString {
            append(arg.name)
            when {
                arg.isArray -> append('*')
                arg.optional -> append('?')
            }
        }

    private fun formatOpt(opt: OptionDefinition): String =
        buildString {
            append("--")
            append(opt.name)
            if (opt.acceptsValue) {
                append('=')
                if (opt.default != null) append(opt.default)
            }
        }
}
