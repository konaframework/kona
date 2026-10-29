package org.konaframework.grind

/**
 * `grind make:firefly-resource <Model> [--simple]` writes a `*Resource` the panel discovers
 * plus thin page subclasses under `{model}/pages/` (not under `{Resource}/`, which would
 * collide with the resource class FQN on the JVM — #321).
 *
 * Simple resources emit [ManagePage] and wire it from `getPages()`. Full resources emit
 * list / create / edit / view pages. The panel sidebar gets **one** entry (list or manage);
 * create / edit / view / delete remain page-header and row actions from [Resource.pages].
 */
class MakeFireflyResourceCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.size != 1) {
            throw GrindException("Usage: grind make:firefly-resource <Model> [--simple]  (writes ModelResource + pages)")
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
        val pkg = "app.panels.$panel.resources"
        // Lowercase model segment — never nest under the Resource class name (#321).
        val modelPkg = model.lowercase()
        val pagesPkg = "$pkg.$modelPkg.pages"
        val pagesDir = "app/panels/$panel/resources/$modelPkg/pages"
        Names.write(
            base,
            Names.sourceFile(lang, "app/panels/$panel/resources", className),
            "Firefly resource",
            resourceSource(className, model, lang, simple, pkg, pagesPkg),
        )
        writeResourcePages(base, lang, model, simple, pagesDir, pagesPkg)
        return 0
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

private fun resourceSource(
    className: String,
    model: String,
    lang: String,
    simple: Boolean,
    pkg: String,
    pagesPkg: String,
): String {
    val slug = model.lowercase()
    if (lang == "java" && simple) return javaSimple(className, model, slug, pkg, pagesPkg)
    if (lang == "java") return javaFull(className, model, slug, pkg, pagesPkg)
    if (simple) return kotlinSimple(className, model, slug, pkg, pagesPkg)
    return kotlinFull(className, model, slug, pkg, pagesPkg)
}

private fun kotlinSimple(
    className: String,
    model: String,
    slug: String,
    pkg: String,
    pagesPkg: String,
): String = lines(
    "package $pkg",
    "",
    "import $pagesPkg.Manage$model",
    "import org.konaframework.firefly.Resource",
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
    "",
    "    override fun getPages(): Map<String, Page> = mapOf(",
    "        \"manage\" to Manage$model(),",
    "    )",
    "}",
)

private fun kotlinFull(
    className: String,
    model: String,
    slug: String,
    pkg: String,
    pagesPkg: String,
): String = lines(
    "package $pkg",
    "",
    "import $pagesPkg.Create$model",
    "import $pagesPkg.Edit$model",
    "import $pagesPkg.List$model",
    "import $pagesPkg.View$model",
    "import org.konaframework.firefly.RelationManager",
    "import org.konaframework.firefly.Resource",
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
    "        \"list\" to List$model(),",
    "        \"create\" to Create$model(),",
    "        \"edit\" to Edit$model(),",
    "        \"view\" to View$model(),",
    "    )",
    "}",
)

private fun javaSimple(
    className: String,
    model: String,
    slug: String,
    pkg: String,
    pagesPkg: String,
): String = lines(
    "package $pkg;",
    "",
    "import $pagesPkg.Manage$model;",
    "import org.konaframework.firefly.Resource;",
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
    "",
    "    @Override",
    "    public Map<String, Page> getPages() {",
    "        Map<String, Page> pages = new LinkedHashMap<>();",
    "        pages.put(\"manage\", new Manage$model());",
    "        return pages;",
    "    }",
    "}",
)

private fun javaFull(
    className: String,
    model: String,
    slug: String,
    pkg: String,
    pagesPkg: String,
): String = lines(
    "package $pkg;",
    "",
    "import $pagesPkg.Create$model;",
    "import $pagesPkg.Edit$model;",
    "import $pagesPkg.List$model;",
    "import $pagesPkg.View$model;",
    "import org.konaframework.firefly.RelationManager;",
    "import org.konaframework.firefly.Resource;",
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
    "        pages.put(\"list\", new List$model());",
    "        pages.put(\"create\", new Create$model());",
    "        pages.put(\"edit\", new Edit$model());",
    "        pages.put(\"view\", new View$model());",
    "        return pages;",
    "    }",
    "}",
)
