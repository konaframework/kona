package org.konaframework.grind

import org.konaframework.cadence.migrations.MigrationDiscovery
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * `grind make:migration <snake_name> [--create=table] [--table=table] [--lang=kotlin|java]`.
 *
 * Writes `src/main/{kotlin|java}/database/migrations/<StudlyName>` and appends the FQCN to
 * `META-INF/kona/migrations` so `./grind migrate` picks it up automatically.
 */
class MakeMigrationCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:migration <name> [--create=table] [--table=table]")
        }
        val name = positional.first()
        Names.requireSnakeName("migration", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val className = Names.studly(name)
        val (mode, table) = plan(name, options)
        write(base, className, lang, mode, table)
        return 0
    }

    internal fun write(base: Path, className: String, lang: String, mode: Mode, table: String?) {
        Names.write(
            base,
            Names.sourceFile(lang, "database/migrations", className),
            "Migration",
            if (lang == "java") javaStub(className, mode, table) else kotlinStub(className, mode, table),
        )
        appendDiscovery(base, "database.migrations.$className")
    }

    internal enum class Mode { CREATE, ALTER, BLANK }

    private fun plan(name: String, options: Map<String, String>): Pair<Mode, String?> {
        options["create"]?.let { return Mode.CREATE to it }
        options["table"]?.let { return Mode.ALTER to it }
        CREATE.matchEntire(name)?.let { return Mode.CREATE to it.groupValues[1] }
        ALTER.find(name)?.let { return Mode.ALTER to it.groupValues[1] }
        return Mode.BLANK to null
    }

    private fun appendDiscovery(base: Path, fqcn: String) {
        val meta = base.resolve("src/main/resources/${MigrationDiscovery.RESOURCE}")
        Files.createDirectories(meta.parent)
        val existing = if (Files.isRegularFile(meta)) Files.readString(meta) else ""
        if (existing.lineSequence().any { it.trim() == fqcn }) {
            return
        }
        val prefix = if (existing.isNotEmpty() && !existing.endsWith("\n")) "\n" else ""
        Files.writeString(
            meta,
            existing + prefix + fqcn + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        GrindIO(color = GrindIO.detectColor()).info("Registered in ${MigrationDiscovery.RESOURCE}")
    }

    private fun kotlinStub(className: String, mode: Mode, table: String?): String {
        val (up, down) = when (mode) {
            Mode.CREATE -> listOf("schema.create(\"$table\") {", "    id()", "    timestamps()", "}") to
                listOf("schema.dropIfExists(\"$table\")")
            Mode.ALTER -> listOf("schema.table(\"$table\") {", "    // string(\"nickname\").nullable()", "}") to
                listOf("schema.table(\"$table\") {", "    // dropColumn(\"nickname\")", "}")
            Mode.BLANK -> listOf("// schema.create(\"table\") { id() }") to listOf("// schema.dropIfExists(\"table\")")
        }
        return lines(
            "package database.migrations",
            "",
            "import org.konaframework.cadence.migrations.Migration",
            "import org.konaframework.cadence.schema.Schema",
            "",
            "class $className : Migration {",
            "    override fun up(schema: Schema) {",
        ) + body(up) + lines("    }", "", "    override fun down(schema: Schema) {") + body(down) + lines("    }", "}")
    }

    private fun javaStub(className: String, mode: Mode, table: String?): String {
        val (up, down) = when (mode) {
            Mode.CREATE -> listOf("schema.create(\"$table\", table -> {", "    table.id();", "    table.timestamps();", "});") to
                listOf("schema.dropIfExists(\"$table\");")
            Mode.ALTER -> listOf("schema.table(\"$table\", table -> {", "    // table.string(\"nickname\").nullable();", "});") to
                listOf("schema.table(\"$table\", table -> {", "    // table.dropColumn(\"nickname\");", "});")
            Mode.BLANK -> listOf("// schema.create(\"table\", table -> table.id());") to listOf("// schema.dropIfExists(\"table\");")
        }
        return lines(
            "package database.migrations;",
            "",
            "import org.konaframework.cadence.migrations.Migration;",
            "import org.konaframework.cadence.schema.Schema;",
            "",
            "public class $className implements Migration {",
            "    @Override",
            "    public void up(Schema schema) {",
        ) + body(up) + lines("    }", "", "    @Override", "    public void down(Schema schema) {") + body(down) +
            lines("    }", "}")
    }

    private fun body(statements: List<String>): String = statements.joinToString("") { "        $it\n" }
}

private val CREATE = Regex("^create_(\\w+?)_table$")
private val ALTER = Regex("_(?:to|from|in)_(\\w+?)_table$")
