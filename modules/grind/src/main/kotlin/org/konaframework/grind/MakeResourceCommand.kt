package org.konaframework.grind

/**
 * `grind make:resource` — generate a JsonResource stub (optional collection helper).
 */
class MakeResourceCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:resource <Name> [--collection] [--lang=kotlin|java]")
        }
        val name = positional.first()
        if (!CLASS_NAME.matches(name)) {
            throw GrindException("Invalid resource name [$name]: expected a class name like UserResource")
        }
        val simple = if (name.endsWith("Resource")) name else name + "Resource"
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val collection = options["collection"] == "true"
        val relative = Names.sourceFile(lang, "app/http/resources", simple)
        Names.write(base, relative, "Resource", resourceStub(simple, lang, collection))
        return 0
    }

    private fun resourceStub(name: String, lang: String, collection: Boolean): String =
        if (lang == "java") javaStub(name, collection) else kotlinStub(name, collection)

    private fun kotlinStub(name: String, collection: Boolean): String {
        val imports = buildString {
            appendLine("import org.konaframework.http.JsonResource")
            appendLine("import org.konaframework.http.Request")
            if (collection) {
                appendLine("import org.konaframework.http.ResourceCollection")
            }
        }
        val companion = if (collection) {
            """

                companion object {
                    fun collection(items: Iterable<Any?>): ResourceCollection =
                        JsonResource.collection(items.map { $name(it) })
                }
            """
        } else {
            ""
        }
        return """
            package app.http.resources

            $imports
            class $name(resource: Any?) : JsonResource(resource) {
                override fun toArray(request: Request): Map<String, Any?> =
                    mapOf(
                        "value" to resource,
                    )
                $companion
            }
        """.trimIndent() + "\n"
    }

    private fun javaStub(name: String, collection: Boolean): String {
        val companion = if (collection) {
            """

                public static ResourceCollection collection(Iterable<?> items) {
                    java.util.ArrayList<JsonResource> resources = new java.util.ArrayList<>();
                    for (Object item : items) {
                        resources.add(new $name(item));
                    }
                    return JsonResource.collection(resources);
                }
            """
        } else {
            ""
        }
        val imports = buildString {
            appendLine("import org.konaframework.http.JsonResource;")
            appendLine("import org.konaframework.http.Request;")
            if (collection) {
                appendLine("import org.konaframework.http.ResourceCollection;")
            }
            appendLine("import java.util.HashMap;")
            appendLine("import java.util.Map;")
        }
        return """
            package app.http.resources;

            $imports
            public class $name extends JsonResource {
                public $name(Object resource) {
                    super(resource);
                }

                @Override
                public Map<String, Object> toArray(Request request) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("value", getResource());
                    return data;
                }
                $companion
            }
        """.trimIndent() + "\n"
    }
}

private val CLASS_NAME = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
