package org.konaframework.wire.console

import org.konaframework.wire.ComponentRegistry
import org.konaframework.grind.ConsoleCommand
import java.nio.file.Files
import java.nio.file.Path

/**
 * `./grind make:form PostForm` — scaffolds a Wire form object under `app.wire.forms`.
 */
class MakeFormCommand : ConsoleCommand() {
    override val signature: String =
        "make:form {name : Form class name (PostForm, post-form)} " +
            "{--lang= : kotlin or java} {--base= : Project root} {--force : Overwrite existing files}"

    override val description: String = "Create a Wire form object"

    override fun handle(): Int {
        val raw = argumentOrFail("name").trim()
        if (!NAME.matches(raw)) {
            error("Invalid form name [$raw]: use names like PostForm or post-form")
            return FAILURE
        }
        val base = Path.of(option("base", ".")).toAbsolutePath().normalize()
        val lang = option("lang") ?: detectLang(base)
        if (lang != "kotlin" && lang != "java") {
            error("Unknown --lang [$lang]; expected kotlin or java")
            return FAILURE
        }
        val className = ComponentRegistry.studly(raw)
        val pkg = "app.wire.forms"
        val ext = if (lang == "java") "java" else "kt"
        val root = if (lang == "java") "src/main/java" else "src/main/kotlin"
        val relative = "$root/${pkg.replace('.', '/')}/$className.$ext"
        val dest = base.resolve(relative)
        if (!optionBool("force") && Files.exists(dest)) {
            error("File already exists: $relative (use --force to overwrite)")
            return FAILURE
        }
        Files.createDirectories(dest.parent)
        Files.writeString(dest, source(lang, pkg, className))
        success("Created $relative")
        info("Declare it on a component: var form = $className()")
        return SUCCESS
    }

    private fun detectLang(base: Path): String {
        val java = base.resolve("src/main/java")
        if (!Files.isDirectory(java)) return "kotlin"
        return Files.walk(java).use { stream ->
            if (stream.anyMatch { it.toString().endsWith(".java") }) "java" else "kotlin"
        }
    }

    private fun source(lang: String, pkg: String, className: String): String =
        if (lang == "java") {
            """
            package $pkg;

            import org.konaframework.wire.Form;
            import org.konaframework.wire.Validate;

            public class $className extends Form {
                @Validate({"required"})
                public String title = "";

                @Validate({"required"})
                public String content = "";
            }
            """.trimIndent() + "\n"
        } else {
            """
            package $pkg

            import org.konaframework.wire.Form
            import org.konaframework.wire.Validate

            class $className : Form() {
                @Validate("required")
                var title: String = ""

                @Validate("required")
                var content: String = ""
            }
            """.trimIndent() + "\n"
        }

    companion object {
        private val NAME = Regex("^[A-Za-z][A-Za-z0-9-]*$")
    }
}
