package org.konaframework.grind

/**
 * `grind make:controller <Name> [--resource] [--api] [--requests] [--model=Name] [--lang=kotlin|java]`.
 *
 * `--api` writes an API resource controller (index/store/show/update/destroy — no create/edit).
 * `--requests` writes `Store{Model}Request` / `Update{Model}Request` and type-hints `store` / `update`.
 */
class MakeControllerCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException(
                "Usage: grind make:controller <Name> [--resource] [--api] [--requests] [--model=Name]",
            )
        }
        val name = positional.first()
        if (!CLASS_NAME.matches(name)) {
            throw GrindException("Invalid controller name [$name]: expected a class name like UserController")
        }
        val simple = if (name.endsWith("Controller")) name else name + "Controller"
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val api = options["api"] == "true"
        val resource = options["resource"] == "true" || api
        val requests = options["requests"] == "true"
        val model = options["model"] ?: simple.removeSuffix("Controller")
        if (requests) {
            Names.requireClassName("model", model)
            MakeRequestCommand.write(base, "Store${model}Request", lang)
            MakeRequestCommand.write(base, "Update${model}Request", lang)
        }
        val relative = Names.sourceFile(lang, "app/http/controllers", simple)
        Names.write(
            base,
            relative,
            "Controller",
            controllerStub(simple, lang, resource = resource, api = api, requests = requests, model = model),
        )
        return 0
    }

    private fun controllerStub(
        name: String,
        lang: String,
        resource: Boolean,
        api: Boolean,
        requests: Boolean,
        model: String,
    ): String {
        if (!resource) {
            return if (lang == "java") {
                lines(
                    "package app.http.controllers;",
                    "",
                    "import org.konaframework.http.Request;",
                    "import org.konaframework.http.Response;",
                    "",
                    "public class $name {",
                    "    public Response index(Request request) {",
                    "        return Response.text(\"$name\");",
                    "    }",
                    "}",
                )
            } else {
                lines(
                    "package app.http.controllers",
                    "",
                    "import org.konaframework.http.Request",
                    "import org.konaframework.http.Response",
                    "",
                    "class $name {",
                    "    fun index(request: Request): Response = Response.text(\"$name\")",
                    "}",
                )
            }
        }
        return if (lang == "java") {
            javaResource(name, api, requests, model)
        } else {
            kotlinResource(name, api, requests, model)
        }
    }

    private fun kotlinResource(name: String, api: Boolean, requests: Boolean, model: String): String {
        val storeParam = if (requests) "request: Store${model}Request" else "request: Request"
        val updateParam = if (requests) "request: Update${model}Request" else "request: Request"
        val parts = mutableListOf(
            "package app.http.controllers",
            "",
            "import org.konaframework.http.Request",
            "import org.konaframework.http.Response",
        )
        if (requests) {
            parts += "import app.http.requests.Store${model}Request"
            parts += "import app.http.requests.Update${model}Request"
        }
        parts += ""
        parts += "class $name {"
        parts += "    fun index(request: Request): Response = Response.text(\"$name@index\")"
        if (!api) {
            parts += ""
            parts += "    fun create(request: Request): Response = Response.text(\"$name@create\")"
        }
        parts += ""
        parts += "    fun store($storeParam): Response = Response.text(\"$name@store\")"
        parts += ""
        parts += "    fun show(request: Request): Response ="
        parts += "        Response.text(\"$name@show:\" + request.route(\"id\"))"
        if (!api) {
            parts += ""
            parts += "    fun edit(request: Request): Response ="
            parts += "        Response.text(\"$name@edit:\" + request.route(\"id\"))"
        }
        parts += ""
        parts += "    fun update($updateParam): Response ="
        parts += "        Response.text(\"$name@update:\" + request.route(\"id\"))"
        parts += ""
        parts += "    fun destroy(request: Request): Response ="
        parts += "        Response.text(\"$name@destroy:\" + request.route(\"id\"))"
        parts += "}"
        return lines(*parts.toTypedArray())
    }

    private fun javaResource(name: String, api: Boolean, requests: Boolean, model: String): String {
        val storeType = if (requests) "Store${model}Request" else "Request"
        val updateType = if (requests) "Update${model}Request" else "Request"
        val parts = mutableListOf(
            "package app.http.controllers;",
            "",
            "import org.konaframework.http.Request;",
            "import org.konaframework.http.Response;",
        )
        if (requests) {
            parts += "import app.http.requests.Store${model}Request;"
            parts += "import app.http.requests.Update${model}Request;"
        }
        parts += ""
        parts += "public class $name {"
        parts += "    public Response index(Request request) {"
        parts += "        return Response.text(\"$name@index\");"
        parts += "    }"
        if (!api) {
            parts += ""
            parts += "    public Response create(Request request) {"
            parts += "        return Response.text(\"$name@create\");"
            parts += "    }"
        }
        parts += ""
        parts += "    public Response store($storeType request) {"
        parts += "        return Response.text(\"$name@store\");"
        parts += "    }"
        parts += ""
        parts += "    public Response show(Request request) {"
        parts += "        return Response.text(\"$name@show:\" + request.route(\"id\"));"
        parts += "    }"
        if (!api) {
            parts += ""
            parts += "    public Response edit(Request request) {"
            parts += "        return Response.text(\"$name@edit:\" + request.route(\"id\"));"
            parts += "    }"
        }
        parts += ""
        parts += "    public Response update($updateType request) {"
        parts += "        return Response.text(\"$name@update:\" + request.route(\"id\"));"
        parts += "    }"
        parts += ""
        parts += "    public Response destroy(Request request) {"
        parts += "        return Response.text(\"$name@destroy:\" + request.route(\"id\"));"
        parts += "    }"
        parts += "}"
        return lines(*parts.toTypedArray())
    }
}

private val CLASS_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
