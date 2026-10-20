package org.konaframework.cli

import org.konaframework.support.console.ConsoleIO
import java.nio.file.Path

/** Resolved `kona new` answers (flags + prompts) before any files are written. */
internal data class NewPlan(
    val directory: Path,
    val appName: String,
    val lang: String,
    val kit: String,
    val css: String,
    val frontend: String,
    val database: DatabaseEngines.Engine,
    val installDeps: Boolean,
    val migrate: Boolean,
    val seed: Boolean,
    val script: String = "js",
) {
    val stackLabel: String
        get() {
            val kitPart = when (kit) {
                "api" -> "API only"
                "brew" -> "Brew"
                "inertia" -> {
                    if (frontend.isEmpty()) {
                        "Inertia"
                    } else {
                        "Inertia · ${frontend[0].uppercaseChar()}${frontend.substring(1)}"
                    }
                }
                else -> "Crema"
            }
            if (kit == "api") return kitPart
            val cssPart = when (css) {
                "tailwind" -> "Tailwind CSS"
                "bootstrap" -> "Bootstrap"
                else -> "custom CSS"
            }
            val label = "$kitPart · $cssPart"
            return if (kit == "inertia" && script == "ts") "$label · TypeScript" else label
        }
}

internal object NewStacks {
    const val API = "API only"
    const val CREMA = "Crema (server-rendered HTML)"
    const val BREW = "Brew (reactive UI)"
    const val INERTIA = "Inertia (SPA)"
    val KITS = listOf(API, CREMA, BREW, INERTIA)

    const val CSS_TAILWIND = "Tailwind CSS"
    const val CSS_BOOTSTRAP = "Bootstrap"
    const val CSS_CUSTOM = "Custom CSS"
    val CSS = listOf(CSS_TAILWIND, CSS_BOOTSTRAP, CSS_CUSTOM)

    const val FE_VUE = "Vue"
    const val FE_REACT = "React"
    const val FE_SVELTE = "Svelte"
    val FRONTENDS = listOf(FE_VUE, FE_REACT, FE_SVELTE)

    const val SCRIPT_JS = "JavaScript"
    const val SCRIPT_TS = "TypeScript"
    val SCRIPTS = listOf(SCRIPT_JS, SCRIPT_TS)

    const val LANG_KOTLIN = "Kotlin"
    const val LANG_JAVA = "Java"
    val LANGUAGES = listOf(LANG_KOTLIN, LANG_JAVA)

    val KITS_IDS = listOf("web", "api", "crema", "inertia", "brew")

    fun kitId(label: String): String =
        when (label) {
            API -> "api"
            BREW -> "brew"
            INERTIA -> "inertia"
            else -> "web"
        }

    fun cssId(label: String): String =
        when (label) {
            CSS_TAILWIND -> "tailwind"
            CSS_BOOTSTRAP -> "bootstrap"
            else -> "custom"
        }

    fun frontendId(label: String): String =
        when (label) {
            FE_REACT -> "react"
            FE_SVELTE -> "svelte"
            else -> "vue"
        }

    fun scriptId(label: String): String = if (label == SCRIPT_TS) "ts" else "js"

    fun langId(label: String): String = if (label == LANG_JAVA) "java" else "kotlin"
}

internal fun titleCaseFolder(raw: String): String {
    val parts = raw.replace(Regex("[._]+"), "-")
        .split('-', ' ', '_')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (parts.isEmpty()) {
        return "Kona"
    }
    return parts.joinToString(" ") { part ->
        val first = part[0]
        val rest = part.substring(1)
        val head = if (first.isLowerCase()) first.uppercaseChar() else first
        "$head$rest"
    }
}

internal const val STDIN_UNREADABLE =
    "Cannot read stdin. Pass flags for each step, or use --no-interaction for defaults."

internal const val NEW_USAGE =
    "Usage: kona new [name] [--lang=kotlin|java] [--kit=web|api|crema|inertia|brew] " +
        "[--css=tailwind|bootstrap|custom] [--frontend=vue|react|svelte] " +
        "[--typescript|--javascript] " +
        "[--name=…] [--database=h2|sqlite|mysql|mariadb|pgsql|sqlsrv|oracle] " +
        "[--install-deps] [--migrate] [--seed] [--no-interaction] [--kona-version=x.y.z]"

/**
 * Full skip only for `--no-interaction` / `--no-interactive`.
 * `CI`, missing `System.console()`, and a positional directory do **not** skip the rest.
 */
internal fun wantsInteraction(options: Map<String, String>): Boolean =
    !options.containsKey("no-interaction") && !options.containsKey("no-interactive")

internal fun triState(options: Map<String, String>, name: String): Boolean? {
    if (options.containsKey("no-$name")) {
        return false
    }
    if (!options.containsKey(name)) {
        return null
    }
    val v = options.getValue(name).lowercase()
    return v !in setOf("false", "0", "no")
}

internal fun parseLangFlag(raw: String): String {
    val lang = raw.lowercase()
    if (lang != "kotlin" && lang != "java") {
        throw KonaCliException("--lang must be kotlin or java")
    }
    return lang
}

internal fun parseKitFlag(raw: String): String {
    val kit = raw.lowercase()
    if (kit !in NewStacks.KITS_IDS) {
        throw KonaCliException("--kit/--preset must be one of: ${NewStacks.KITS_IDS.joinToString(", ")}")
    }
    return kit
}

internal fun parseCssFlag(raw: String): String =
    when (raw.lowercase()) {
        "tailwind", "tw" -> "tailwind"
        "bootstrap", "bs" -> "bootstrap"
        "custom", "none", "vanilla", "plain" -> "custom"
        else -> throw KonaCliException("--css must be tailwind, bootstrap, or custom")
    }

internal fun parseFrontendFlag(raw: String): String {
    val id = raw.lowercase()
    if (id != "vue" && id != "react" && id != "svelte") {
        throw KonaCliException("--frontend must be vue, react, or svelte")
    }
    return id
}

/**
 * Inertia frontend script from `--typescript`/`--ts` and `--javascript`/`--js`.
 * Later flags win. `=false`, `=0`, and `=no` invert that flag.
 * Returns null when neither family was passed.
 */
internal fun frontendScriptChoice(options: Map<String, String>): String? {
    var choice: String? = null
    for ((key, raw) in options) {
        val enabled = flagOn(raw)
        when (key) {
            "typescript" -> choice = if (enabled) "ts" else "js"
            "ts" -> choice = if (enabled) "ts" else "js"
            "javascript" -> choice = if (enabled) "js" else "ts"
            "js" -> choice = if (enabled) "js" else "ts"
            else -> Unit
        }
    }
    return choice
}

internal fun flagOn(raw: String): Boolean = raw.lowercase() !in setOf("false", "0", "no")

internal fun resolveNewPlan(
    positional: List<String>,
    options: Map<String, String>,
    canPrompt: Boolean,
    prompts: InstallerPrompts,
    io: ConsoleIO,
): NewPlan? {
    if (!canPrompt && positional.isEmpty()) {
        throw KonaCliException(
            "$NEW_USAGE\nA directory argument skips only that question. Scripts: --no-interaction.",
        )
    }
    if (canPrompt) {
        io.intro("kona new")
        io.box(
            "Let's brew an app",
            listOf(
                "A few questions, then we write the project.",
                "Any flag you already passed skips that step.",
            ),
        )
    }
    val directoryRaw =
        positional.firstOrNull()
            ?: prompts.ask("Where should we create the app?", "kona-app")
    val directory = Path.of(directoryRaw).toAbsolutePath().normalize()
    val folder = directory.fileName.toString()
    val named = options["name"] ?: options["app-name"]
    val appName =
        when {
            named != null -> named
            canPrompt -> prompts.ask("App name", titleCaseFolder(folder))
            else -> folder
        }
    val lang =
        if (options.containsKey("lang")) {
            parseLangFlag(options.getValue("lang"))
        } else if (canPrompt) {
            NewStacks.langId(prompts.choice("Language", NewStacks.LANGUAGES, defaultIndex = 0))
        } else {
            "kotlin"
        }
    val kit =
        if (options.containsKey("kit") || options.containsKey("preset")) {
            parseKitFlag(options["kit"] ?: options.getValue("preset"))
        } else if (canPrompt) {
            NewStacks.kitId(prompts.choice("What are we brewing?", NewStacks.KITS, defaultIndex = 1))
        } else {
            "web"
        }
    val frontendFlag = options["frontend"]?.let { parseFrontendFlag(it) }
    val frontend =
        when {
            kit != "inertia" -> ""
            frontendFlag != null -> frontendFlag
            canPrompt -> NewStacks.frontendId(prompts.choice("Inertia adapter", NewStacks.FRONTENDS, defaultIndex = 0))
            else -> "vue"
        }
    val scriptFlag = frontendScriptChoice(options)
    val script =
        when {
            kit != "inertia" -> "js"
            scriptFlag != null -> scriptFlag
            canPrompt -> NewStacks.scriptId(prompts.choice("Frontend language", NewStacks.SCRIPTS, defaultIndex = 0))
            else -> "js"
        }
    val cssFlag = options["css"]?.let { parseCssFlag(it) }
    val css =
        when {
            kit == "api" -> "custom"
            cssFlag != null -> cssFlag
            canPrompt -> NewStacks.cssId(prompts.choice("CSS", NewStacks.CSS, defaultIndex = 0))
            else -> "custom"
        }
    val database =
        if (options.containsKey("database")) {
            DatabaseEngines.resolve(options.getValue("database"))
        } else if (canPrompt) {
            val names = DatabaseEngines.names()
            val picked = prompts.choice("Database", names, defaultIndex = names.indexOf(DatabaseEngines.DEFAULT_NAME).coerceAtLeast(0))
            DatabaseEngines.require(picked)
        } else {
            DatabaseEngines.require(DatabaseEngines.DEFAULT_NAME)
        }
    val installDeps = triState(options, "install-deps")
        ?: if (canPrompt) prompts.confirm("Install dependencies now? (Gradle wrapper + npm when the kit has a JS app)", true) else false
    val migrate = triState(options, "migrate")
        ?: if (canPrompt) prompts.confirm("Run initial migrations?", true) else false
    val seed = triState(options, "seed")
        ?: if (canPrompt) prompts.confirm("Run database seeders?", true) else false
    val plan = NewPlan(
        directory = directory,
        appName = appName,
        lang = lang,
        kit = kit,
        css = css,
        frontend = frontend,
        database = database,
        installDeps = installDeps,
        migrate = migrate,
        seed = seed,
        script = script,
    )
    if (canPrompt) {
        io.writeln()
        io.box(
            "Ready to write",
            buildList {
                add("App        ${plan.appName}")
                add("Path       ${plan.directory}")
                add("Language   ${plan.lang}")
                add("Stack      ${plan.stackLabel}")
                if (plan.kit == "inertia") {
                    add("Frontend   ${if (plan.script == "ts") "TypeScript" else "JavaScript"}")
                }
                add("Database   ${plan.database.name}")
                add("Install    ${if (plan.installDeps) "yes" else "skip"}")
                add("Migrate    ${if (plan.migrate) "yes" else "skip"}")
                add("Seed       ${if (plan.seed) "yes" else "skip"}")
            },
        )
        if (!prompts.confirm("Create the application?", true)) {
            io.warn("Nothing was written.")
            return null
        }
    }
    return plan
}
