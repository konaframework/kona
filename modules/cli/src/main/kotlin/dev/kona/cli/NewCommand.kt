package dev.kona.cli

import java.nio.file.Files
import java.nio.file.Path

/**
 * `kona new <name>` — scaffolds a self-contained Gradle application with a local `./grind` CLI.
 */
class NewCommand(
    private val versions: KonaVersions = KonaVersions.load(),
) {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        val name = positional.firstOrNull()
            ?: throw KonaCliException("Usage: kona new <name> [--lang=kotlin|java] [--kona-version=x.y.z]")
        val lang = options["lang"] ?: "kotlin"
        if (lang !in SKELETONS) {
            throw KonaCliException("--lang must be kotlin or java")
        }
        val target = Path.of(name).toAbsolutePath().normalize()
        if (Files.exists(target)) {
            throw KonaCliException("Directory already exists: $target")
        }
        val variables = mapOf(
            "appName" to target.fileName.toString(),
            "konaVersion" to options.getOrDefault("kona-version", versions.kona),
            "kotlinVersion" to versions.kotlin,
        )
        Files.createDirectories(target)
        copySkeleton(lang, target, variables)
        target.resolve("grind").toFile().setExecutable(true)
        target.resolve("gradlew").toFile().setExecutable(true)
        println("Created Kona application at $target ($lang)")
        println("  cd ${target.fileName}")
        println("  ./grind serve")
        return 0
    }

    internal fun copySkeleton(lang: String, target: Path, variables: Map<String, String>) {
        val files = SKELETONS[lang] ?: throw KonaCliException("Unknown skeleton: $lang")
        COMMON_FILES.forEach { copyResource("skeletons/common/$it", target.resolve(destination(it)), variables) }
        files.forEach { copyResource("skeletons/$lang/$it", target.resolve(destination(it)), variables) }
        listOf("app/Http/Controllers", "app/Providers", "config", "resources", "routes").forEach {
            Files.createDirectories(target.resolve(it))
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
