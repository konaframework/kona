package dev.kona.cli

import java.nio.file.Files
import java.nio.file.Path

/**
 * `kona new <name>` — scaffolds a self-contained Gradle application with a local `./grind` CLI.
 *
 * Kits (`--kit` / `--preset`): `api` (default), `crema`, `inertia`, `brew`.
 */
class NewCommand(
    private val versions: KonaVersions = KonaVersions.load(),
) {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        val name = positional.firstOrNull()
            ?: throw KonaCliException(
                "Usage: kona new <name> [--lang=kotlin|java] [--kit=api|crema|inertia|brew] [--kona-version=x.y.z]",
            )
        val lang = options["lang"] ?: "kotlin"
        if (lang !in SKELETONS) {
            throw KonaCliException("--lang must be kotlin or java")
        }
        val kit = (options["kit"] ?: options["preset"] ?: "api").lowercase()
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
        )
        Files.createDirectories(target)
        copySkeleton(lang, target, variables, kit)
        target.resolve("grind").toFile().setExecutable(true)
        target.resolve("gradlew").toFile().setExecutable(true)
        println("Created Kona application at $target ($lang, kit=$kit)")
        println("  cd ${target.fileName}")
        println("  ./grind serve")
        return 0
    }

    internal fun copySkeleton(
        lang: String,
        target: Path,
        variables: Map<String, String>,
        kit: String = "api",
    ) {
        val files = SKELETONS[lang] ?: throw KonaCliException("Unknown skeleton: $lang")
        COMMON_FILES.forEach { copyResource("skeletons/common/$it", target.resolve(destination(it)), variables) }
        files.forEach { copyResource("skeletons/$lang/$it", target.resolve(destination(it)), variables) }
        listOf("app/Http/Controllers", "app/Providers", "config", "resources", "routes").forEach {
            Files.createDirectories(target.resolve(it))
        }
        applyKit(lang, kit, target, variables)
    }

    internal fun applyKit(lang: String, kit: String, target: Path, variables: Map<String, String>) {
        when (kit) {
            "api" -> {
                // Base skeleton is the API kit.
            }
            "crema" -> overlayKit("crema", lang, target, variables)
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
            "grind.txt" -> "grind"
            "grind.bat.txt" -> "grind.bat"
            else -> relative.removeSuffix(".txt")
        }

    companion object {
        private val KITS = listOf("api", "crema", "inertia", "brew")

        private val COMMON_FILES = listOf(
            "gitignore.txt",
            "gradlew",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
            "grind.txt",
            "grind.bat.txt",
        )

        private val SKELETONS = mapOf(
            "kotlin" to listOf(
                "README.md",
                "env.txt",
                "settings.gradle.kts.txt",
                "build.gradle.kts.txt",
                "bootstrap/App.kt.txt",
                "app/Providers/AppServiceProvider.kt.txt",
                "app/Http/Controllers/WelcomeController.kt.txt",
                "routes/web.kt.txt",
            ),
            "java" to listOf(
                "README.md",
                "env.txt",
                "settings.gradle.kts.txt",
                "build.gradle.kts.txt",
                "bootstrap/App.java.txt",
                "app/Providers/AppServiceProvider.java.txt",
                "app/Http/Controllers/WelcomeController.java.txt",
                "routes/WebRoutes.java.txt",
            ),
        )

        private val KIT_COMMON = mapOf(
            "crema" to listOf(
                "resources/views/welcome.crema.html",
            ),
            "inertia" to listOf(
                "package.json",
                "vite.config.js",
                "resources/js/app.js",
                "resources/views/app.crema.html",
                "public/.gitkeep",
            ),
            "brew" to listOf(
                "resources/views/brew-counter.crema.html",
            ),
        )

        private val KIT_LANG = mapOf(
            "crema" to mapOf(
                "kotlin" to listOf(
                    "build.gradle.kts.txt",
                    "bootstrap/App.kt.txt",
                    "app/Http/Controllers/WelcomeController.kt.txt",
                    "README.md",
                ),
                "java" to listOf(
                    "build.gradle.kts.txt",
                    "bootstrap/App.java.txt",
                    "app/Http/Controllers/WelcomeController.java.txt",
                    "README.md",
                ),
            ),
            "inertia" to mapOf(
                "kotlin" to listOf(
                    "build.gradle.kts.txt",
                    "bootstrap/App.kt.txt",
                    "app/Http/Controllers/WelcomeController.kt.txt",
                    "app/Http/Middleware/HandleInertia.kt.txt",
                    "routes/web.kt.txt",
                    "README.md",
                ),
                "java" to listOf(
                    "build.gradle.kts.txt",
                    "bootstrap/App.java.txt",
                    "app/Http/Controllers/WelcomeController.java.txt",
                    "app/Http/Middleware/HandleInertia.java.txt",
                    "routes/WebRoutes.java.txt",
                    "README.md",
                ),
            ),
            "brew" to mapOf(
                "kotlin" to listOf(
                    "build.gradle.kts.txt",
                    "bootstrap/App.kt.txt",
                    "app/Http/Controllers/WelcomeController.kt.txt",
                    "app/Brew/Counter.kt.txt",
                    "routes/web.kt.txt",
                    "README.md",
                ),
                "java" to listOf(
                    "build.gradle.kts.txt",
                    "bootstrap/App.java.txt",
                    "app/Http/Controllers/WelcomeController.java.txt",
                    "app/Brew/Counter.java.txt",
                    "routes/WebRoutes.java.txt",
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
