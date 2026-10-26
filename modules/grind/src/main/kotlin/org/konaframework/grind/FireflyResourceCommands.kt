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
    "import org.konaframework.firefly.ManagePage",
    "import org.konaframework.firefly.Resource",
    "import org.konaframework.firefly.forms.TextInput",
    "import org.konaframework.firefly.infolists.Infolist",
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
    "        form(Schema.make().components(TextInput.make(\"name\")))",
    "        infolist(Infolist.make().components(TextEntry.make(\"name\")))",
    "        table(Table.memory().columns(TextColumn.make(\"name\")))",
    "        page(ManagePage(\"manage\"))",
    "    }",
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
    "import org.konaframework.firefly.infolists.Infolist",
    "import org.konaframework.firefly.infolists.TextEntry",
    "import org.konaframework.firefly.schemas.Schema",
    "import org.konaframework.firefly.tables.Table",
    "import org.konaframework.firefly.tables.TextColumn",
    "",
    "class $name : Resource(\"$slug\") {",
    "    init {",
    "        label(\"$name\")",
    "        model(app.models.$name::class.java)",
    "        form(Schema.make().components(TextInput.make(\"name\")))",
    "        infolist(Infolist.make().components(TextEntry.make(\"name\")))",
    "        table(Table.memory().columns(TextColumn.make(\"name\")))",
    "        relation(",
    "            RelationManager(\"related\")",
    "                .related(app.models.$name::class.java)",
    "                .titleAttribute(\"id\")",
    "                .table(Table.memory().columns(TextColumn.make(\"id\")))",
    "        )",
    "        page(ListPage(\"list\"))",
    "        page(CreatePage(\"create\"))",
    "        page(EditPage(\"edit\"))",
    "        page(ViewPage(\"view\"))",
    "    }",
    "}",
)

private fun javaSimple(name: String, slug: String, pkg: String): String = lines(
    "package $pkg;",
    "",
    "import org.konaframework.firefly.ManagePage;",
    "import org.konaframework.firefly.Resource;",
    "import org.konaframework.firefly.forms.TextInput;",
    "import org.konaframework.firefly.infolists.Infolist;",
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
    "        form(Schema.make().components(TextInput.make(\"name\")));",
    "        infolist(Infolist.make().components(TextEntry.make(\"name\")));",
    "        table(Table.memory().columns(TextColumn.make(\"name\")));",
    "        page(new ManagePage(\"manage\"));",
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
    "import org.konaframework.firefly.infolists.Infolist;",
    "import org.konaframework.firefly.infolists.TextEntry;",
    "import org.konaframework.firefly.schemas.Schema;",
    "import org.konaframework.firefly.tables.Table;",
    "import org.konaframework.firefly.tables.TextColumn;",
    "",
    "public class $name extends Resource {",
    "    public $name() {",
    "        super(\"$slug\");",
    "        label(\"$name\");",
    "        model(app.models.$name.class);",
    "        form(Schema.make().components(TextInput.make(\"name\")));",
    "        infolist(Infolist.make().components(TextEntry.make(\"name\")));",
    "        table(Table.memory().columns(TextColumn.make(\"name\")));",
    "        relation(",
    "            new RelationManager(\"related\")",
    "                .related(app.models.$name.class)",
    "                .titleAttribute(\"id\")",
    "                .table(Table.memory().columns(TextColumn.make(\"id\")))",
    "        );",
    "        page(new ListPage(\"list\"));",
    "        page(new CreatePage(\"create\"));",
    "        page(new EditPage(\"edit\"));",
    "        page(new ViewPage(\"view\"));",
    "    }",
    "}",
)
