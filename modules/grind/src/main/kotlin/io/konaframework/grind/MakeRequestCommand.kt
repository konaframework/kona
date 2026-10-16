package io.konaframework.grind

/**
 * `grind make:request <Name> [--lang=kotlin|java]` → form-request stub under `app.http.requests`.
 */
class MakeRequestCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:request <Name> [--lang=kotlin|java]")
        }
        val name = positional.first()
        Names.requireClassName("request", name)
        val simple = if (name.endsWith("Request")) name else name + "Request"
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val relative = Names.sourceFile(lang, "app/http/requests", simple)
        Names.write(base, relative, "Form request", requestStub(simple, lang))
        return 0
    }

    private fun requestStub(name: String, lang: String): String = if (lang == "java") {
        lines(
            "package app.http.requests;",
            "",
            "import io.konaframework.http.FormRequest;",
            "import java.util.List;",
            "import java.util.Map;",
            "",
            "public class $name extends FormRequest {",
            "    @Override",
            "    public Map<String, List<String>> rules() {",
            "        return Map.of();",
            "    }",
            "}",
        )
    } else {
        lines(
            "package app.http.requests",
            "",
            "import io.konaframework.http.FormRequest",
            "",
            "class $name : FormRequest() {",
            "    override fun rules(): Map<String, List<String>> = emptyMap()",
            "}",
        )
    }
}
