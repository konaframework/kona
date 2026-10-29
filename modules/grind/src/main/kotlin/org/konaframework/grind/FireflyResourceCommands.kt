package org.konaframework.grind

/**
 * `grind make:firefly-resource <Model> [--simple] [--generate]` writes one grouped folder:
 *
 * ```
 * app/panels/{panel}/resources/{model}/
 *   {Model}Resource.kt|.java
 *   pages/…
 * ```
 *
 * Without `--generate`, form/table/infolist stub a single `name` field (no DB).
 * With `--generate`, boots the app, loads `app.models.{Model}`, introspects its table,
 * and emits components for the columns (#325). Grouping under the lowercase model name
 * keeps the Resource class FQN clear of any package segment (#321 / #323).
 */
class MakeFireflyResourceCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.size != 1) {
            throw GrindException(
                "Usage: grind make:firefly-resource <Model> [--simple] [--generate] [--panel=] [--lang=]",
            )
        }
        val input = positional.first()
        Names.requireClassName("resource", input)
        val model = Names.modelNameFromResourceInput(input)
        val className = Names.resourceClassName(input)
        Names.requireClassName("model", model)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val simple = options["simple"] == "true"
        val generate = options["generate"] == "true"
        val panel = panelIdFromOptions(options)
        val modelPkg = model.lowercase()
        val resourceDir = "app/panels/$panel/resources/$modelPkg"
        val pkg = "app.panels.$panel.resources.$modelPkg"
        val pagesPkg = "$pkg.pages"
        val pagesDir = "$resourceDir/pages"
        val fields = if (generate) {
            generateFields(options, model)
        } else {
            FireflyResourceGenerate.stubName()
        }
        Names.write(
            base,
            Names.sourceFile(lang, resourceDir, className),
            "Firefly resource",
            resourceSource(className, model, lang, simple, pkg, pagesPkg, fields),
        )
        writeResourcePages(base, lang, model, simple, pagesDir, pagesPkg)
        return 0
    }
}

internal fun generateFields(options: Map<String, String>, model: String): ResourceFieldSet =
    MigrateCommand.withApplication(options) { app, loader ->
        MigrateCommand.withDatabase(app) { database ->
            val instance = FireflyResourceGenerate.loadModel(loader, model)
            FireflyResourceGenerate.fromModel(instance, database, options["database"])
        }
    }

private fun writeResourcePages(
    base: java.nio.file.Path,
    lang: String,
    model: String,
    simple: Boolean,
    pagesDir: String,
    pagesPkg: String,
) {
    val specs = if (simple) {
        listOf(PageStub("Manage$model", "ManagePage", "manage"))
    } else {
        listOf(
            PageStub("List$model", "ListPage", "list"),
            PageStub("Create$model", "CreatePage", "create"),
            PageStub("Edit$model", "EditPage", "edit"),
            PageStub("View$model", "ViewPage", "view"),
        )
    }
    for (stub in specs) {
        Names.write(
            base,
            Names.sourceFile(lang, pagesDir, stub.className),
            "Firefly resource page",
            pageSource(lang, pagesPkg, stub),
        )
    }
}

private data class PageStub(val className: String, val baseClass: String, val slug: String)

private fun pageSource(lang: String, pkg: String, stub: PageStub): String =
    if (lang == "java") {
        lines(
            "package $pkg;",
            "",
            "import org.konaframework.firefly.${stub.baseClass};",
            "",
            "public class ${stub.className} extends ${stub.baseClass} {",
            "    public ${stub.className}() {",
            "        super(\"${stub.slug}\");",
            "    }",
            "}",
        )
    } else {
        lines(
            "package $pkg",
            "",
            "import org.konaframework.firefly.${stub.baseClass}",
            "",
            "class ${stub.className} : ${stub.baseClass}(\"${stub.slug}\")",
        )
    }

internal fun resourceSource(
    className: String,
    model: String,
    lang: String,
    simple: Boolean,
    pkg: String,
    pagesPkg: String,
    fields: ResourceFieldSet,
): String {
    val slug = model.lowercase()
    if (lang == "java" && simple) return javaSimple(className, model, slug, pkg, pagesPkg, fields)
    if (lang == "java") return javaFull(className, model, slug, pkg, pagesPkg, fields)
    if (simple) return kotlinSimple(className, model, slug, pkg, pagesPkg, fields)
    return kotlinFull(className, model, slug, pkg, pagesPkg, fields)
}

private fun importLines(lang: String, fields: ResourceFieldSet, extra: List<String>): List<String> {
    val all = (extra + fields.imports()).distinct().sorted()
    return if (lang == "java") all.map { "import $it;" } else all.map { "import $it" }
}

private fun kotlinComponents(items: List<GeneratedComponent>): String =
    items.joinToString(",\n            ") { it.callExpr() }

private fun javaComponents(items: List<GeneratedComponent>): String =
    items.joinToString(",\n            ") { it.callExpr() }

private fun kotlinSimple(
    className: String,
    model: String,
    slug: String,
    pkg: String,
    pagesPkg: String,
    fields: ResourceFieldSet,
): String {
    val imports = importLines(
        "kotlin",
        fields,
        listOf(
            "$pagesPkg.Manage$model",
            "org.konaframework.firefly.Resource",
            "org.konaframework.firefly.schemas.Schema",
            "org.konaframework.firefly.tables.Table",
            "org.konaframework.firefly.widgets.Page",
        ),
    )
    return lines(
        "package $pkg",
        "",
        *imports.toTypedArray(),
        "",
        "class $className : Resource(\"$slug\") {",
        "    init {",
        "        label(\"$model\")",
        "        simple()",
        "        model(app.models.$model::class.java)",
        "    }",
        "",
        "    override fun form(schema: Schema): Schema =",
        "        ${schemaColumnsCall(fields.formColumns)}.components(",
        "            ${kotlinComponents(fields.form)},",
        "        )",
        "",
        "    override fun infolist(schema: Schema): Schema =",
        "        ${schemaColumnsCall(fields.infolistColumns)}.components(",
        "            ${kotlinComponents(fields.infolist)},",
        "        )",
        "",
        "    override fun table(table: Table<*, *>): Table<*, *> =",
        "        table.columns(",
        "            ${kotlinComponents(fields.table)},",
        "        )",
        "",
        "    override fun getPages(): Map<String, Page> = mapOf(",
        "        \"manage\" to Manage$model(),",
        "    )",
        "}",
    )
}

private fun kotlinFull(
    className: String,
    model: String,
    slug: String,
    pkg: String,
    pagesPkg: String,
    fields: ResourceFieldSet,
): String {
    val imports = importLines(
        "kotlin",
        fields,
        listOf(
            "$pagesPkg.Create$model",
            "$pagesPkg.Edit$model",
            "$pagesPkg.List$model",
            "$pagesPkg.View$model",
            "org.konaframework.firefly.RelationManager",
            "org.konaframework.firefly.Resource",
            "org.konaframework.firefly.schemas.Schema",
            "org.konaframework.firefly.tables.Table",
            "org.konaframework.firefly.tables.TextColumn",
            "org.konaframework.firefly.widgets.Page",
        ),
    )
    return lines(
        "package $pkg",
        "",
        *imports.toTypedArray(),
        "",
        "class $className : Resource(\"$slug\") {",
        "    init {",
        "        label(\"$model\")",
        "        model(app.models.$model::class.java)",
        "    }",
        "",
        "    override fun form(schema: Schema): Schema =",
        "        ${schemaColumnsCall(fields.formColumns)}.components(",
        "            ${kotlinComponents(fields.form)},",
        "        )",
        "",
        "    override fun infolist(schema: Schema): Schema =",
        "        ${schemaColumnsCall(fields.infolistColumns)}.components(",
        "            ${kotlinComponents(fields.infolist)},",
        "        )",
        "",
        "    override fun table(table: Table<*, *>): Table<*, *> =",
        "        table.columns(",
        "            ${kotlinComponents(fields.table)},",
        "        )",
        "",
        "    override fun getRelations(): List<RelationManager> = listOf(",
        "        RelationManager(\"related\")",
        "            .related(app.models.$model::class.java)",
        "            .titleAttribute(\"id\")",
        "            .table(Table.memory().columns(TextColumn.make(\"id\"))),",
        "    )",
        "",
        "    override fun getPages(): Map<String, Page> = mapOf(",
        "        \"list\" to List$model(),",
        "        \"create\" to Create$model(),",
        "        \"edit\" to Edit$model(),",
        "        \"view\" to View$model(),",
        "    )",
        "}",
    )
}

private fun javaSimple(
    className: String,
    model: String,
    slug: String,
    pkg: String,
    pagesPkg: String,
    fields: ResourceFieldSet,
): String {
    val imports = importLines(
        "java",
        fields,
        listOf(
            "$pagesPkg.Manage$model",
            "org.konaframework.firefly.Resource",
            "org.konaframework.firefly.schemas.Schema",
            "org.konaframework.firefly.tables.Table",
            "org.konaframework.firefly.widgets.Page",
            "java.util.LinkedHashMap",
            "java.util.Map",
        ),
    )
    return lines(
        "package $pkg;",
        "",
        *imports.toTypedArray(),
        "",
        "public class $className extends Resource {",
        "    public $className() {",
        "        super(\"$slug\");",
        "        label(\"$model\");",
        "        simple();",
        "        model(app.models.$model.class);",
        "    }",
        "",
        "    @Override",
        "    public Schema form(Schema schema) {",
        "        return ${schemaColumnsCall(fields.formColumns)}.components(",
        "            ${javaComponents(fields.form)}",
        "        );",
        "    }",
        "",
        "    @Override",
        "    public Schema infolist(Schema schema) {",
        "        return ${schemaColumnsCall(fields.infolistColumns)}.components(",
        "            ${javaComponents(fields.infolist)}",
        "        );",
        "    }",
        "",
        "    @Override",
        "    public Table<?, ?> table(Table<?, ?> table) {",
        "        return Table.memory().columns(",
        "            ${javaComponents(fields.table)}",
        "        );",
        "    }",
        "",
        "    @Override",
        "    public Map<String, Page> getPages() {",
        "        Map<String, Page> pages = new LinkedHashMap<>();",
        "        pages.put(\"manage\", new Manage$model());",
        "        return pages;",
        "    }",
        "}",
    )
}

private fun javaFull(
    className: String,
    model: String,
    slug: String,
    pkg: String,
    pagesPkg: String,
    fields: ResourceFieldSet,
): String {
    val imports = importLines(
        "java",
        fields,
        listOf(
            "$pagesPkg.Create$model",
            "$pagesPkg.Edit$model",
            "$pagesPkg.List$model",
            "$pagesPkg.View$model",
            "org.konaframework.firefly.RelationManager",
            "org.konaframework.firefly.Resource",
            "org.konaframework.firefly.schemas.Schema",
            "org.konaframework.firefly.tables.Table",
            "org.konaframework.firefly.tables.TextColumn",
            "org.konaframework.firefly.widgets.Page",
            "java.util.LinkedHashMap",
            "java.util.Map",
        ),
    )
    return lines(
        "package $pkg;",
        "",
        *imports.toTypedArray(),
        "",
        "public class $className extends Resource {",
        "    public $className() {",
        "        super(\"$slug\");",
        "        label(\"$model\");",
        "        model(app.models.$model.class);",
        "    }",
        "",
        "    @Override",
        "    public Schema form(Schema schema) {",
        "        return ${schemaColumnsCall(fields.formColumns)}.components(",
        "            ${javaComponents(fields.form)}",
        "        );",
        "    }",
        "",
        "    @Override",
        "    public Schema infolist(Schema schema) {",
        "        return ${schemaColumnsCall(fields.infolistColumns)}.components(",
        "            ${javaComponents(fields.infolist)}",
        "        );",
        "    }",
        "",
        "    @Override",
        "    public Table<?, ?> table(Table<?, ?> table) {",
        "        return Table.memory().columns(",
        "            ${javaComponents(fields.table)}",
        "        );",
        "    }",
        "",
        "    @Override",
        "    public java.util.List<RelationManager> getRelations() {",
        "        return java.util.List.of(",
        "            new RelationManager(\"related\")",
        "                .related(app.models.$model.class)",
        "                .titleAttribute(\"id\")",
        "                .table(Table.memory().columns(TextColumn.make(\"id\")))",
        "        );",
        "    }",
        "",
        "    @Override",
        "    public Map<String, Page> getPages() {",
        "        Map<String, Page> pages = new LinkedHashMap<>();",
        "        pages.put(\"list\", new List$model());",
        "        pages.put(\"create\", new Create$model());",
        "        pages.put(\"edit\", new Edit$model());",
        "        pages.put(\"view\", new View$model());",
        "        return pages;",
        "    }",
        "}",
    )
}

/** Emit `schema.columns()` when count is 2 (no-arg default); otherwise `schema.columns(n)`. */
internal fun schemaColumnsCall(count: Int): String =
    if (count == 2) "schema.columns()" else "schema.columns($count)"

