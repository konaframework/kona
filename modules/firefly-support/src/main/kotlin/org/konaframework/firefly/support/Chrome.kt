package org.konaframework.firefly.support

class ChromeContext(
    val theme: FireflyTheme,
    val hooks: RenderHooks,
    val partials: Map<String, String>,
    val css: String,
)

interface PanelLayout {
    fun render(context: ChromeContext): String
}

class DefaultLayout : PanelLayout {
    override fun render(context: ChromeContext): String {
        val sidebar = part(context, "sidebar")
        val topbar = part(context, "topbar")
        val body = part(context, "body")
        val footer = part(context, "footer")
        val panel = """
            <div class="ff-panel">
              $sidebar
              $topbar
              $body
              $footer
            </div>
        """.trimIndent()
        return """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>Firefly</title>
                <style>${context.css}</style>
              </head>
              <body style="margin:0;background:${escAttr(context.theme.tokens().bodyBackground)}">
                $panel
              </body>
            </html>
        """.trimIndent()
    }

    private fun escAttr(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '<' -> append("&lt;")
                else -> append(ch)
            }
        }
    }

    private fun part(context: ChromeContext, name: String): String {
        val html = context.partials[name]
        if (html == null) throw FireflyException("Missing partial [$name]")
        return html
    }
}

class Panel {
    private var theme: FireflyTheme = KonaTheme()
    private var layout: PanelLayout = DefaultLayout()
    private val hooks = RenderHooks()
    private val overrides = linkedMapOf<String, String>()

    fun theme(theme: FireflyTheme): Panel = apply { this.theme = theme }

    fun layout(layout: PanelLayout): Panel = apply { this.layout = layout }

    fun hook(hook: Hook, html: String): Panel = apply { hooks.add(hook, html) }

    fun overridePartial(slot: String, html: String): Panel = apply { overrides[slot] = html }

    fun render(): String {
        val partials = linkedMapOf<String, String>()
        for (slot in SLOTS) {
            val fromApp = overrides[slot]
            val fromTheme = theme.partial(slot)
            val chosen = when {
                fromApp != null -> fromApp
                fromTheme != null -> fromTheme
                else -> builtin(slot)
            }
            val missing = requiredMarkers(slot).filter { marker -> !chosen.contains(marker) }
            if (missing.isNotEmpty()) {
                throw FireflyException("Partial [$slot] is missing markers $missing")
            }
            partials[slot] = fill(slot, chosen)
        }
        return layout.render(ChromeContext(theme, hooks, partials, Stylesheet.render(theme)))
    }

    private fun fill(slot: String, template: String): String {
        var html = template
        for (hook in hooksFor(slot)) {
            html = html.replace("<!-- ff-hook:${hook.marker} -->", hooks.render(hook))
        }
        return html
    }

    companion object {
        private val SLOTS = listOf("sidebar", "topbar", "body", "footer")
    }
}

internal fun hooksFor(slot: String): List<Hook> = when (slot) {
    "sidebar" -> listOf(Hook.SIDEBAR_START, Hook.SIDEBAR_END)
    "topbar" -> listOf(Hook.TOPBAR_START, Hook.TOPBAR_END)
    "body" -> listOf(Hook.BODY_START, Hook.BODY_END)
    "footer" -> listOf(Hook.FOOTER)
    else -> emptyList()
}

internal fun requiredMarkers(slot: String): List<String> = hooksFor(slot).map { "<!-- ff-hook:${it.marker} -->" }

private const val SIDEBAR = """<aside class="ff-sidebar"><!-- ff-hook:sidebar.start --><!-- ff-hook:sidebar.end --></aside>"""
private const val TOPBAR = """<header class="ff-topbar"><!-- ff-hook:topbar.start --><!-- ff-hook:topbar.end --></header>"""
private const val BODY = """<main class="ff-body"><!-- ff-hook:body.start --><!-- ff-hook:body.end --></main>"""
private const val FOOTER = """<footer class="ff-footer"><!-- ff-hook:footer --></footer>"""

internal fun builtin(slot: String): String = when (slot) {
    "sidebar" -> SIDEBAR
    "topbar" -> TOPBAR
    "body" -> BODY
    "footer" -> FOOTER
    else -> throw FireflyException("Unknown partial [$slot]")
}
