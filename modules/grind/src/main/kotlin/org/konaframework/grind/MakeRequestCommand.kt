package org.konaframework.grind

import java.nio.file.Path

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
        write(base, simple, Names.lang(base, options))
        return 0
    }

    companion object {
        internal fun write(base: Path, className: String, lang: String) {
            Names.write(
                base,
                Names.sourceFile(lang, "app/http/requests", className),
                "Form request",
                requestStub(className, lang),
            )
        }

        private fun requestStub(name: String, lang: String): String = if (lang == "java") {
            lines(
                "package app.http.requests;",
                "",
                "import org.konaframework.http.FormRequest;",
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
                "import org.konaframework.http.FormRequest",
                "",
                "class $name : FormRequest() {",
                "    override fun rules(): Map<String, List<String>> = emptyMap()",
                "}",
            )
        }
    }
}
