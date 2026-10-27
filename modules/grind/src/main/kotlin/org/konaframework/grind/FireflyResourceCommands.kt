package org.konaframework.grind

/**
 * `grind make:firefly-resource <Model> [--simple]` writes a `*Resource` the panel discovers.
 *
 * Full resources declare list/create/edit/view pages. The panel sidebar gets **one** entry
 * (the list). Create / edit / view / delete are page-header and row actions from [Resource.pages].
 */
class MakeFireflyResourceCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.size != 1) {
            throw GrindException("Usage: grind make:firefly-resource <Model> [--simple]  (writes ModelResource)")
        }
        val input = positional.first()
        Names.requireClassName("resource", input)
        val model = Names.modelNameFromResourceInput(input)
        val className = Names.resourceClassName(input)
        Names.requireClassName("model", model)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val simple = options["simple"] == "true"
        val panel = panelIdFromOptions(options)
        Names.write(
            base,
            Names.sourceFile(lang, "app/panels/$panel/resources", className),
            "Firefly resource",
            resourceSource(className, model, lang, simple, panel),
        )
        return 0
    }
}

private fun resourceSource(
    className: String,
    model: String,
    lang: String,
    simple: Boolean,
    panel: String,
): String {
    val slug = model.lowercase()
    val pkg = "app.panels.$panel.resources"
    if (lang == "java" && simple) return javaSimple(className, model, slug, pkg)
    if (lang == "java") return javaFull(className, model, slug, pkg)
    if (simple) return kotlinSimple(className, model, slug, pkg)
    return kotlinFull(className, model, slug, pkg)
}

private fun kotlinSimple(className: String, model: String, slug: String, pkg: String): String = lines(
    "package $pkg",
    "",
    "import org.konaframework.firefly.Resource",
    "import org.konaframework.firefly.forms.TextInput",
    "import org.konaframework.firefly.infolists.TextEntry",
    "import org.konaframework.firefly.schemas.Schema",
    "import org.konaframework.firefly.tables.Table",
    "import org.konaframework.firefly.tables.TextColumn",
    "",
    "class $className : Resource(\"$slug\") {",
    "    init {",
    "        label(\"$model\")",
    "        simple()",
    "        model(app.models.$model::class.java)",
    "    }",
    "",
    "    override fun form(schema: Schema): Schema =",
    "        schema.components(TextInput.make(\"name\"))",
    "",
    "    override fun infolist(schema: Schema): Schema =",
    "        schema.components(TextEntry.make(\"name\"))",
    "",
    "    override fun table(table: Table<*, *>): Table<*, *> =",
    "        table.columns(TextColumn.make(\"name\"))",
    "}",
)

private fun kotlinFull(className: String, model: String, slug: String, pkg: String): String = lines(
    "package $pkg",
    "",
    "import org.konaframework.firefly.CreatePage",
    "import org.konaframework.firefly.EditPage",
    "import org.konaframework.firefly.ListPage",
    "import org.konaframework.firefly.RelationManager",
    "import org.konaframework.firefly.Resource",
    "import org.konaframework.firefly.ViewPage",
    "import org.konaframework.firefly.forms.TextInput",
    "import org.konaframework.firefly.infolists.TextEntry",
    "import org.konaframework.firefly.schemas.Schema",
    "import org.konaframework.firefly.tables.Table",
    "import org.konaframework.firefly.tables.TextColumn",
    "import org.konaframework.firefly.widgets.Page",
    "",
    "class $className : Resource(\"$slug\") {",
    "    init {",
    "        label(\"$model\")",
    "        model(app.models.$model::class.java)",
    "    }",
    "",
    "    override fun form(schema: Schema): Schema =",
    "        schema.components(TextInput.make(\"name\"))",
    "",
    "    override fun infolist(schema: Schema): Schema =",
    "        schema.components(TextEntry.make(\"name\"))",
    "",
    "    override fun table(table: Table<*, *>): Table<*, *> =",
    "        table.columns(TextColumn.make(\"name\"))",
    "",
    "    override fun getRelations(): List<RelationManager> = listOf(",
    "        RelationManager(\"related\")",
    "            .related(app.models.$model::class.java)",
    "            .titleAttribute(\"id\")",
    "            .table(Table.memory().columns(TextColumn.make(\"id\"))),",
    "    )",
    "",
    "    override fun getPages(): Map<String, Page> = mapOf(",
    "        \"list\" to ListPage(\"list\"),",
    "        \"create\" to CreatePage(\"create\"),",
    "        \"edit\" to EditPage(\"edit\"),",
    "        \"view\" to ViewPage(\"view\"),",
    "    )",
    "}",
)

private fun javaSimple(className: String, model: String, slug: String, pkg: String): String = lines(
    "package $pkg;",
    "",
    "import org.konaframework.firefly.Resource;",
    "import org.konaframework.firefly.forms.TextInput;",
    "import org.konaframework.firefly.infolists.TextEntry;",
    "import org.konaframework.firefly.schemas.Schema;",
    "import org.konaframework.firefly.tables.Table;",
    "import org.konaframework.firefly.tables.TextColumn;",
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
    "        return schema.components(TextInput.make(\"name\"));",
    "    }",
    "",
    "    @Override",
    "    public Schema infolist(Schema schema) {",
    "        return schema.components(TextEntry.make(\"name\"));",
    "    }",
    "",
    "    @Override",
    "    public Table<?, ?> table(Table<?, ?> table) {",
    "        return Table.memory().columns(TextColumn.make(\"name\"));",
    "    }",
    "}",
)

private fun javaFull(className: String, model: String, slug: String, pkg: String): String = lines(
    "package $pkg;",
    "",
    "import org.konaframework.firefly.CreatePage;",
    "import org.konaframework.firefly.EditPage;",
    "import org.konaframework.firefly.ListPage;",
    "import org.konaframework.firefly.RelationManager;",
    "import org.konaframework.firefly.Resource;",
    "import org.konaframework.firefly.ViewPage;",
    "import org.konaframework.firefly.forms.TextInput;",
    "import org.konaframework.firefly.infolists.TextEntry;",
    "import org.konaframework.firefly.schemas.Schema;",
    "import org.konaframework.firefly.tables.Table;",
    "import org.konaframework.firefly.tables.TextColumn;",
    "import org.konaframework.firefly.widgets.Page;",
    "",
    "import java.util.LinkedHashMap;",
    "import java.util.Map;",
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
    "        return schema.components(TextInput.make(\"name\"));",
    "    }",
    "",
    "    @Override",
    "    public Schema infolist(Schema schema) {",
    "        return schema.components(TextEntry.make(\"name\"));",
    "    }",
    "",
    "    @Override",
    "    public Table<?, ?> table(Table<?, ?> table) {",
    "        return Table.memory().columns(TextColumn.make(\"name\"));",
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
    "        pages.put(\"list\", new ListPage(\"list\"));",
    "        pages.put(\"create\", new CreatePage(\"create\"));",
    "        pages.put(\"edit\", new EditPage(\"edit\"));",
    "        pages.put(\"view\", new ViewPage(\"view\"));",
    "        return pages;",
    "    }",
    "}",
)
