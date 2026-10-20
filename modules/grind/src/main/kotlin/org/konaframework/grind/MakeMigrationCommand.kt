package org.konaframework.grind

import org.konaframework.cadence.migrations.MigrationDiscovery
import org.konaframework.cadence.migrations.MigrationOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * `grind make:migration <name> [--create=table] [--table=table] [--lang=kotlin|java]`.
 *
 * [name] may be snake_case (`create_flights_table`) or PascalCase (`CreateFlightsTable`),
 * optionally prefixed with a UTC stamp (`2026_10_20_141530_…` or `M2026_10_20_141530_…`).
 *
 * Writes `database/migrations/M{yyyy_MM_dd_HHmmss}_{DescriptiveName}` and appends the FQCN to
 * `META-INF/kona/migrations`. The stamp is the run order [Migrator] uses.
 */
class MakeMigrationCommand @JvmOverloads constructor(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:migration <name> [--create=table] [--table=table]")
        }
        val parsed = Names.parseMigrationInput(positional.first())
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val (mode, table) = plan(parsed.snake, options)
        val className = nextClassName(base, lang, parsed.descriptive, parsed.timestamp)
        write(base, className, lang, mode, table)
        return 0
    }

    internal fun write(base: Path, className: String, lang: String, mode: Mode, table: String?) {
        val resolved = if (MigrationOrder.timestampOf(className) != null) {
            className
        } else {
            nextClassName(base, lang, className, null)
        }
        Names.write(
            base,
            Names.sourceFile(lang, "database/migrations", resolved),
            "Migration",
            if (lang == "java") javaStub(resolved, mode, table) else kotlinStub(resolved, mode, table),
        )
        appendDiscovery(base, "database.migrations.$resolved")
    }

    internal enum class Mode { CREATE, ALTER, BLANK }

    internal fun nextClassName(base: Path, lang: String, descriptive: String, startTimestamp: String?): String {
        var instant = startInstant(startTimestamp)
        val destDir = base.resolve(Names.languageRoot(lang)).resolve("database/migrations")
        while (true) {
            val className = MigrationOrder.className(MigrationOrder.format(instant), descriptive)
            val dest = destDir.resolve("${className}.${Names.extension(lang)}")
            if (!Files.exists(dest)) {
                return className
            }
            instant = instant.plusSeconds(1)
        }
    }

    private fun startInstant(startTimestamp: String?): Instant {
        if (startTimestamp == null) {
            return clock.instant().truncatedTo(ChronoUnit.SECONDS)
        }
        return try {
            MigrationOrder.parse(startTimestamp)
        } catch (_: DateTimeException) {
            throw GrindException("Invalid migration timestamp [$startTimestamp]")
        }
    }

    private fun plan(snake: String, options: Map<String, String>): Pair<Mode, String?> {
        options["create"]?.let { return Mode.CREATE to it }
        options["table"]?.let { return Mode.ALTER to it }
        CREATE.matchEntire(snake)?.let { return Mode.CREATE to it.groupValues[1] }
        ALTER.find(snake)?.let { return Mode.ALTER to it.groupValues[1] }
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
