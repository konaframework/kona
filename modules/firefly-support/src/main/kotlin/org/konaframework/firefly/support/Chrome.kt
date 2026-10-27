package org.konaframework.firefly.support

import org.konaframework.crema.CremaEngine

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

/**
 * Default authenticated panel document.
 *
 * Phase A (Wire + Crema rule): markup lives in
 * `views/firefly/layouts/panel.crema.html`. This class is the thin
 * [PanelLayout] adapter — it validates partials, packs data, and asks Crema to
 * render. A Wire page-host component is deferred; auth stays on [AuthLayout].
 */
class DefaultLayout(
    private val crema: CremaEngine,
) : PanelLayout {
    /** Default engine: package Crema layouts via [FireflyCrema]. */
    constructor() : this(FireflyCrema.engine())

    override fun render(context: ChromeContext): String {
        if (context.mode == ChromeMode.AUTH) {
            return AuthLayout().render(context)
        }
        val sidebar = part(context, "sidebar")
        val topbar = part(context, "topbar")
        val body = part(context, "body")
        val footer = part(context, "footer")
        val tokens = context.theme.tokens()
        val csrf = context.csrfToken?.takeUnless { it.isBlank() }
        val title = context.documentTitle.ifBlank { "Firefly" }
        return crema.render(
            FireflyCrema.PANEL,
            mapOf(
                "documentTitle" to title,
                "brandName" to context.brandName,
                "csrfToken" to csrf,
                "css" to context.css,
                "sidebar" to sidebar,
                "topbar" to topbar,
                "body" to body,
                "footer" to footer,
                "themeInitScript" to THEME_INIT_SCRIPT,
                "chromeScript" to CHROME_SCRIPT,
                "bodyBackground" to tokens.bodyBackground,
                "bodyColor" to tokens.heading,
            ),
        )
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
         * menus (notifications + user), mobile sidebar drawer, Action/TableButton
         * dialogs (`data-ff-dialog-*`), and table soft-nav (`data-ff-table-nav`) that
         * swaps `.ff-table-wrap` via fetch without a full document reload.
         * Alpine is optional — dialogs and table nav open without it.
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
                "function closeDialog(panel){if(!panel)return;panel.classList.remove('ff-open');" +
                "panel.setAttribute('hidden','');panel.style.display='none';panel.setAttribute('aria-hidden','true');}" +
                "function closeDialogs(){root.querySelectorAll('[data-ff-dialog-panel].ff-open').forEach(closeDialog);}" +
                "function openDialog(panel){if(!panel)return;closeDialogs();panel.classList.add('ff-open');" +
                "panel.removeAttribute('hidden');panel.style.display='block';panel.removeAttribute('aria-hidden');}" +
                "apply(pref());" +
                "media.addEventListener('change',function(){if(pref()==='system')apply('system');});" +
                "document.addEventListener('click',function(ev){" +
                "var t=ev.target;if(!t||!t.closest)return;" +
                "if(t.closest('[data-ff-theme-toggle]')){var o=['light','dark','system'];" +
                "var n=o[(o.indexOf(pref())+1)%o.length];try{localStorage.setItem('ff-theme',n);}catch(e){}apply(n);return;}" +
                "var openBtn=t.closest('[data-ff-dialog-open]');" +
                "if(openBtn){var mount=openBtn.closest('[data-ff-dialog-mount]');" +
                "if(mount){openDialog(mount.querySelector('[data-ff-dialog-panel]'));}return;}" +
                "if(t.closest('[data-ff-dialog-close]')||t.closest('.ff-dialog-overlay')){" +
                "var dm=t.closest('[data-ff-dialog-mount]');if(dm){closeDialog(dm.querySelector('[data-ff-dialog-panel]'));}return;}" +
                "if(t.closest('[data-ff-dialog-panel]'))return;" +
                "var tr=t.closest('[data-ff-menu-trigger]');" +
                "if(tr){var menu=tr.closest('[data-ff-menu]');var was=menu.classList.contains('ff-open');" +
                "closeMenus();if(!was)menu.classList.add('ff-open');return;}" +
                "if(t.closest('[data-ff-menu-panel]'))return;" +
                "if(t.closest('[data-ff-sidebar-toggle]')){root.classList.add('ff-sidebar-open');return;}" +
                "if(t.closest('[data-ff-sidebar-close]')||t.closest('[data-ff-backdrop]')){root.classList.remove('ff-sidebar-open');closeMenus();closeDialogs();return;}" +
                "var nav=t.closest('a[data-ff-table-nav]');" +
                "if(nav){ev.preventDefault();ffTableNav(nav.getAttribute('href'),nav.closest('.ff-table-wrap'));return;}" +
                "closeMenus();closeDialogs();});" +
                "function ffTableIndex(wrap){return Array.prototype.indexOf.call(document.querySelectorAll('.ff-table-wrap'),wrap);}" +
                "function ffTableNav(href,wrap){if(!href)return;var idx=wrap?ffTableIndex(wrap):-1;" +
                "fetch(href,{credentials:'same-origin',headers:{'X-Requested-With':'FF-Table'}}).then(function(r){return r.text();}).then(function(html){" +
                "var doc=new DOMParser().parseFromString(html,'text/html');" +
                "var next=idx>=0?doc.querySelectorAll('.ff-table-wrap')[idx]:doc.querySelector('.ff-table-wrap');" +
                "var cur=idx>=0?document.querySelectorAll('.ff-table-wrap')[idx]:(wrap||document.querySelector('.ff-table-wrap'));" +
                "if(next&&cur){cur.replaceWith(next);}try{history.pushState({},'',href);}catch(e){}" +
                "}).catch(function(){location.href=href;});}" +
                "document.addEventListener('submit',function(ev){var f=ev.target;if(!f||!f.matches||!f.matches('form[data-ff-table-nav]'))return;" +
                "ev.preventDefault();var wrap=f.closest('.ff-table-wrap');var action=f.getAttribute('action')||location.pathname;" +
                "var qs=new URLSearchParams(new FormData(f)).toString();ffTableNav(action+(qs?('?'+qs):''),wrap);});" +
                "document.addEventListener('change',function(ev){var el=ev.target;if(!el||!el.matches||!el.matches('[data-ff-table-nav-select]'))return;" +
                "var wrap=el.closest('.ff-table-wrap');var form=el.closest('form[data-ff-table-nav]');" +
                "if(form){if(form.requestSubmit)form.requestSubmit();else form.submit();return;}" +
                "var url=new URL(location.href);url.searchParams.set(el.name||'perPage',el.value);url.searchParams.set('page','1');" +
                "ffTableNav(url.pathname+url.search,wrap);});" +
                "document.addEventListener('keydown',function(ev){if(ev.key==='Escape'){" +
                "root.classList.remove('ff-sidebar-open');closeMenus();closeDialogs();}});" +
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
