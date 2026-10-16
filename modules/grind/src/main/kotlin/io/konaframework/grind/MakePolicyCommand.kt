package io.konaframework.grind

/**
 * `grind make:policy <Name> [--lang=kotlin|java] [--model=Post]` → `app/policies/`.
 */
class MakePolicyCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:policy <Name> [--lang=kotlin|java] [--model=Post]")
        }
        val name = positional.first()
        Names.requireClassName("policy", name)
        val simple = if (name.endsWith("Policy")) name else name + "Policy"
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val model = options["model"]
        val relative = Names.sourceFile(lang, "app/policies", simple)
        Names.write(base, relative, "Policy", policyStub(simple, lang, model))
        return 0
    }

    private fun policyStub(name: String, lang: String, model: String?): String = if (lang == "java") {
        val modelImport = if (model != null) "\nimport app.models.$model;" else ""
        lines(
            "package app.policies;",
            "",
            "import io.konaframework.auth.Authenticatable;",
            "import io.konaframework.auth.authorization.Policy;$modelImport",
            "",
            "public class $name implements Policy {",
            "    @Override",
            "    public boolean authorize(Authenticatable user, String ability, Object model) {",
            "        return false;",
            "    }",
            "}",
        )
    } else {
        val modelImport = if (model != null) "\nimport app.models.$model" else ""
        lines(
            "package app.policies",
            "",
            "import io.konaframework.auth.Authenticatable",
            "import io.konaframework.auth.authorization.Policy$modelImport",
            "",
            "class $name : Policy {",
            "    override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean =",
            "        false",
            "}",
        )
    }
}
