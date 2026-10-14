package dev.kona.grind

import java.nio.file.Files
import java.nio.file.Path

class MakeControllerCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:controller <Name>")
        }
        val name = positional.first()
        if (!CLASS_NAME.matches(name)) {
            throw GrindException("Invalid controller name [$name]: expected a class name like UserController")
        }
        val simple = if (name.endsWith("Controller")) name else name + "Controller"
        val base = Path.of(options.getOrDefault("base", ".")).toAbsolutePath().normalize()
        val lang = options.getOrDefault("lang", detectLang(base))
        val relative = if (lang == "java") {
            "app/Http/Controllers/$simple.java"
        } else {
            "app/Http/Controllers/$simple.kt"
        }
        val dest = base.resolve(relative)
        if (Files.exists(dest)) {
            throw GrindException("Controller already exists: $dest")
        }
        Files.createDirectories(dest.parent)
        Files.writeString(dest, controllerStub(simple, lang))
        println("Controller created: $relative")
        return 0
    }

    private fun detectLang(base: Path): String {
        val appDir = base.resolve("app")
        if (!Files.isDirectory(appDir)) {
            return "kotlin"
        }
        Files.walk(appDir).use { stream ->
            val hasJava = stream.anyMatch { it.toString().endsWith(".java") }
            if (hasJava) {
                return "java"
            }
        }
        return "kotlin"
    }

    private fun controllerStub(name: String, lang: String): String {
        if (lang == "java") {
            return """
                package app.Http.Controllers;

                import dev.kona.http.Request;
                import dev.kona.http.Response;

                public class $name {
                    public Response index(Request request) {
                        return Response.text("$name");
                    }
                }
            """.trimIndent() + "\n"
        }
        return """
            package app.Http.Controllers

            import dev.kona.http.Request
            import dev.kona.http.Response

            class $name {
                fun index(request: Request): Response = Response.text("$name")
            }
        """.trimIndent() + "\n"
    }
}

private val CLASS_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
