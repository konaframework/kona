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

/** `grind make:firefly-theme <Name>` writes a FireflyTheme subclass the app can edit. */
class MakeFireflyThemeCommand {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        if (positional.isEmpty()) {
            throw GrindException("Usage: grind make:firefly-theme <Name> [--panel=admin] [--lang=kotlin|java]")
        }
        val name = positional.first()
        Names.requireClassName("theme", name)
        val base = Names.base(options)
        val lang = Names.lang(base, options)
        val panel = panelIdFromOptions(options)
        val relative = Names.sourceFile(lang, "app/panels/$panel/themes", name)
        Names.write(base, relative, "Firefly theme", themeSource(name, lang, panel))
        val hint = if (lang == "java") {
            "Register it with panel.theme(new $name())"
        } else {
            "Register it with panel.theme($name())"
        }
        GrindIO().info(hint)
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
    // Nested directories only — never a single `panels.$id` folder.
    val root = "app/panels/$segment"
    Names.write(base, Names.sourceFile(lang, root, "Panel"), "Firefly panel", panelSource(lang, segment, path))
    val language = if (lang == "java") "java" else "kotlin"
    for (folder in listOf("pages", "widgets", "resources")) {
        Names.write(base, "src/main/$language/$root/$folder/.gitkeep", "Firefly $folder", "")
    }
}

private fun panelSource(lang: String, id: String, path: String?): String {
    val calls = ArrayList<String>()
    calls.add("id(\"$id\")")
    if (path != null) calls.add("path(\"$path\")")
    calls.add("reuseAppGuard()")
    calls.add("authenticate(true)")
    return if (lang == "java") {
        val chain = calls.mapIndexed { index, call ->
            val suffix = if (index == calls.lastIndex) ";" else ""
            "            .$call$suffix"
        }
        lines(
            "package app.panels.$id;",
            "",
            "public class Panel extends org.konaframework.firefly.Panel {",
            "    @Override",
            "    public org.konaframework.firefly.Panel panel(org.konaframework.firefly.Panel panel) {",
            "        return panel",
            *chain.toTypedArray(),
            "    }",
            "}",
        )
    } else {
        val chain = calls.map { "            .$it" }
        lines(
            "package app.panels.$id",
            "",
            "import org.konaframework.firefly.Panel as FireflyPanel",
            "",
            "class Panel : FireflyPanel() {",
            "    override fun panel(panel: FireflyPanel): FireflyPanel =",
            "        panel",
            *chain.toTypedArray(),
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

/**
 * One hook per chrome region. The comment names the element the render appends to.
 * Empty returns keep the stock panel unchanged until the app overrides a hook.
 */
private val THEME_HOOKS = listOf(
    "panel" to ".ff-panel",
    "sidebar" to "aside.ff-sidebar",
    "topbar" to "header.ff-topbar",
    "brand" to ".ff-sidebar-brand, .ff-topbar-brand, and .ff-auth-brand",
    "navigation" to "nav.ff-nav",
    "navigationItem" to ".ff-nav-item",
    "navigationGroup" to ".ff-nav-group",
    "content" to "main.ff-body",
    "pageHeader" to ".ff-page-header",
    "footer" to "footer.ff-footer",
    "sidebarFoot" to ".ff-sidebar-foot",
    "userMenu" to ".ff-user-menu",
    "column" to ".ff-column",
    "scroll" to ".ff-scroll",
    "auth" to "body.ff-auth",
    "authCard" to ".ff-auth-panel",
)

private fun themeSource(name: String, lang: String, panel: String): String = if (lang == "java") {
    val hooks = THEME_HOOKS.map { (method, element) ->
        "    /** Extra classes for $element. */\n    @Override public String $method() { return \"\"; }"
    }
    lines(
        "package app.panels.$panel.themes;",
        "",
        "import org.konaframework.firefly.support.FireflyTheme;",
        "",
        "/**",
        " * Chrome theme for the $panel panel.",
        " * Override a hook to append CSS classes. Empty strings leave the stock UI unchanged.",
        " * Register it with {@code panel.theme(new $name())}.",
        " * Example: {@code public String sidebar() { return \"bg-slate-950 text-white\"; }}",
        " */",
        "public class $name extends FireflyTheme {",
        "    @Override public String id() { return \"${name.lowercase()}\"; }",
        "",
        "    /** Stylesheet URL linked from the document head, or null. */",
        "    @Override public String stylesheet() { return null; }",
        "",
        *hooks.toTypedArray(),
        "}",
    )
} else {
    val hooks = THEME_HOOKS.map { (method, element) ->
        "    /** Extra classes for `$element`. */\n    override fun $method(): String = \"\""
    }
    lines(
        "package app.panels.$panel.themes",
        "",
        "import org.konaframework.firefly.support.FireflyTheme",
        "",
        "/**",
        " * Chrome theme for the $panel panel.",
        " * Override a hook to append CSS classes. Empty strings leave the stock UI unchanged.",
        " * Register it with `panel.theme($name())`.",
        " * Example: `override fun sidebar(): String = \"bg-slate-950 text-white\"`.",
        " */",
        "class $name : FireflyTheme() {",
        "    override fun id(): String = \"${name.lowercase()}\"",
        "",
        "    /** Stylesheet URL linked from the document head, or null. */",
        "    override fun stylesheet(): String? = null",
        "",
        *hooks.toTypedArray(),
        "}",
    )
}
