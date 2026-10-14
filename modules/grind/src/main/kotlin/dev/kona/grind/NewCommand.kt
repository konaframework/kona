package dev.kona.grind

import java.nio.file.Files
import java.nio.file.Path

class NewCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        val name = positional.firstOrNull()
            ?: throw GrindException("Usage: grind new <name> [--lang=kotlin|java]")
        val lang = options["lang"] ?: "kotlin"
        if (lang != "kotlin" && lang != "java") {
            throw GrindException("--lang must be kotlin or java")
        }
        val target = Path.of(name).toAbsolutePath().normalize()
        if (Files.exists(target)) {
            throw GrindException("Directory already exists: $target")
        }
        Files.createDirectories(target)
        copySkeleton(lang, target, target.fileName.toString())
        println("Created Kona application at $target ($lang)")
        return 0
    }

    internal fun copySkeleton(lang: String, target: Path, appName: String) {
        val resourceRoot = "skeletons/$lang"
        val classLoader = NewCommand::class.java.classLoader
        val files = listSkeletonFiles(lang)
        files.forEach { relative ->
            val dest = target.resolve(relative.removeSuffix(".txt"))
            Files.createDirectories(dest.parent)
            val resource = "$resourceRoot/$relative"
            val url = classLoader.getResource(resource)
            if (url == null) {
                throw GrindException("Missing skeleton resource: $resource")
            }
            val content = url.readText().replace("{{appName}}", appName)
            Files.writeString(dest, content)
        }
        Files.createDirectories(target.resolve("app/Http/Controllers"))
        Files.createDirectories(target.resolve("routes"))
        Files.createDirectories(target.resolve("config"))
    }

    private fun listSkeletonFiles(lang: String): List<String> = when (lang) {
        "kotlin" -> listOf(
            "README.md",
            ".env",
            "routes/web.kt.txt",
            "app/Http/Controllers/WelcomeController.kt.txt",
        )
        "java" -> listOf(
            "README.md",
            ".env",
            "routes/WebRoutes.java.txt",
            "app/Http/Controllers/WelcomeController.java.txt",
        )
        else -> listOf("missing-file.txt")
    }
}
