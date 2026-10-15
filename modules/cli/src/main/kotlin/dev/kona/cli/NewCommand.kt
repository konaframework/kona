package dev.kona.cli

import dev.kona.support.console.ConsoleIO
import java.nio.file.Files
import java.nio.file.Path

/**
 * `kona new <name>` — scaffolds a self-contained Gradle application with a local `./grind` CLI.
 *
 * Kits (`--kit` / `--preset`): `web` (default), `api`, `crema` (alias of `web`), `inertia`, `brew`.
 */
class NewCommand(
    private val versions: KonaVersions = KonaVersions.load(),
) {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        val name = positional.firstOrNull()
            ?: throw KonaCliException(
                "Usage: kona new <name> [--lang=kotlin|java] [--kit=web|api|crema|inertia|brew] [--kona-version=x.y.z]",
            )
        val lang = options["lang"] ?: "kotlin"
        if (lang !in SKELETONS) {
            throw KonaCliException("--lang must be kotlin or java")
        }
        val kit = (options["kit"] ?: options["preset"] ?: "web").lowercase()
        if (kit !in KITS) {
            throw KonaCliException("--kit/--preset must be one of: ${KITS.joinToString(", ")}")
        }
        val target = Path.of(name).toAbsolutePath().normalize()
        if (Files.exists(target)) {
            throw KonaCliException("Directory already exists: $target")
        }
        val variables = mapOf(
            "appName" to target.fileName.toString(),
            "konaVersion" to options.getOrDefault("kona-version", versions.kona),
            "kotlinVersion" to versions.kotlin,
            "kit" to kit,
            "appKey" to ScaffoldKeys.generateAppKey(),
        )
        Files.createDirectories(target)
        copySkeleton(lang, target, variables, kit)
        target.resolve("grind").toFile().setExecutable(true)
        target.resolve("gradlew").toFile().setExecutable(true)
        writeKonaHomeProperty(target)
        val io = ConsoleIO()
        io.success("Created Kona application at $target ($lang, kit=$kit)")
        io.info("cd ${target.fileName}")
        io.info("./grind serve")
        return 0
    }

    /**
     * Points the app at a local Kona checkout (`includeBuild`) when available so
     * SNAPSHOT modules resolve without every transitive jar on Maven Central.
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
                "# Local Kona framework checkout (set by kona new / installer)\n$line\n"
            } else {
                "$body\n\n# Local Kona framework checkout (set by kona new / installer)\n$line\n"
            }
        Files.writeString(props, content)
    }

    internal fun copySkeleton(
        lang: String,
        target: Path,
        variables: Map<String, String>,
        kit: String = "web",
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
            "src/main/resources/public",
            "src/main/resources/lang",
            "storage/app",
            "storage/framework",
            "storage/logs",
            "src/test/${if (lang == "java") "java" else "kotlin"}",
        ).forEach {
            Files.createDirectories(target.resolve(it))
        }
        applyKit(lang, kit, target, variables)
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
            "brew" -> {
                overlayKit("crema", lang, target, variables)
                overlayKit("brew", lang, target, variables)
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
        private val KITS = listOf("web", "api", "crema", "inertia", "brew")

        internal fun resolveKonaHome(
            env: Map<String, String> = System.getenv(),
            userHome: Path = Path.of(System.getProperty("user.home")),
        ): Path? {
            env["KONA_HOME"]?.takeIf { it.isNotBlank() }?.let { candidate ->
                val path = Path.of(candidate).toAbsolutePath().normalize()
                if (isKonaRoot(path)) return path
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
                if (isKonaRoot(path)) return path
            }
            val defaultSrc = userHome.resolve(".kona")
            if (isKonaRoot(defaultSrc)) return defaultSrc
            return null
        }

        internal fun isKonaRoot(path: Path): Boolean =
            Files.isRegularFile(path.resolve("settings.gradle.kts")) &&
                Files.isDirectory(path.resolve("modules/cli")) &&
                Files.isExecutable(path.resolve("gradlew"))

        private val COMMON_FILES = listOf(
            "gitignore.txt",
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
                "$root/routes/WebRoutes.$ext.txt",
                "$root/routes/ApiRoutes.$ext.txt",
                "$root/config/App.$ext.txt",
                "$root/config/Database.$ext.txt",
                "$root/config/Session.$ext.txt",
                "$root/config/Filesystems.$ext.txt",
                "$root/config/Http.$ext.txt",
                "$root/config/Paths.$ext.txt",
                "src/main/resources/css/app.css",
                "src/main/resources/js/app.js",
                "src/main/resources/public/.gitkeep",
                "src/main/resources/public/robots.txt",
                "src/main/resources/lang/.gitkeep",
                "$root/app/http/middleware/.gitkeep",
                "$root/app/models/.gitkeep",
                "$root/database/migrations/.gitkeep",
                "$root/database/factories/.gitkeep",
                "$root/database/seeders/.gitkeep",
                "storage/app/.gitkeep",
                "storage/app/public/.gitkeep",
                "storage/framework/.gitkeep",
                "storage/logs/.gitkeep",
            )
        }

        private val SKELETONS = mapOf(
            "kotlin" to baseFiles("kotlin", "kt"),
            "java" to baseFiles("java", "java"),
        )

        private val KIT_COMMON = mapOf(
            "crema" to listOf(
                "src/main/resources/views/welcome.crema.html",
            ),
            "inertia" to listOf(
                "package.json",
                "vite.config.js",
                "src/main/resources/js/app.js",
                "src/main/resources/views/app.crema.html",
                "public/.gitkeep",
            ),
            "brew" to listOf(
                "src/main/resources/views/brew-counter.crema.html",
            ),
        )

        private val KIT_LANG = mapOf(
            "crema" to mapOf(
                "kotlin" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/kotlin/bootstrap/App.kt.txt",
                    "src/main/kotlin/app/http/controllers/WelcomeController.kt.txt",
                    "README.md",
                ),
                "java" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/java/bootstrap/App.java.txt",
                    "src/main/java/app/http/controllers/WelcomeController.java.txt",
                    "README.md",
                ),
            ),
            "inertia" to mapOf(
                "kotlin" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/kotlin/bootstrap/App.kt.txt",
                    "src/main/kotlin/app/http/controllers/WelcomeController.kt.txt",
                    "src/main/kotlin/app/http/middleware/HandleInertia.kt.txt",
                    "src/main/kotlin/routes/WebRoutes.kt.txt",
                    "README.md",
                ),
                "java" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/java/bootstrap/App.java.txt",
                    "src/main/java/app/http/controllers/WelcomeController.java.txt",
                    "src/main/java/app/http/middleware/HandleInertia.java.txt",
                    "src/main/java/routes/WebRoutes.java.txt",
                    "README.md",
                ),
            ),
            "brew" to mapOf(
                "kotlin" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/kotlin/bootstrap/App.kt.txt",
                    "src/main/kotlin/app/http/controllers/WelcomeController.kt.txt",
                    "src/main/kotlin/app/brew/Counter.kt.txt",
                    "src/main/kotlin/routes/WebRoutes.kt.txt",
                    "README.md",
                ),
                "java" to listOf(
                    "build.gradle.kts.txt",
                    "src/main/java/bootstrap/App.java.txt",
                    "src/main/java/app/http/controllers/WelcomeController.java.txt",
                    "src/main/java/app/brew/Counter.java.txt",
                    "src/main/java/routes/WebRoutes.java.txt",
                    "README.md",
                ),
            ),
        )
    }
}

/** Versions stamped into `kona.properties` at build time. */
data class KonaVersions(val kona: String, val kotlin: String) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun load(resource: String = "dev/kona/cli/kona.properties"): KonaVersions {
            val stream = KonaVersions::class.java.classLoader.getResourceAsStream(resource)
                ?: throw KonaCliException("Missing kona version resource: $resource")
            val props = java.util.Properties()
            stream.use { props.load(it) }
            return KonaVersions(
                kona = props.getProperty("konaVersion"),
                kotlin = props.getProperty("kotlinVersion"),
            )
        }
    }
}
