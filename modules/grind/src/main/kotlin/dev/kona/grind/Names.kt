package dev.kona.grind

import java.nio.file.Files
import java.nio.file.Path

internal object Names {
    private val CLASS_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val SNAKE_NAME = Regex("^[a-z][a-z0-9_]*$")

    fun requireClassName(kind: String, name: String) {
        if (!CLASS_NAME.matches(name)) {
            throw GrindException("Invalid $kind name [$name]: expected a class name like User")
        }
    }

    fun requireSnakeName(kind: String, name: String) {
        if (!SNAKE_NAME.matches(name)) {
            throw GrindException("Invalid $kind name [$name]: expected snake_case like create_users_table")
        }
    }

    /** `create_users_table` → `CreateUsersTable`. */
    fun studly(snake: String): String =
        snake.split('_').joinToString("") { it.take(1).uppercase() + it.drop(1) }

    fun base(options: Map<String, String>): Path = Path.of(options.getOrDefault("base", ".")).toAbsolutePath().normalize()

    /** `--lang=` wins; otherwise Java when any `.java` file exists under `src/main/java`, else Kotlin. */
    fun lang(base: Path, options: Map<String, String>): String {
        options["lang"]?.let { return it }
        val javaRoot = base.resolve("src/main/java")
        if (Files.isDirectory(javaRoot)) {
            Files.walk(javaRoot).use { stream ->
                if (stream.anyMatch { it.toString().endsWith(".java") }) {
                    return "java"
                }
            }
        }
        return "kotlin"
    }

    fun extension(lang: String): String = if (lang == "java") "java" else "kt"

    /** `src/main/kotlin` or `src/main/java`. */
    fun languageRoot(lang: String): String =
        if (lang == "java") "src/main/java" else "src/main/kotlin"

    /**
     * Relative path for a type under the standard JVM source root.
     * [packagePath] uses `/` separators matching the package (e.g. `app/http/controllers`).
     */
    fun sourceFile(lang: String, packagePath: String, fileName: String): String =
        "${languageRoot(lang)}/$packagePath/$fileName.${extension(lang)}"

    /** Crema / static views under the Maven resource root. */
    fun viewFile(dottedName: String): String =
        "src/main/resources/views/${dottedName.replace('.', '/')}.crema.html"

    /** Writes [content] to [base]/[relative], refusing to overwrite. */
    fun write(base: Path, relative: String, kind: String, content: String) {
        val dest = base.resolve(relative)
        if (Files.exists(dest)) {
            throw GrindException("$kind already exists: $dest")
        }
        Files.createDirectories(dest.parent)
        Files.writeString(dest, content)
        GrindIO(color = GrindIO.detectColor()).success("$kind created: $relative")
    }
}

internal fun lines(vararg lines: String): String = lines.joinToString("") { it + "\n" }
