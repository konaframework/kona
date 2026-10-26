package org.konaframework.grind

import java.nio.file.Path

/** `grind make:firefly-panel` writes the panel class and its pages, widgets, and resources folders. */
class MakeFireflyPanelCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isNotEmpty()) {
            Names.requireClassName("panel", positional.first())
            if (positional.first() != "Panel") throw GrindException("The panel class is Panel")
        }
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        writePanel(base, lang, options["id"] ?: "admin", panelPath(options))
        return 0
    }
}

/** `grind firefly:install` writes the same panel tree. */
class FireflyInstallCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isNotEmpty()) throw GrindException("Usage: grind firefly:install [--lang=kotlin|java]")
        val base = Names.base(options)
        writePanel(base, Names.lang(base, options), options["id"] ?: "admin", panelPath(options))
        return 0
    }
}

/** `grind make:firefly-page <Name>` writes a custom page into the panel pages folder. */
class MakeFireflyPageCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) throw GrindException("Usage: grind make:firefly-page <Name> [--panel=admin] [--lang=kotlin|java]")
        val name = positional.first()
        Names.requireClassName("page", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val panel = panelIdFromOptions(options)
        Names.write(
            base,
            Names.sourceFile(lang, "app/panels/$panel/pages", name),
            "Firefly page",
            pageSource(name, lang, panel),
        )
        return 0
    }
}

/** `grind make:firefly-theme <Name>` writes a theme class. */
class MakeFireflyThemeCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) throw GrindException("Usage: grind make:firefly-theme <Name> [--lang=kotlin|java]")
        val name = positional.first()
        Names.requireClassName("theme", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        Names.write(base, Names.sourceFile(lang, "app/firefly/themes", name), "Firefly theme", themeSource(name, lang))
        return 0
    }
}

/** `grind firefly:publish` copies the sidebar partial into the app so it can be edited. */
class FireflyPublishCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isNotEmpty()) throw GrindException("Usage: grind firefly:publish")
        val base = Names.base(options)
        Names.write(
            base,
            "src/main/resources/views/firefly/sidebar.crema.html",
            "Firefly sidebar",
            """<aside class="ff-sidebar"><!-- ff-hook:sidebar.start --><!-- ff-hook:sidebar.end --></aside>
""",
        )
        return 0
    }
}

internal fun panelPath(options: Map<String, String>): String? =
    if (options.containsKey("path")) options["path"] else null

internal fun panelIdFromOptions(options: Map<String, String>): String {
    val raw = options["panel"]?.takeIf { it.isNotEmpty() } ?: "admin"
    return panelPackageSegment(raw)
}

internal fun panelPackageSegment(id: String): String {
    if (!PANEL_ID.matches(id)) throw GrindException("Panel id [$id] is not a package name")
    return id
}

private val PANEL_ID = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

internal fun writePanel(base: Path, lang: String, id: String, path: String?) {
    if (id.isBlank()) throw GrindException("Panel id is blank")
    val segment = panelPackageSegment(id)
    val root = "app/panels/$segment"
    Names.write(base, Names.sourceFile(lang, root, "Panel"), "Firefly panel", panelSource(lang, segment, path))
    val language = if (lang == "java") "java" else "kotlin"
    for (folder in listOf("pages", "widgets", "resources")) {
        Names.write(base, "src/main/$language/$root/$folder/.gitkeep", "Firefly $folder", "")
    }
}

private fun panelSource(lang: String, id: String, path: String?): String {
    val pathLines = if (path == null) {
        emptyList()
    } else if (lang == "java") {
        listOf("        path(\"$path\");")
    } else {
        listOf("        path(\"$path\")")
    }
    return if (lang == "java") {
        lines(
            "package app.panels.$id;",
            "",
            "public class Panel extends org.konaframework.firefly.Panel {",
            "    public Panel() {",
            "        id(\"$id\");",
            *pathLines.toTypedArray(),
            "    }",
            "}",
        )
    } else {
        lines(
            "package app.panels.$id",
            "",
            "class Panel : org.konaframework.firefly.Panel() {",
            "    init {",
            "        id(\"$id\")",
            *pathLines.toTypedArray(),
            "    }",
            "}",
        )
    }
}

private fun pageSource(name: String, lang: String, panel: String): String = if (lang == "java") {
    lines(
        "package app.panels.$panel.pages;",
        "",
        "import org.konaframework.firefly.CustomPage;",
        "",
        "public class $name extends CustomPage {",
        "    public $name() {",
        "        super(\"${name.lowercase()}\");",
        "    }",
        "}",
    )
} else {
    lines(
        "package app.panels.$panel.pages",
        "",
        "import org.konaframework.firefly.CustomPage",
        "",
        "class $name : CustomPage(\"${name.lowercase()}\")",
    )
}

private fun themeSource(name: String, lang: String): String = if (lang == "java") {
    lines(
        "package app.firefly.themes;",
        "",
        "import org.konaframework.firefly.support.FireflyTheme;",
        "import org.konaframework.firefly.support.KonaTheme;",
        "import org.konaframework.firefly.support.ThemeTokens;",
        "",
        "public class $name implements FireflyTheme {",
        "    public String id() { return \"${name.lowercase()}\"; }",
        "    public ThemeTokens tokens() { return new KonaTheme().tokens(); }",
        "}",
    )
} else {
    lines(
        "package app.firefly.themes",
        "",
        "import org.konaframework.firefly.support.FireflyTheme",
        "import org.konaframework.firefly.support.KonaTheme",
        "import org.konaframework.firefly.support.ThemeTokens",
        "",
        "class $name : FireflyTheme {",
        "    override fun id(): String = \"${name.lowercase()}\"",
        "    override fun tokens(): ThemeTokens = KonaTheme().tokens()",
        "}",
    )
}
