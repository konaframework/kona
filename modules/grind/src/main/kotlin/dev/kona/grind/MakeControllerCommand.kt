package dev.kona.grind

import java.nio.file.Files
import java.nio.file.Path

class MakeControllerCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:controller <Name> [--resource] [--lang=kotlin|java]")
        }
        val name = positional.first()
        if (!CLASS_NAME.matches(name)) {
            throw GrindException("Invalid controller name [$name]: expected a class name like UserController")
        }
        val simple = if (name.endsWith("Controller")) name else name + "Controller"
        val base = Path.of(options.getOrDefault("base", ".")).toAbsolutePath().normalize()
        val lang = options.getOrDefault("lang", detectLang(base))
        val resource = options["resource"] == "true"
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
        Files.writeString(dest, controllerStub(simple, lang, resource))
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

    private fun controllerStub(name: String, lang: String, resource: Boolean): String {
        if (lang == "java") {
            return if (resource) {
                """
                    package app.Http.Controllers;

                    import dev.kona.http.Request;
                    import dev.kona.http.Response;

                    public class $name {
                        public Response index(Request request) {
                            return Response.text("$name@index");
                        }

                        public Response create(Request request) {
                            return Response.text("$name@create");
                        }

                        public Response store(Request request) {
                            return Response.text("$name@store");
                        }

                        public Response show(Request request) {
                            return Response.text("$name@show:" + request.route("id"));
                        }

                        public Response edit(Request request) {
                            return Response.text("$name@edit:" + request.route("id"));
                        }

                        public Response update(Request request) {
                            return Response.text("$name@update:" + request.route("id"));
                        }

                        public Response destroy(Request request) {
                            return Response.text("$name@destroy:" + request.route("id"));
                        }
                    }
                """.trimIndent() + "\n"
            } else {
                """
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
        }
        return if (resource) {
            """
                package app.Http.Controllers

                import dev.kona.http.Request
                import dev.kona.http.Response

                class $name {
                    fun index(request: Request): Response = Response.text("$name@index")

                    fun create(request: Request): Response = Response.text("$name@create")

                    fun store(request: Request): Response = Response.text("$name@store")

                    fun show(request: Request): Response =
                        Response.text("$name@show:" + request.route("id"))

                    fun edit(request: Request): Response =
                        Response.text("$name@edit:" + request.route("id"))

                    fun update(request: Request): Response =
                        Response.text("$name@update:" + request.route("id"))

                    fun destroy(request: Request): Response =
                        Response.text("$name@destroy:" + request.route("id"))
                }
            """.trimIndent() + "\n"
        } else {
            """
                package app.Http.Controllers

                import dev.kona.http.Request
                import dev.kona.http.Response

                class $name {
                    fun index(request: Request): Response = Response.text("$name")
                }
            """.trimIndent() + "\n"
        }
    }
}

private val CLASS_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
