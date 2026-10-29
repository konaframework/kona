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
 * [name] may be snake_case (`create_flights_table`) or PascalCase (`CreateFlightsTable`).
 * You do not type a timestamp — grind prefixes the **file** with UTC `yyyy_MM_dd_HHmmss`.
 * The JVM **class** stays PascalCase (`CreateFlightsTable`) so editors do not flag
 * underscores. Optional `{stamp}_` / `M{stamp}_` prefixes are still accepted.
 *
 * Writes `database/migrations/{stamp}_{snake_name}` and appends the class FQCN to
 * `META-INF/kona/migrations`. The file stem is the run order [org.konaframework.cadence.migrations.Migrator] uses.
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
        write(base, parsed.descriptive, lang, mode, table, parsed.snake, parsed.timestamp)
        return 0
    }

    internal fun write(
        base: Path,
        className: String,
        lang: String,
        mode: Mode,
        table: String?,
        snake: String = Names.snake(className),
        startTimestamp: String? = null,
    ) {
        if (classDefined(base, lang, className)) {
            throw GrindException("Migration class $className already exists")
        }
        val stem = nextFileStem(base, lang, snake, startTimestamp)
        Names.write(
            base,
            Names.sourceFile(lang, "database/migrations", stem),
            "Migration",
            if (lang == "java") javaStub(className, stem, mode, table) else kotlinStub(className, stem, mode, table),
        )
        appendDiscovery(base, "database.migrations.$className")
    }

    internal enum class Mode { CREATE, ALTER, BLANK }

    internal fun nextFileStem(base: Path, lang: String, snake: String, startTimestamp: String?): String {
        var instant = startInstant(startTimestamp)
        val destDir = base.resolve(Names.languageRoot(lang)).resolve("database/migrations")
        while (true) {
            val stem = MigrationOrder.fileStem(MigrationOrder.format(instant), snake)
            val dest = destDir.resolve("${stem}.${Names.extension(lang)}")
            if (!Files.exists(dest)) {
                return stem
            }
            instant = instant.plusSeconds(1)
        }
    }

    private fun classDefined(base: Path, lang: String, className: String): Boolean {
        val dir = base.resolve(Names.languageRoot(lang)).resolve("database/migrations")
        if (!Files.isDirectory(dir)) {
            return false
        }
        val pattern = Regex("""(?:public\s+|internal\s+)?class\s+${Regex.escape(className)}\b""")
        Files.list(dir).use { stream ->
            return stream
                .filter { it.fileName.toString().endsWith(".${Names.extension(lang)}") }
                .anyMatch { pattern.containsMatchIn(Files.readString(it)) }
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

    private fun kotlinStub(className: String, stem: String, mode: Mode, table: String?): String {
        val (up, down) = when (mode) {
            Mode.CREATE -> listOf(
                "schema.create(\"$table\") { table ->",
                "    table.id()",
                "    // table.string(\"name\")",
                "    // table.boolean(\"active\").default(false)",
                "    // table.date(\"due_on\").nullable()",
                "    // table.dateTime(\"published_at\").nullable()",
                "    table.timestamps()",
                "}",
            ) to listOf("schema.dropIfExists(\"$table\")")
            Mode.ALTER -> listOf(
                "schema.table(\"$table\") { table ->",
                "    // table.string(\"nickname\").nullable()",
                "    // table.date(\"labeled_on\").nullable()",
                "    // table.dateTime(\"reviewed_at\").nullable()",
                "}",
            ) to listOf("schema.table(\"$table\") { table ->", "    // table.dropColumn(\"nickname\")", "}")
            Mode.BLANK -> listOf(
                "// schema.create(\"table\") { table -> table.id(); table.date(\"due_on\"); table.dateTime(\"published_at\") }",
            ) to listOf("// schema.dropIfExists(\"table\")")
        }
        return lines(
            "package database.migrations",
            "",
            "import org.konaframework.cadence.migrations.Migration",
            "import org.konaframework.cadence.migrations.MigrationName",
            "import org.konaframework.cadence.schema.Schema",
            "",
            "@MigrationName(\"$stem\")",
            "class $className : Migration {",
            "    override fun up(schema: Schema) {",
        ) + body(up) + lines("    }", "", "    override fun down(schema: Schema) {") + body(down) + lines("    }", "}")
    }

    private fun javaStub(className: String, stem: String, mode: Mode, table: String?): String {
        val (up, down) = when (mode) {
            Mode.CREATE -> listOf(
                "schema.create(\"$table\", table -> {",
                "    table.id();",
                "    // table.string(\"name\");",
                "    // table.bool(\"active\").withDefault(false);",
                "    // table.date(\"due_on\").nullable();",
                "    // table.dateTime(\"published_at\").nullable();",
                "    table.timestamps();",
                "});",
            ) to listOf("schema.dropIfExists(\"$table\");")
            Mode.ALTER -> listOf(
                "schema.table(\"$table\", table -> {",
                "    // table.string(\"nickname\").nullable();",
                "    // table.date(\"labeled_on\").nullable();",
                "    // table.dateTime(\"reviewed_at\").nullable();",
                "});",
            ) to listOf("schema.table(\"$table\", table -> {", "    // table.dropColumn(\"nickname\");", "});")
            Mode.BLANK -> listOf("// schema.create(\"table\", table -> { table.id(); table.date(\"due_on\"); table.dateTime(\"published_at\"); });") to
                listOf("// schema.dropIfExists(\"table\");")
        }
        return lines(
            "package database.migrations;",
            "",
            "import org.konaframework.cadence.migrations.Migration;",
            "import org.konaframework.cadence.migrations.MigrationName;",
            "import org.konaframework.cadence.schema.Schema;",
            "",
            "@MigrationName(\"$stem\")",
            "class $className implements Migration {",
            "    @Override",
            "    public void up(Schema schema) {",
        ) + body(up) + lines("    }", "", "    @Override", "    public void down(Schema schema) {") + body(down) +
            lines("    }", "}")
    }

    private fun body(statements: List<String>): String = statements.joinToString("") { "        $it\n" }
}

private val CREATE = Regex("^create_(\\w+?)_table$")
private val ALTER = Regex("_(?:to|from|in)_(\\w+?)_table$")
