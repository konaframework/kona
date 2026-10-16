package io.konaframework.grind

/**
 * `grind make:middleware <Name> [--lang=kotlin|java]` → `src/main/.../app/http/middleware/`.
 */
class MakeMiddlewareCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:middleware <Name> [--lang=kotlin|java]")
        }
        val name = positional.first()
        Names.requireClassName("middleware", name)
        val simple = if (name.endsWith("Middleware")) name else name + "Middleware"
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val relative = Names.sourceFile(lang, "app/http/middleware", simple)
        Names.write(base, relative, "Middleware", middlewareStub(simple, lang))
        return 0
    }

    private fun middlewareStub(name: String, lang: String): String = if (lang == "java") {
        lines(
            "package app.http.middleware;",
            "",
            "import io.konaframework.http.Handler;",
            "import io.konaframework.http.Middleware;",
            "import io.konaframework.http.Request;",
            "import io.konaframework.http.Response;",
            "",
            "public class $name implements Middleware {",
            "    @Override",
            "    public Response handle(Request request, Handler next) {",
            "        return next.handle(request);",
            "    }",
            "}",
        )
    } else {
        lines(
            "package app.http.middleware",
            "",
            "import io.konaframework.http.Handler",
            "import io.konaframework.http.Middleware",
            "import io.konaframework.http.Request",
            "import io.konaframework.http.Response",
            "",
            "class $name : Middleware {",
            "    override fun handle(request: Request, next: Handler): Response =",
            "        next.handle(request)",
            "}",
        )
    }
}
