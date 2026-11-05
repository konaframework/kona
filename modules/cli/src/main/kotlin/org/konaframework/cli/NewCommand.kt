package org.konaframework.cli

import org.konaframework.support.console.ConsoleIO
import java.nio.file.Files
import java.nio.file.Path

/**
 * `kona new [name]` — scaffolds a self-contained Gradle application with a local `./grind` CLI.
 *
 * Always interactive unless `--no-interaction` / `--no-interactive`. A positional directory
 * skips **only** the directory question; language, kit, CSS, database, install, migrate, and
 * seed are still asked. `CI` and missing `System.console()` do not skip prompts.
 * Kits (`--kit` / `--preset`): `web` (default), `api`, `crema` (alias of `web`), `inertia`, `wire` (`brew` alias).
 */
class NewCommand
@JvmOverloads
constructor(
    private val versions: KonaVersions = KonaVersions.load(),
    private val io: ConsoleIO = ConsoleIO(),
    private val prompts: InstallerPrompts = InstallerPrompts(),
    private val runner: GrindProcessRunner = GrindProcessRunner.SYSTEM,
    private val windows: Boolean = defaultWindows(),
) {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (options.containsKey("help") || options.containsKey("h") || positional.contains("-h")) {
            printNewHelp(io, versions)
            return 0
        }
        val canPrompt = wantsInteraction(options)
        val plan = resolveNewPlan(positional, options, canPrompt, prompts, io) ?: return 1
        val target = plan.directory
        if (Files.exists(target)) {
            throw KonaCliException("Directory already exists: $target")
        }
        val database = plan.database
        // H2 keeps the scaffold alias `main` (same URL as `h2`); everyone else uses the engine key.
        val dbConnection = if (database.name == "h2") "main" else database.connectionKey
        val variables = mapOf(
            "appName" to plan.appName,
            "konaVersion" to options.getOrDefault("kona-version", versions.kona),
            "kotlinVersion" to versions.kotlin,
            "kit" to plan.kit,
            "appKey" to ScaffoldKeys.generateAppKey(),
            "dbConnection" to dbConnection,
            "viteEntry" to ScaffoldFrontends.viteEntry(plan.frontend, plan.script),
        )
        Files.createDirectories(target)
        copySkeleton(plan.lang, target, variables, plan.kit, plan.css, plan.frontend, plan.script)
        applyDatabaseSelection(target, database, dbConnection)
        target.resolve("grind").toFile().setExecutable(true)
        target.resolve("gradlew").toFile().setExecutable(true)
        // Do not write kona.home — apps resolve published artifacts. Opt in with
        // -Pkona.home=… / KONA_HOME (see settings.gradle.kts).
        if (!canPrompt) {
            io.intro("kona new")
        }
        io.success("Application created successfully.")
        io.twoColumn("Path", target.toString(), 12)
        io.twoColumn("Language", plan.lang, 12)
        io.twoColumn("Stack", plan.stackLabel, 12)
        io.twoColumn("Database", database.name, 12)
        runPostCreate(target, plan)
        io.writeln()
        io.subtitle("Next steps")
        io.twoColumn("cd", target.fileName.toString(), 12)
        if (Files.isRegularFile(target.resolve("package.json"))) {
            io.twoColumn("frontend", "npm install && npm run dev", 12)
        }
        io.twoColumn("serve", "./grind serve", 12)
        if (!plan.migrate) {
            io.twoColumn("migrate", "./grind migrate --seed", 12)
        }
        io.outro("Pour a cup — then ./grind serve.")
        return 0
    }

    internal fun runPostCreate(target: Path, plan: NewPlan) {
        if (plan.installDeps) {
            val gradle = target.resolve(if (windows) "gradlew.bat" else "gradlew").toString()
            val gradleCode = runQuiet(listOf(gradle, "--no-daemon", "help", "-q"), target)
            if (gradleCode == 0) {
                io.success("Gradle wrapper ready.")
            } else {
                io.warn("Gradle dependencies skipped (wrapper exited $gradleCode).")
            }
            if (Files.isRegularFile(target.resolve("package.json"))) {
                val js = jsInstallCommand(target)
                val jsCode = runQuiet(js, target)
                if (jsCode == 0) {
                    io.success("Frontend packages installed (${js.first()}).")
                } else {
                    io.warn("Frontend install skipped (${js.first()} exited $jsCode).")
                }
            }
        }
        if (plan.migrate) {
            val grind = target.resolve(if (windows) "grind.bat" else "grind").toString()
            val code = runQuiet(listOf(grind, "migrate"), target)
            if (code == 0) {
                io.success("Migrations applied.")
            } else {
                io.warn("Migrations skipped (no database, or grind migrate failed).")
            }
        }
        if (plan.seed) {
            val grind = target.resolve(if (windows) "grind.bat" else "grind").toString()
            val code = runQuiet(listOf(grind, "db:seed"), target)
            if (code == 0) {
                io.success("Database seeded.")
            } else {
                io.warn("Seeders skipped (no database, or grind db:seed failed).")
            }
        }
    }

    internal fun jsInstallCommand(target: Path): List<String> =
        if (Files.isRegularFile(target.resolve("pnpm-lock.yaml"))) {
            listOf("pnpm", "install")
        } else {
            listOf("npm", "install")
        }

    internal fun runQuiet(command: List<String>, dir: Path): Int =
        try {
            runner.run(command, dir)
        } catch (_: KonaCliException) {
            -1
        } catch (_: Exception) {
            -1
        }

    /**
     * Sets `DB_CONNECTION` and engine host/port/database credentials in `.env` files.
     * Leaves `DB_URL` commented so `DB_HOST` / `DB_PORT` / `DB_DATABASE` are honored.
     * Adds the engine's JDBC `runtimeOnly` coordinate when it is not the SQLite
     * zero-install default (already on every skeleton).
     */
    internal fun applyDatabaseSelection(
        target: Path,
        engine: DatabaseEngines.Engine,
        dbConnection: String,
    ) {
        listOf(".env", ".env.example").forEach { name ->
            val path = target.resolve(name)
            if (!Files.isRegularFile(path)) {
                return@forEach
            }
            var text = Files.readString(path)
            text = upsertEnvLine(text, "DB_CONNECTION", dbConnection)
            // Prefer discrete host pieces; keep DB_URL as a commented documentation line.
            text = upsertEnvLine(text, "DB_URL", engine.defaultUrl, commented = true)
            if (engine.usesHostPieces) {
                text = upsertEnvLine(text, "DB_HOST", engine.defaultHost!!)
                text = upsertEnvLine(text, "DB_PORT", engine.defaultPort!!)
                text = upsertEnvLine(text, "DB_DATABASE", engine.defaultDatabase!!)
                text = upsertEnvLine(text, "DB_USERNAME", engine.defaultUsername)
                text = upsertEnvLine(text, "DB_PASSWORD", engine.defaultPassword)
            } else if (engine.defaultUsername.isNotEmpty()) {
                text = upsertEnvLine(text, "DB_USERNAME", engine.defaultUsername)
                text = upsertEnvLine(text, "DB_PASSWORD", engine.defaultPassword)
            }
            Files.writeString(path, text)
        }
        val dep = engine.runtimeDependency ?: return
        val build = target.resolve("build.gradle.kts")
        if (!Files.isRegularFile(build)) {
            return
        }
        var gradle = Files.readString(build)
        if (gradle.contains(dep)) {
            return
        }
        val marker = "runtimeOnly(\"org.xerial:sqlite-jdbc:3.49.1.0\")"
        val insertion = "$marker\n    // Selected by kona new --database=${engine.name}\n    runtimeOnly(\"$dep\")"
        gradle = if (gradle.contains(marker)) {
            gradle.replace(marker, insertion)
        } else {
            gradle.replace(
                Regex("dependencies \\{"),
                "dependencies {\n    runtimeOnly(\"$dep\")",
            )
        }
        Files.writeString(build, gradle)
    }

    /** Inserts or replaces `KEY=value` (optionally commented) in dotenv text. */
    internal fun upsertEnvLine(text: String, key: String, value: String, commented: Boolean = false): String {
        val line = if (commented) "# $key=$value" else "$key=$value"
        val pattern = Regex("(?m)^#?\\s*$key=.*$")
        return if (pattern.containsMatchIn(text)) {
            text.replace(pattern, line)
        } else {
            text.trimEnd() + "\n$line\n"
        }
    }

    /**
     * Optional helper for contributors: writes `kona.home` so `settings.gradle.kts`
     * `includeBuild`s a local checkout. [kona new] does **not** call this — generated
     * apps resolve Maven Central / Maven Local by default. Pass `-Pkona.home` or
     * `KONA_HOME` instead.
     */
    internal fun writeKonaHomeProperty(target: Path, home: Path? = resolveKonaHome()) {
        if (home == null) {
            return
        }
        val props = target.resolve("gradle.properties")
        val line = "kona.home=${home.toAbsolutePath().normalize()}"
        val existing = if (Files.isRegularFile(props)) Files.readString(props) else ""
        val kept = existing.lineSequence().filterNot { it.startsWith("kona.home=") }.toList()
        val body = kept.joinToString("\n").trimEnd()
        val content =
            if (body.isEmpty()) {
                "# Local Kona framework checkout (opt-in; prefer -Pkona.home / KONA_HOME)\n$line\n"
            } else {
                "$body\n\n# Local Kona framework checkout (opt-in; prefer -Pkona.home / KONA_HOME)\n$line\n"
            }
        Files.writeString(props, content)
    }

    internal fun copySkeleton(
        lang: String,
        target: Path,
        variables: Map<String, String>,
        kit: String = "web",
        css: String = "custom",
        frontend: String = "vue",
        script: String = "js",
    ) {
        val files = SKELETONS[lang] ?: throw KonaCliException("Unknown skeleton: $lang")
        COMMON_FILES.forEach { copyResource("skeletons/common/$it", target.resolve(destination(it)), variables) }
        files.forEach { copyResource("skeletons/$lang/$it", target.resolve(destination(it)), variables) }
        val langRoot = if (lang == "java") "src/main/java" else "src/main/kotlin"
        listOf(
            "$langRoot/app/http/controllers",
            "$langRoot/app/http/middleware",
            "$langRoot/app/providers",
            "$langRoot/app/models",
            "$langRoot/routes",
            "$langRoot/config",
            "$langRoot/database/migrations",
            "$langRoot/database/factories",
            "$langRoot/database/seeders",
            "src/main/resources/views",
            "public",
            "src/main/resources/lang",
            "storage/app",
            "storage/framework",
            "storage/logs",
            "storage/database",
            "src/test/${if (lang == "java") "java" else "kotlin"}",
        ).forEach {
            Files.createDirectories(target.resolve(it))
        }
        applyKit(lang, kit, target, variables)
        ScaffoldFrontends.apply(target, kit, css, frontend, script)
        // Vue auth pages / controllers replace the generic Inertia Crema login routes.
        // Empty frontend matches ScaffoldFrontends (defaults to vue).
        val inertiaAdapter = frontend.ifEmpty { "vue" }
        if (kit == "inertia" && inertiaAdapter == "vue") {
            overlayKit("inertia-vue", lang, target, variables)
        }
    }

    internal fun applyKit(lang: String, kit: String, target: Path, variables: Map<String, String>) {
        when (kit) {
            "api" -> {
                // Base skeleton without Crema (JSON health + plain-text welcome).
            }
            "web", "crema" -> overlayKit("crema", lang, target, variables)
            "inertia" -> {
                overlayKit("crema", lang, target, variables)
                overlayKit("inertia", lang, target, variables)
            }
            "wire", "brew" -> {
                overlayKit("crema", lang, target, variables)
                overlayKit("wire", lang, target, variables)
            }
            "firefly" -> {
                overlayKit("crema", lang, target, variables)
                overlayKit("firefly", lang, target, variables)
            }
            else -> throw KonaCliException("Unknown kit: $kit")
        }
    }

    internal fun overlayKit(kit: String, lang: String, target: Path, variables: Map<String, String>) {
        KIT_COMMON[kit].orEmpty().forEach { relative ->
            copyResource("skeletons/kits/$kit/common/$relative", target.resolve(destination(relative)), variables)
        }
        KIT_LANG[kit]?.get(lang).orEmpty().forEach { relative ->
            copyResource("skeletons/kits/$kit/$lang/$relative", target.resolve(destination(relative)), variables)
        }
    }

    internal fun copyResource(resource: String, dest: Path, variables: Map<String, String>) {
        val url = NewCommand::class.java.classLoader.getResource(resource)
            ?: throw KonaCliException("Missing skeleton resource: $resource")
        Files.createDirectories(dest.parent)
        val bytes = url.readBytes()
        if (resource.endsWith(".jar")) {
            Files.write(dest, bytes)
            return
        }
        var content = bytes.toString(Charsets.UTF_8)
        variables.forEach { (key, value) -> content = content.replace("{{$key}}", value) }
        Files.writeString(dest, content)
    }

    private fun destination(relative: String): String =
        when (relative) {
            "gitignore.txt" -> ".gitignore"
            "env.txt" -> ".env"
            "env.example.txt" -> ".env.example"
            "grind.txt" -> "grind"
            "grind.bat.txt" -> "grind.bat"
            else ->
                if (relative.endsWith(".kt.txt") ||
                    relative.endsWith(".java.txt") ||
                    relative.endsWith(".kts.txt")
                ) {
                    relative.removeSuffix(".txt")
                } else {
                    relative
                }
        }

    companion object {
        internal fun defaultWindows(osName: String? = System.getProperty("os.name")): Boolean =
            (osName ?: "").startsWith("Windows")

        internal fun resolveKonaHome(
            env: Map<String, String> = System.getenv(),
            userHome: Path = Path.of(System.getProperty("user.home")),
        ): Path? {
            env["KONA_HOME"]?.takeIf { it.isNotBlank() }?.let { candidate ->
                val path = Path.of(candidate).toAbsolutePath().normalize()
                if (isUsableKonaHome(path)) return path
            }
            val xdg = env["XDG_CONFIG_HOME"]
            val configHome =
                if (xdg.isNullOrBlank()) {
                    userHome.resolve(".config")
                } else {
                    Path.of(xdg)
                }
            val recorded = configHome.resolve("kona/home")
            if (Files.isRegularFile(recorded)) {
                val path = Path.of(Files.readString(recorded).trim()).toAbsolutePath().normalize()
                if (isUsableKonaHome(path)) return path
            }
            val defaultSrc = userHome.resolve(".kona")
            if (isUsableKonaHome(defaultSrc)) return defaultSrc
            return null
        }

        internal fun isKonaRoot(path: Path): Boolean =
            Files.isRegularFile(path.resolve("settings.gradle.kts")) &&
                Files.isDirectory(path.resolve("modules/cli")) &&
                Files.isExecutable(path.resolve("gradlew"))

        /**
         * Rejects checkouts whose `modules/wire` lacks the current `template()` API —
         * `includeBuild` would substitute that stale module and break `--kit=wire` scaffolds.
         * In-tree Brew with `template()` is the supported remonorepo layout.
         */
        internal fun isUsableKonaHome(path: Path): Boolean {
            if (!isKonaRoot(path)) return false
            // Legacy (dev.kona / io.konaframework) or current (org.konaframework) layout under a leftover modules/wire.
            val brewComponent = listOf(
                path.resolve("modules/wire/src/main/kotlin/org/konaframework/wire/Component.kt"),
                path.resolve("modules/wire/src/main/kotlin/dev/kona/wire/Component.kt"),
            ).firstOrNull { Files.isRegularFile(it) } ?: return true
            return Files.readString(brewComponent).contains("fun template")
        }
        private val COMMON_FILES = listOf(
            "gitignore.txt",
            "public/.gitkeep",
            "public/robots.txt",
            "public/branding/kona-logo.svg",
            "gradlew",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
            "grind.txt",
            "grind.bat.txt",
        )

        private fun baseFiles(lang: String, ext: String): List<String> {
            val root = if (lang == "java") "src/main/java" else "src/main/kotlin"
            return listOf(
                "README.md",
                "env.txt",
                "env.example.txt",
                "settings.gradle.kts.txt",
                "build.gradle.kts.txt",
                "$root/bootstrap/App.$ext.txt",
                "$root/bootstrap/Middleware.$ext.txt",
                "$root/app/providers/AppServiceProvider.$ext.txt",
                "$root/app/http/controllers/WelcomeController.$ext.txt",
                "$root/app/http/controllers/api/UserController.$ext.txt",
                "$root/app/models/User.$ext.txt",
                "$root/database/migrations/2000_01_01_000000_create_users_table.$ext.txt",
                "$root/database/seeders/DatabaseSeeder.$ext.txt",
                "$root/routes/WebRoutes.$ext.txt",
                "$root/routes/ApiRoutes.$ext.txt",
                "$root/config/App.$ext.txt",
                "$root/config/Database.$ext.txt",
                "$root/config/Redis.$ext.txt",
                "$root/config/Session.$ext.txt",
                "$root/config/Cache.$ext.txt",
                "$root/config/Filesystems.$ext.txt",
                "$root/config/Queue.$ext.txt",
                "$root/config/Mail.$ext.txt",
                "$root/config/Logging.$ext.txt",
                "$root/config/Auth.$ext.txt",
                "$root/config/Hash.$ext.txt",
                "$root/config/Broadcasting.$ext.txt",
                "$root/config/Services.$ext.txt",
                "$root/config/Http.$ext.txt",
                "$root/config/Paths.$ext.txt",
                "src/main/resources/css/app.css",
                "src/main/resources/js/app.js",
                "src/main/resources/META-INF/kona/migrations",
                "src/main/resources/lang/.gitkeep",
                "$root/app/http/middleware/.gitkeep",
                "$root/database/factories/.gitkeep",
                "storage/app/.gitkeep",
                "storage/app/public/.gitkeep",
                "storage/framework/.gitkeep",
                "storage/logs/.gitkeep",
                "storage/database/.gitkeep",
            )
        }

        private val SKELETONS = mapOf(
            "kotlin" to baseFiles("kotlin", "kt"),
            "java" to baseFiles("java", "java"),
        )

        private val KIT_COMMON = mapOf(
            "crema" to listOf(
                "src/main/resources/views/welcome.crema.html",
                "src/main/resources/views/login.crema.html",
                "src/main/resources/views/dashboard.crema.html",
            ),
            "inertia" to listOf(
                "package.json",
                "vite.config.js",
                "src/main/resources/js/app.js",
                "src/main/resources/views/app.crema.html",
                "public/.gitkeep",
            ),
            "inertia-vue" to listOf(
                "src/main/resources/js/Layouts/AuthLayout.vue",
                "src/main/resources/js/Layouts/AppLayout.vue",
                "src/main/resources/js/Components/InputError.vue",
                "src/main/resources/js/Components/TextLink.vue",
                // Welcome.vue stays from ScaffoldFrontends (CSS + optional TypeScript).
                "src/main/resources/js/Pages/Dashboard.vue",
                "src/main/resources/js/Pages/Auth/Login.vue",
                "src/main/resources/js/Pages/Auth/Register.vue",
                "src/main/resources/js/Pages/Auth/ForgotPassword.vue",
                "src/main/resources/js/Pages/Auth/ResetPassword.vue",
                "src/main/resources/js/Pages/Auth/VerifyEmail.vue",
                "src/main/resources/js/Pages/Auth/ConfirmPassword.vue",
                "src/main/resources/js/Pages/Auth/TwoFactorChallenge.vue",
                "src/main/resources/js/Pages/Settings/Profile.vue",
                "src/main/resources/js/Pages/Settings/Security.vue",
                "src/main/resources/js/Pages/Settings/Appearance.vue",
            ),
            // Brew overlays Crema landing with a live `<wire:counter />` demo (+ Counter class via KIT_LANG).
            "wire" to listOf(
                "src/main/resources/views/welcome.crema.html",
            ),
            "firefly" to emptyList(),
        )

        private val KIT_LANG = mapOf(
            "crema" to mapOf(
                "kotlin" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/kotlin/bootstrap/App.kt.txt",
                    "src/main/kotlin/app/http/controllers/WelcomeController.kt.txt",
                    "src/main/kotlin/app/http/controllers/LoginController.kt.txt",
                    "src/main/kotlin/app/http/controllers/DashboardController.kt.txt",
                    "src/main/kotlin/routes/WebRoutes.kt.txt",
                    "README.md",
                ),
                "java" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/java/bootstrap/App.java.txt",
                    "src/main/java/app/http/controllers/WelcomeController.java.txt",
                    "src/main/java/app/http/controllers/LoginController.java.txt",
                    "src/main/java/app/http/controllers/DashboardController.java.txt",
                    "src/main/java/routes/WebRoutes.java.txt",
                    "README.md",
                ),
            ),
            "inertia" to mapOf(
                "kotlin" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/kotlin/bootstrap/App.kt.txt",
                    "src/main/kotlin/app/http/middleware/HandleInertia.kt.txt",
                    "src/main/kotlin/routes/WebRoutes.kt.txt",
                    "README.md",
                ),
                "java" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/java/bootstrap/App.java.txt",
                    "src/main/java/app/http/middleware/HandleInertia.java.txt",
                    "src/main/java/routes/WebRoutes.java.txt",
                    "README.md",
                ),
            ),
            "inertia-vue" to mapOf(
                "kotlin" to listOf(
                    "src/main/kotlin/app/support/Features.kt.txt",
                    "src/main/kotlin/app/models/User.kt.txt",
                    "src/main/kotlin/database/migrations/2000_01_01_000000_create_users_table.kt.txt",
                    "src/main/kotlin/app/http/middleware/HandleInertia.kt.txt",
                    "src/main/kotlin/app/http/middleware/HandleAppearance.kt.txt",
                    "src/main/kotlin/app/http/controllers/WelcomeController.kt.txt",
                    "src/main/kotlin/app/http/controllers/DashboardController.kt.txt",
                    "src/main/kotlin/app/http/controllers/Auth/SessionController.kt.txt",
                    "src/main/kotlin/app/http/controllers/Auth/RegisteredUserController.kt.txt",
                    "src/main/kotlin/app/http/controllers/Auth/PasswordResetController.kt.txt",
                    "src/main/kotlin/app/http/controllers/Auth/EmailVerificationController.kt.txt",
                    "src/main/kotlin/app/http/controllers/Auth/ConfirmPasswordController.kt.txt",
                    "src/main/kotlin/app/http/controllers/Settings/ProfileController.kt.txt",
                    "src/main/kotlin/app/http/controllers/Settings/PasswordController.kt.txt",
                    "src/main/kotlin/app/http/controllers/Settings/TwoFactorController.kt.txt",
                    "src/main/kotlin/app/http/controllers/Settings/AppearanceController.kt.txt",
                    "src/main/kotlin/routes/WebRoutes.kt.txt",
                    "src/main/kotlin/bootstrap/App.kt.txt",
                    "src/main/kotlin/bootstrap/Middleware.kt.txt",
                    "src/test/kotlin/AuthScaffoldTest.kt.txt",
                    "README.md",
                ),
                "java" to listOf(
                    "src/main/java/app/support/Features.java.txt",
                    "src/main/java/app/models/User.java.txt",
                    "src/main/java/database/migrations/2000_01_01_000000_create_users_table.java.txt",
                    "src/main/java/app/http/middleware/HandleInertia.java.txt",
                    "src/main/java/app/http/middleware/HandleAppearance.java.txt",
                    "src/main/java/app/http/controllers/WelcomeController.java.txt",
                    "src/main/java/app/http/controllers/DashboardController.java.txt",
                    "src/main/java/app/http/controllers/auth/SessionController.java.txt",
                    "src/main/java/app/http/controllers/auth/RegisteredUserController.java.txt",
                    "src/main/java/app/http/controllers/auth/PasswordResetController.java.txt",
                    "src/main/java/app/http/controllers/auth/EmailVerificationController.java.txt",
                    "src/main/java/app/http/controllers/auth/ConfirmPasswordController.java.txt",
                    "src/main/java/app/http/controllers/settings/ProfileController.java.txt",
                    "src/main/java/app/http/controllers/settings/PasswordController.java.txt",
                    "src/main/java/app/http/controllers/settings/TwoFactorController.java.txt",
                    "src/main/java/app/http/controllers/settings/AppearanceController.java.txt",
                    "src/main/java/routes/WebRoutes.java.txt",
                    "src/main/java/bootstrap/App.java.txt",
                    "src/main/java/bootstrap/Middleware.java.txt",
                    "README.md",
                ),
            ),
            "wire" to mapOf(
                "kotlin" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/kotlin/bootstrap/App.kt.txt",
                    "src/main/kotlin/app/wire/Counter.kt.txt",
                    "README.md",
                ),
                "java" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/java/bootstrap/App.java.txt",
                    "src/main/java/app/wire/Counter.java.txt",
                    "README.md",
                ),
            ),
            "firefly" to mapOf(
                "kotlin" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/kotlin/bootstrap/App.kt.txt",
                    "src/main/kotlin/app/panels/admin/Panel.kt.txt",
                    "src/main/kotlin/app/panels/admin/pages/Dashboard.kt.txt",
                    "src/main/kotlin/app/panels/admin/widgets/.gitkeep",
                    "src/main/kotlin/app/panels/admin/resources/.gitkeep",
                ),
                "java" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/java/bootstrap/App.java.txt",
                    "src/main/java/app/panels/admin/Panel.java.txt",
                    "src/main/java/app/panels/admin/pages/Dashboard.java.txt",
                    "src/main/java/app/panels/admin/widgets/.gitkeep",
                    "src/main/java/app/panels/admin/resources/.gitkeep",
                ),
            ),
        )
    }
}

/** Versions stamped into `kona.properties` at build time. */
data class KonaVersions
@JvmOverloads
constructor(
    val kona: String,
    val kotlin: String,
    val revision: String = kona,
) {
    fun display(): String =
        if (revision.isBlank() || revision == kona) {
            "kona $kona"
        } else {
            "kona $kona ($revision)"
        }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun load(resource: String = "org/konaframework/cli/kona.properties"): KonaVersions {
            val stream = KonaVersions::class.java.classLoader.getResourceAsStream(resource)
                ?: throw KonaCliException("Missing kona version resource: $resource")
            val props = java.util.Properties()
            stream.use { props.load(it) }
            val kona = props.getProperty("konaVersion")
            val revision = props.getProperty("konaRevision")?.trim().orEmpty().ifEmpty { kona }
            return KonaVersions(
                kona = kona,
                kotlin = props.getProperty("kotlinVersion"),
                revision = revision,
            )
        }
    }
}

internal fun printNewHelp(io: ConsoleIO, versions: KonaVersions = KonaVersions.load()) {
    io.intro("kona new")
    io.box(
        versions.display(),
        listOf(
            "Interactive installer. A name skips only the directory question.",
            "Scripts: pass --no-interaction (CI=true does not skip prompts).",
        ),
    )
    io.title("Usage")
    io.info(NEW_USAGE)
    io.twoColumn("kona new", "Ask every question")
    io.twoColumn("kona new my-app", "Directory set; still asks lang / kit / CSS / db / …")
    io.twoColumn("kona new my-app --no-interaction", "Silent defaults (kotlin, web, sqlite)")
    io.writeln()
    io.title("Skip one step")
    io.info("--lang --kit/--preset --css --frontend --typescript/--ts --javascript/--js --name --database --install-deps --migrate --seed")
    io.info("Inertia only: --typescript/--ts or --javascript/--js. If both are passed, the last flag wins.")
    io.info("Aliases: --no-interactive (same as --no-interaction); --no-install-deps --no-migrate --no-seed")
    io.outro("https://docs.konaframework.org/docs/starter-kits/")
}
