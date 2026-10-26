package org.konaframework.grind

/** `grind make:firefly-resource <Name> [--simple]` writes a resource the panel discovers. */
class MakeFireflyResourceCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.size != 1) throw GrindException("Usage: grind make:firefly-resource <Name> [--simple]")
        val name = positional.first()
        Names.requireClassName("resource", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val simple = options["simple"] == "true"
        val panel = panelIdFromOptions(options)
        Names.write(
            base,
            Names.sourceFile(lang, "app/panels/$panel/resources", name),
            "Firefly resource",
            resourceSource(name, lang, simple, panel),
        )
        return 0
    }
}

private fun resourceSource(name: String, lang: String, simple: Boolean, panel: String): String {
    val slug = name.lowercase()
    val pkg = "app.panels.$panel.resources"
    if (lang == "java" && simple) return javaSimple(name, slug, pkg)
    if (lang == "java") return javaFull(name, slug, pkg)
    if (simple) return kotlinSimple(name, slug, pkg)
    return kotlinFull(name, slug, pkg)
}

private fun kotlinSimple(name: String, slug: String, pkg: String): String = lines(
    "package $pkg",
    "",
    "import org.konaframework.firefly.Resource",
    "import org.konaframework.firefly.forms.TextInput",
    "import org.konaframework.firefly.infolists.TextEntry",
    "import org.konaframework.firefly.schemas.Schema",
    "import org.konaframework.firefly.tables.Table",
    "import org.konaframework.firefly.tables.TextColumn",
    "",
    "class $name : Resource(\"$slug\") {",
    "    init {",
    "        label(\"$name\")",
    "        simple()",
    "        model(app.models.$name::class.java)",
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

private fun kotlinFull(name: String, slug: String, pkg: String): String = lines(
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
    "class $name : Resource(\"$slug\") {",
    "    init {",
    "        label(\"$name\")",
    "        model(app.models.$name::class.java)",
    "        relation(",
    "            RelationManager(\"related\")",
    "                .related(app.models.$name::class.java)",
    "                .titleAttribute(\"id\")",
    "                .table(Table.memory().columns(TextColumn.make(\"id\")))",
    "        )",
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
    "    override fun getPages(): Map<String, Page> = mapOf(",
    "        \"list\" to ListPage(\"list\"),",
    "        \"create\" to CreatePage(\"create\"),",
    "        \"edit\" to EditPage(\"edit\"),",
    "        \"view\" to ViewPage(\"view\"),",
    "    )",
    "}",
)

private fun javaSimple(name: String, slug: String, pkg: String): String = lines(
    "package $pkg;",
    "",
    "import org.konaframework.firefly.Resource;",
    "import org.konaframework.firefly.forms.TextInput;",
    "import org.konaframework.firefly.infolists.TextEntry;",
    "import org.konaframework.firefly.schemas.Schema;",
    "import org.konaframework.firefly.tables.Table;",
    "import org.konaframework.firefly.tables.TextColumn;",
    "",
    "public class $name extends Resource {",
    "    public $name() {",
    "        super(\"$slug\");",
    "        label(\"$name\");",
    "        simple();",
    "        model(app.models.$name.class);",
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

private fun javaFull(name: String, slug: String, pkg: String): String = lines(
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
    "public class $name extends Resource {",
    "    public $name() {",
    "        super(\"$slug\");",
    "        label(\"$name\");",
    "        model(app.models.$name.class);",
    "        relation(",
    "            new RelationManager(\"related\")",
    "                .related(app.models.$name.class)",
    "                .titleAttribute(\"id\")",
    "                .table(Table.memory().columns(TextColumn.make(\"id\")))",
    "        );",
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
