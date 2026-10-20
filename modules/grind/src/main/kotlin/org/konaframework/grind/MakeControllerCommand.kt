package org.konaframework.grind

/**
 * `grind make:controller <Name> [--resource] [--api] [--requests] [--model=Name] [--lang=kotlin|java]`.
 *
 * `--resource` / `--api` write a [org.konaframework.http.BaseResourceController] subclass so the
 * stub can be passed to `router.resource` / `apiResource`. `--api` omits `create` / `edit`
 * overrides (the base returns 404). `--requests` writes `Store{Model}Request` /
 * `Update{Model}Request` and calls `validate` from `store` / `update`.
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
        val parts = mutableListOf(
            "package app.http.controllers",
            "",
            "import org.konaframework.http.BaseResourceController",
            "import org.konaframework.http.Request",
            "import org.konaframework.http.Response",
        )
        if (requests) {
            parts += "import app.http.requests.Store${model}Request"
            parts += "import app.http.requests.Update${model}Request"
        }
        parts += ""
        parts += "class $name : BaseResourceController() {"
        parts += "    override fun index(request: Request): Response = Response.text(\"$name@index\")"
        if (!api) {
            parts += ""
            parts += "    override fun create(request: Request): Response = Response.text(\"$name@create\")"
        }
        parts += ""
        parts.addAll(kotlinMutator(name, "store", requests, "Store${model}Request", withId = false))
        parts += ""
        parts += "    override fun show(request: Request): Response ="
        parts += "        Response.text(\"$name@show:\" + request.route(\"id\"))"
        if (!api) {
            parts += ""
            parts += "    override fun edit(request: Request): Response ="
            parts += "        Response.text(\"$name@edit:\" + request.route(\"id\"))"
        }
        parts += ""
        parts.addAll(kotlinMutator(name, "update", requests, "Update${model}Request", withId = true))
        parts += ""
        parts += "    override fun destroy(request: Request): Response ="
        parts += "        Response.text(\"$name@destroy:\" + request.route(\"id\"))"
        parts += "}"
        return lines(*parts.toTypedArray())
    }

    private fun kotlinMutator(
        name: String,
        action: String,
        requests: Boolean,
        form: String,
        withId: Boolean,
    ): List<String> {
        val text = if (withId) {
            "Response.text(\"$name@$action:\" + request.route(\"id\"))"
        } else {
            "Response.text(\"$name@$action\")"
        }
        if (!requests) {
            return if (withId) {
                listOf(
                    "    override fun $action(request: Request): Response =",
                    "        $text",
                )
            } else {
                listOf("    override fun $action(request: Request): Response = $text")
            }
        }
        return listOf(
            "    override fun $action(request: Request): Response {",
            "        validate(request, $form())",
            "        return $text",
            "    }",
        )
    }

    private fun javaResource(name: String, api: Boolean, requests: Boolean, model: String): String {
        val parts = mutableListOf(
            "package app.http.controllers;",
            "",
            "import org.konaframework.http.BaseResourceController;",
            "import org.konaframework.http.Request;",
            "import org.konaframework.http.Response;",
        )
        if (requests) {
            parts += "import app.http.requests.Store${model}Request;"
            parts += "import app.http.requests.Update${model}Request;"
        }
        parts += ""
        parts += "public class $name extends BaseResourceController {"
        parts.addAll(javaAction(name, "index", form = "", withId = false))
        if (!api) {
            parts += ""
            parts.addAll(javaAction(name, "create", form = "", withId = false))
        }
        parts += ""
        parts.addAll(javaAction(name, "store", if (requests) "Store${model}Request" else "", withId = false))
        parts += ""
        parts.addAll(javaAction(name, "show", form = "", withId = true))
        if (!api) {
            parts += ""
            parts.addAll(javaAction(name, "edit", form = "", withId = true))
        }
        parts += ""
        parts.addAll(javaAction(name, "update", if (requests) "Update${model}Request" else "", withId = true))
        parts += ""
        parts.addAll(javaAction(name, "destroy", form = "", withId = true))
        parts += "}"
        return lines(*parts.toTypedArray())
    }

    private fun javaAction(
        name: String,
        action: String,
        form: String,
        withId: Boolean,
    ): List<String> {
        val text = if (withId) {
            "Response.text(\"$name@$action:\" + request.route(\"id\"))"
        } else {
            "Response.text(\"$name@$action\")"
        }
        val body = mutableListOf(
            "    @Override",
            "    public Response $action(Request request) {",
        )
        if (form.isNotEmpty()) {
            body += "        validate(request, new $form());"
        }
        body += "        return $text;"
        body += "    }"
        return body
    }
}

private val CLASS_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
