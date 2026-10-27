package org.konaframework.firefly.support

enum class ChromeMode {
    PANEL,
    AUTH,
}

class ChromeContext @JvmOverloads constructor(
    val theme: FireflyTheme,
    val hooks: RenderHooks,
    val partials: Map<String, String>,
    val css: String,
    val csrfToken: String? = null,
    val documentTitle: String = "Firefly",
    val brandName: String = "Firefly",
    val mode: ChromeMode = ChromeMode.PANEL,
)

interface PanelLayout {
    fun render(context: ChromeContext): String
}

class DefaultLayout : PanelLayout {
    override fun render(context: ChromeContext): String {
        if (context.mode == ChromeMode.AUTH) {
            return AuthLayout().render(context)
        }
        val sidebar = part(context, "sidebar")
        val topbar = part(context, "topbar")
        val body = part(context, "body")
        val footer = part(context, "footer")
        val panel = """
            <div class="ff-panel">
              <div class="ff-backdrop" data-ff-backdrop aria-hidden="true"></div>
              $sidebar
              <div class="ff-column">
                $topbar
                <div class="ff-scroll">
                  $body
                </div>
                $footer
              </div>
            </div>
        """.trimIndent()
        val brand = context.theme.tokens().heading
        val csrf = context.csrfToken
        val meta =
            if (csrf.isNullOrBlank()) {
                ""
            } else {
                """<meta name="csrf-token" content="${escAttr(csrf)}" />"""
            }
        val title = context.documentTitle.ifBlank { "Firefly" }
        return """
            <!DOCTYPE html>
            <html lang="en" class="ff-html">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                $meta
                <title>${escAttr(title)}</title>
                <link rel="preconnect" href="https://fonts.googleapis.com" />
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
                <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,400..700;1,9..40,400..700&family=Inter:ital,opsz,wght@0,400..700;1,400..700&display=swap" />
                $THEME_INIT_SCRIPT
                <style>${context.css}</style>
              </head>
              <body class="ff-body-root" style="margin:0;background:${escAttr(context.theme.tokens().bodyBackground)};color:${escAttr(brand)}">
                $panel
                $CHROME_SCRIPT
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

    companion object {
        /**
         * Applies the stored theme preference before first paint so dark panels
         * never flash light. Toggles `ff-dark` on the document root; dark tokens
         * respond to both `.ff-dark .ff-panel` and `.ff-panel.ff-dark`.
         */
        internal const val THEME_INIT_SCRIPT: String =
            "<script>(function(){try{var p=localStorage.getItem('ff-theme')||'system';" +
                "var d=p==='dark'||(p!=='light'&&window.matchMedia('(prefers-color-scheme: dark)').matches);" +
                "document.documentElement.classList.toggle('ff-dark',d);}catch(e){}})();</script>"

        /**
         * Panel chrome behavior with no framework: 3-state theme cycle, dropdown
         * menus (notifications + user), and the mobile sidebar drawer. All driven
         * by data attributes so themes and layouts can keep rendering plain HTML.
         */
        internal const val CHROME_SCRIPT: String =
            "<script>(function(){" +
                "var root=document.querySelector('.ff-panel');if(!root)return;" +
                "var media=window.matchMedia('(prefers-color-scheme: dark)');" +
                "function pref(){try{var v=localStorage.getItem('ff-theme');" +
                "return v==='light'||v==='dark'||v==='system'?v:'system';}catch(e){return 'system';}}" +
                "function apply(p){var d=p==='dark'||(p!=='light'&&media.matches);" +
                "document.documentElement.classList.toggle('ff-dark',d);" +
                "root.querySelectorAll('[data-ff-theme-toggle]').forEach(function(b){b.setAttribute('data-ff-theme',p);});}" +
                "function closeMenus(){root.querySelectorAll('[data-ff-menu].ff-open').forEach(function(m){m.classList.remove('ff-open');});}" +
                "apply(pref());" +
                "media.addEventListener('change',function(){if(pref()==='system')apply('system');});" +
                "document.addEventListener('click',function(ev){" +
                "var t=ev.target;if(!t||!t.closest)return;" +
                "if(t.closest('[data-ff-theme-toggle]')){var o=['light','dark','system'];" +
                "var n=o[(o.indexOf(pref())+1)%o.length];try{localStorage.setItem('ff-theme',n);}catch(e){}apply(n);return;}" +
                "var tr=t.closest('[data-ff-menu-trigger]');" +
                "if(tr){var menu=tr.closest('[data-ff-menu]');var was=menu.classList.contains('ff-open');" +
                "closeMenus();if(!was)menu.classList.add('ff-open');return;}" +
                "if(t.closest('[data-ff-menu-panel]'))return;" +
                "if(t.closest('[data-ff-sidebar-toggle]')){root.classList.add('ff-sidebar-open');return;}" +
                "if(t.closest('[data-ff-sidebar-close]')||t.closest('[data-ff-backdrop]')){root.classList.remove('ff-sidebar-open');closeMenus();return;}" +
                "closeMenus();});" +
                "document.addEventListener('keydown',function(ev){if(ev.key==='Escape'){" +
                "root.classList.remove('ff-sidebar-open');closeMenus();}});" +
                "})();</script>"
    }
}

class Panel {
    private var theme: FireflyTheme = KonaTheme()
    private var layout: PanelLayout = DefaultLayout()
    private val hooks = RenderHooks()
    private val overrides = linkedMapOf<String, String>()
    private var csrfToken: String? = null
    private var documentTitle: String = "Firefly"
    private var brandName: String = "Firefly"
    private var mode: ChromeMode = ChromeMode.PANEL

    fun theme(theme: FireflyTheme): Panel = apply { this.theme = theme }

    fun layout(layout: PanelLayout): Panel = apply { this.layout = layout }

    fun hook(hook: Hook, html: String): Panel = apply { hooks.add(hook, html) }

    fun overridePartial(slot: String, html: String): Panel = apply { overrides[slot] = html }

    fun csrfToken(token: String?): Panel = apply { csrfToken = token }

    fun documentTitle(title: String): Panel = apply { documentTitle = title }

    fun brandName(name: String): Panel = apply { brandName = name }

    fun mode(mode: ChromeMode): Panel = apply { this.mode = mode }

    fun render(): String {
        val partials = linkedMapOf<String, String>()
        val slots = if (mode == ChromeMode.AUTH) listOf("body") else SLOTS
        for (slot in slots) {
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
        return layout.render(
            ChromeContext(
                theme,
                hooks,
                partials,
                Stylesheet.render(theme),
                csrfToken,
                documentTitle,
                brandName,
                mode,
            ),
        )
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
