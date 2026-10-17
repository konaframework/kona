package org.konaframework.grind

import org.konaframework.cadence.Cadence
import java.nio.file.Path

/**
 * `grind make:model <Name> [--migration] [--factory] [--seeder] [--all] [--lang=kotlin|java]`.
 *
 * Writes `src/main/.../app/models/<Name>`; flags also write migrations/factories/seeders
 * under the same language root (`database.migrations`, etc.).
 */
class MakeModelCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:model <Name> [--migration] [--factory] [--seeder] [--all]")
        }
        val name = positional.first()
        Names.requireClassName("model", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val all = options["all"] == "true"
        Names.write(base, Names.sourceFile(lang, "app/models", name), "Model", modelStub(name, lang))
        if (all || options["migration"] == "true") {
            val table = Cadence.tableNameFor(name)
            MakeMigrationCommand().write(base, "Create${Names.studly(table)}Table", lang, MakeMigrationCommand.Mode.CREATE, table)
        }
        if (all || options["factory"] == "true") {
            MakeFactoryCommand.write(base, name + "Factory", name, lang)
        }
        if (all || options["seeder"] == "true") {
            MakeSeederCommand.write(base, name + "Seeder", lang)
        }
        return 0
    }

    private fun modelStub(name: String, lang: String): String = if (lang == "java") {
        lines(
            "package app.models;",
            "",
            "import org.konaframework.cadence.Cadence;",
            "import org.konaframework.cadence.CadenceCompanion;",
            "import java.util.Set;",
            "",
            "public class $name extends Cadence<$name> {",
            "    public static final CadenceCompanion<$name> QUERY = Cadence.of($name.class);",
            "",
            "    @Override",
            "    public Set<String> getFillable() {",
            "        return Set.of();",
            "    }",
            "}",
        )
    } else {
        lines(
            "package app.models",
            "",
            "import org.konaframework.cadence.Cadence",
            "import org.konaframework.cadence.CadenceCompanion",
            "",
            "class $name : Cadence<$name>() {",
            "    override val fillable = setOf<String>()",
            "",
            "    companion object : CadenceCompanion<$name>($name::class.java)",
            "}",
        )
    }
}

/** `grind make:factory <Name>Factory [--model=Name] [--lang=kotlin|java]`. */
class MakeFactoryCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:factory <Name>Factory [--model=Name]")
        }
        val name = positional.first()
        Names.requireClassName("factory", name)
        val className = if (name.endsWith("Factory")) name else name + "Factory"
        val model = options["model"] ?: className.removeSuffix("Factory")
        Names.requireClassName("model", model)
        val base = Names.base(options)
        write(base, className, model, Names.lang(base, options))
        return 0
    }

    companion object {
        internal fun write(base: Path, className: String, model: String, lang: String) {
            val content = if (lang == "java") {
                lines(
                    "package database.factories;",
                    "",
                    "import app.models.$model;",
                    "import org.konaframework.cadence.factory.Factory;",
                    "import java.util.Map;",
                    "",
                    "public class $className extends Factory<$model> {",
                    "    public $className() {",
                    "        super($model.class);",
                    "    }",
                    "",
                    "    @Override",
                    "    public Map<String, Object> definition() {",
                    "        return Map.of(",
                    "            // \"name\", \"$model \" + getIndex()",
                    "        );",
                    "    }",
                    "}",
                )
            } else {
                lines(
                    "package database.factories",
                    "",
                    "import app.models.$model",
                    "import org.konaframework.cadence.factory.Factory",
                    "",
                    "class $className : Factory<$model>($model::class.java) {",
                    "    override fun definition(): Map<String, Any?> = mapOf(",
                    "        // \"name\" to \"$model \$index\",",
                    "    )",
                    "}",
                )
            }
            Names.write(base, Names.sourceFile(lang, "database/factories", className), "Factory", content)
        }
    }
}

/** `grind make:seeder <Name>Seeder [--lang=kotlin|java]`. */
class MakeSeederCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:seeder <Name>Seeder")
        }
        val name = positional.first()
        Names.requireClassName("seeder", name)
        val base = Names.base(options)
        write(base, if (name.endsWith("Seeder")) name else name + "Seeder", Names.lang(base, options))
        return 0
    }

    companion object {
        internal fun write(base: Path, className: String, lang: String) {
            val content = if (lang == "java") {
                lines(
                    "package database.seeders;",
                    "",
                    "import org.konaframework.cadence.seeding.Seeder;",
                    "",
                    "public class $className extends Seeder {",
                    "    @Override",
                    "    public void run() {",
                    "        // call(OtherSeeder.class);",
                    "    }",
                    "}",
                )
            } else {
                lines(
                    "package database.seeders",
                    "",
                    "import org.konaframework.cadence.seeding.Seeder",
                    "",
                    "class $className : Seeder() {",
                    "    override fun run() {",
                    "        // call(OtherSeeder::class.java)",
                    "    }",
                    "}",
                )
            }
            Names.write(base, Names.sourceFile(lang, "database/seeders", className), "Seeder", content)
        }
    }
}
