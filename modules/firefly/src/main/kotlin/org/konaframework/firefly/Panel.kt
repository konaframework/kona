package org.konaframework.firefly

import org.konaframework.auth.Guard
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.support.DefaultLayout
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.FireflyTheme
import org.konaframework.firefly.support.Hook
import org.konaframework.firefly.support.KonaTheme
import org.konaframework.firefly.support.PanelLayout
import org.konaframework.firefly.widgets.Widget
import org.konaframework.http.Handler
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.nio.file.Path
import java.util.function.Function
import org.konaframework.firefly.support.Panel as Chrome

/**
 * A mounted Firefly panel. Adding this module does not register routes.
 * Call [mount] to add the panel's own paths. `/_firefly/` and `/wire/update` are never registered.
 *
 * The panel guard is `firefly-{id}` unless [reuseAppGuard] names the application guard.
 */
open class Panel {
    private var idLiteral: String = "admin"
    private var idUsing: Function<Get, String>? = null
    private var pathLiteral: String = ""
    private var pathUsing: Function<Get, String>? = null
    private var pathExplicit: Boolean = false
    private var brandLiteral: String = "Firefly"
    private var brandUsing: Function<Get, String>? = null
    private var domainLiteral: String? = null
    private var domainUsing: Function<Get, String>? = null
    private var navigationLiteral: NavigationType = NavigationType.APP
    private var navigationUsing: Function<Get, NavigationType>? = null
    private var theme: FireflyTheme = KonaTheme()
    private var layout: PanelLayout = DefaultLayout()
    private val hooks = ArrayList<Pair<Hook, String>>()
    private val items = ArrayList<NavigationItem>()
    private val userItems = ArrayList<NavigationItem>()
    private val pages = ArrayList<PanelPage>()
    private val resources = ArrayList<Resource>()
    private val widgets = ArrayList<Widget>()
    private var panelGuard: PanelGuard = MemoryPanelGuard()
    private var guardNameLiteral: String? = null
    private var usesAppGuard: Boolean = false
    private var authenticatePages: Boolean = true
    private var spaMode: Boolean = false
    private var directory: Path? = null
    private val tenantList = ArrayList<Tenant>()
    private val plugins = ArrayList<FireflyPlugin>()
    private var mounted: Boolean = false
    private var resolvedPrefix: String = "admin"
    private var resolvedId: String = "admin"

    fun id(value: String): Panel = apply {
        if (value.isBlank()) throw FireflyException("Panel id is blank")
        idLiteral = value
        idUsing = null
        resolvedId = value
    }

    fun id(provider: Function<Get, String>): Panel = apply {
        idUsing = provider
    }

    fun path(value: String): Panel = apply {
        pathLiteral = value
        pathUsing = null
        pathExplicit = true
    }

    fun path(provider: Function<Get, String>): Panel = apply {
        pathUsing = provider
        pathExplicit = true
    }

    fun brand(value: String): Panel = apply {
        if (value.isBlank()) throw FireflyException("Panel brand is blank")
        brandLiteral = value
        brandUsing = null
    }

    fun brand(provider: Function<Get, String>): Panel = apply {
        brandUsing = provider
    }

    fun domain(value: String): Panel = apply {
        if (value.isBlank()) throw FireflyException("Panel domain is blank")
        domainLiteral = value
        domainUsing = null
    }

    fun domain(provider: Function<Get, String>): Panel = apply {
        domainUsing = provider
    }

    fun navigation(type: NavigationType): Panel = apply {
        navigationLiteral = type
        navigationUsing = null
    }

    fun navigation(provider: Function<Get, NavigationType>): Panel = apply {
        navigationUsing = provider
    }

    fun topNavigation(): Panel = navigation(NavigationType.TOP)

    fun theme(theme: FireflyTheme): Panel = apply { this.theme = theme }

    fun layout(layout: PanelLayout): Panel = apply { this.layout = layout }

    fun hook(hook: Hook, html: String): Panel = apply { hooks.add(hook to html) }

    fun spa(on: Boolean): Panel = apply { spaMode = on }

    fun authenticate(on: Boolean): Panel = apply { authenticatePages = on }

    fun guard(guard: PanelGuard): Panel = apply { panelGuard = guard }

    fun useAuthGuard(guard: Guard): Panel = apply {
        panelGuard = AuthPanelGuard(guard)
        usesAppGuard = true
    }

    fun reuseAppGuard(name: String = "web"): Panel = apply {
        if (name.isBlank()) throw FireflyException("Panel guard name is blank")
        guardNameLiteral = name
        usesAppGuard = true
    }

    fun guardName(): String {
        val named = guardNameLiteral
        if (named != null) return named
        return "firefly-$resolvedId"
    }

    fun usesAppGuard(): Boolean = usesAppGuard

    fun item(item: NavigationItem): Panel = apply { items.add(item) }

    fun cluster(name: String): NavigationItem {
        val item = NavigationItem(name).cluster(true)
        items.add(item)
        return item
    }

    fun userMenu(vararg items: NavigationItem): Panel = apply {
        userItems.clear()
        userItems.addAll(items)
    }

    fun page(page: PanelPage): Panel = apply { pages.add(page) }

    fun resource(resource: Resource): Panel = apply { resources.add(resource) }

    fun widget(widget: Widget): Panel = apply { widgets.add(widget) }

    fun discover(panelDirectory: Path): Panel = apply { directory = panelDirectory }

    /** Sets the directory of pages, widgets, and resources when the panel did not call [discover]. */
    internal fun adoptHome(path: Path) {
        if (directory == null) directory = path
    }

    fun tenant(tenant: Tenant): Panel = apply { tenantList.add(tenant) }

    fun plugin(plugin: FireflyPlugin): Panel = apply {
        val ready = FireflyPlugin.finish(plugin)
        if (plugins.any { it.id() == ready.id() }) {
            throw FireflyException("Plugin [${ready.id()}] is already registered")
        }
        ready.register(this)
        plugins.add(ready)
    }

    fun mount(router: Router): Panel = mount(router, emptyMap())

    fun mount(router: Router, state: Map<String, Any?>): Panel {
        if (mounted) throw FireflyException("Panel [$resolvedId] is already mounted")
        val get = SchemaBridge.open(state)
        resolvedId = resolveId(get)
        resolvedPrefix = normalizePrefix(resolvePath(get, resolvedId))
        resolveBrand(get)
        resolveDomain(get)
        for (plugin in plugins) plugin.boot(this)
        val discovered = loadDiscovered()
        adopt(discovered)
        val planned = ArrayList<Planned>()
        authRoutes(planned)
        pageRoutes(planned)
        for (route in planned) {
            reserved(route.uri, route.name)
            val existing = router.all().firstOrNull { it.methods.contains(route.method) && it.uri == route.uri }
            if (existing != null) {
                throw FireflyException(
                    "Route ${route.method} ${route.uri} is already handled by [${handlerLabel(existing)}] and [${route.name}]",
                )
            }
        }
        for (route in planned) {
            router.match(listOf(route.method), route.uri, route.handler).name(route.name)
        }
        mounted = true
        return this
    }

    fun render(request: Request): String = draw(request, emptyMap(), "")

    fun render(request: Request, state: Map<String, Any?>): String = draw(request, state, "")

    private fun draw(request: Request, state: Map<String, Any?>, body: String): String {
        val get = SchemaBridge.open(state)
        val type = resolveNavigation(get)
        val brand = resolveBrand(get)
        val chrome = Chrome().theme(theme).layout(layout)
        for (hook in hooks) chrome.hook(hook.first, hook.second)
        chrome.hook(Hook.TOPBAR_START, topHtml(type, request.path, brand, get))
        chrome.hook(Hook.TOPBAR_END, tenantHtml(request) + userHtml(request.path, get))
        chrome.hook(Hook.SIDEBAR_START, sideHtml(type, request.path, get))
        chrome.hook(Hook.BODY_START, body)
        return chrome.render()
    }

    private fun loadDiscovered(): Discovered {
        val dir = directory
        if (dir == null) return Discovered(emptyList(), emptyList(), emptyList())
        return Discovered(
            PanelDiscovery.pages(dir),
            PanelDiscovery.resources(dir),
            PanelDiscovery.widgets(dir),
        )
    }

    private fun adopt(discovered: Discovered) {
        for (page in discovered.pages) pages.add(page)
        for (resource in discovered.resources) resources.add(resource)
        for (widget in discovered.widgets) widgets.add(widget)
        val seen = HashSet<String>()
        for (page in pages) {
            val key = page.slug()
            if (!seen.add(key)) throw FireflyException("Panel page [$key] is declared more than once")
        }
        if (items.isEmpty()) {
            for (page in pages) {
                val node = NavigationItem(page.slug().ifEmpty { "dashboard" })
                    .label(page.navigationLabel())
                    .url(panelUri(resolvedPrefix, page.slug()))
                val parent = page.parentName()
                if (parent == null) {
                    items.add(node)
                } else {
                    val found = findItem(parent)
                    if (found == null) throw FireflyException("Navigation item [${node.name}] names missing parent [$parent]")
                    found.child(node)
                }
            }
            for (resource in resources) {
                val cluster = NavigationItem(resource.slug()).label(resource.label()).cluster(true)
                for (page in resource.pages()) {
                    cluster.child(
                        NavigationItem(page.slug().ifEmpty { "page" })
                            .label(page.navigationLabel())
                            .url(panelUri(resolvedPrefix, resource.slug() + "/" + page.slug())),
                    )
                }
                items.add(cluster)
            }
        }
        if (widgets.isNotEmpty()) {
            val home = pages.firstOrNull { it.slug().isEmpty() }
            val target = if (home == null) pages.firstOrNull() else home
            if (target != null) target.widgets(*widgets.toTypedArray())
        }
        for (item in items) item.assertDepth(1)
    }

    private fun findItem(name: String): NavigationItem? {
        for (item in items) {
            val found = item.find(name)
            if (found != null) return found
        }
        return null
    }

    private fun authRoutes(planned: MutableList<Planned>) {
        planned.add(Planned("GET", uri("login"), routeName("login")) { request -> loginGet(request) })
        planned.add(Planned("POST", uri("login"), routeName("login.store")) { request -> loginPost(request) })
        planned.add(Planned("GET", uri("register"), routeName("register")) { request -> registerGet(request) })
        planned.add(Planned("POST", uri("register"), routeName("register.store")) { request -> registerPost(request) })
        planned.add(Planned("GET", uri("password-reset"), routeName("password")) { request -> resetGet(request) })
        planned.add(Planned("POST", uri("password-reset"), routeName("password.store")) { request -> resetPost(request) })
        planned.add(Planned("GET", uri("email-verify"), routeName("verify")) { request -> verifyGet(request) })
        planned.add(Planned("POST", uri("email-verify"), routeName("verify.store")) { request -> verifyPost(request) })
        planned.add(Planned("GET", uri("profile"), routeName("profile")) { request -> profileGet(request) })
        planned.add(Planned("POST", uri("profile"), routeName("profile.store")) { request -> profilePost(request) })
    }

    private fun pageRoutes(planned: MutableList<Planned>) {
        for (page in pages) {
            planned.add(Planned("GET", uri(page.slug()), routeName("pages." + page.slug().ifEmpty { "dashboard" })) { request ->
                showPage(request, page)
            })
        }
        for (resource in resources) {
            for (page in resource.pages()) {
                val slug = resource.slug() + "/" + page.slug()
                planned.add(Planned("GET", uri(slug), routeName("resources.$slug")) { request -> showPage(request, page) })
            }
        }
    }

    private fun showPage(request: Request, page: PanelPage): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !panelGuard.check()) {
            return Response.redirect(uri("login"))
        }
        val tenant = tenantId(request)
        return FireflyTenant.use(tenant) {
            Response.html(draw(request, emptyMap(), page.render()))
        }
    }

    private fun loginGet(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (panelGuard.check()) return Response.redirect(uri(""))
        return Response.html(shell(request, authForm("ff-login", "Login", "login", null)))
    }

    private fun loginPost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val email = request.input("email")
        val password = request.input("password")
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Response.html(shell(request, authForm("ff-login", "Login", "login", "Email and password are required")), 422)
        }
        if (!panelGuard.attempt(email, password)) {
            return Response.html(shell(request, authForm("ff-login", "Login", "login", "Those credentials do not match")), 422)
        }
        return Response.redirect(uri(""))
    }

    private fun registerGet(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        return Response.html(shell(request, authForm("ff-register", "Register", "register", null)))
    }

    private fun registerPost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val name = request.input("name")
        val email = request.input("email")
        val password = request.input("password")
        val error = panelGuard.register(
            if (name == null) "" else name,
            if (email == null) "" else email,
            if (password == null) "" else password,
        )
        if (error != null) return Response.html(shell(request, authForm("ff-register", "Register", "register", error)), 422)
        return Response.redirect(uri("login"))
    }

    private fun resetGet(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        return Response.html(shell(request, authForm("ff-password-reset", "Reset password", "password-reset", null)))
    }

    private fun resetPost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val email = request.input("email")
        val error = panelGuard.requestReset(if (email == null) "" else email)
        if (error != null) {
            return Response.html(shell(request, authForm("ff-password-reset", "Reset password", "password-reset", error)), 422)
        }
        return Response.html(shell(request, authForm("ff-password-reset", "Reset password", "password-reset", "Reset link sent")))
    }

    private fun verifyGet(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        return Response.html(shell(request, authForm("ff-email-verify", "Verify email", "email-verify", null)))
    }

    private fun verifyPost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val email = request.input("email")
        val error = panelGuard.verify(if (email == null) "" else email)
        if (error != null) {
            return Response.html(shell(request, authForm("ff-email-verify", "Verify email", "email-verify", error)), 422)
        }
        return Response.html(shell(request, authForm("ff-email-verify", "Verify email", "email-verify", "Email verified")))
    }

    private fun profileGet(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (!panelGuard.check()) return Response.redirect(uri("login"))
        val id = panelGuard.id()
        val shown = if (id == null) "" else id
        return Response.html(shell(request, """<section class="ff-profile"><h1>Profile</h1><p>$shown</p></section>"""))
    }

    private fun profilePost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        panelGuard.logout()
        return Response.redirect(uri("login"))
    }

    private fun gate(request: Request): Response? {
        val expected = resolveDomain(SchemaBridge.open(emptyMap()))
        if (expected == null) return null
        val host = request.header("Host")
        if (host == null) return Response.html("Panel [$resolvedId] expected host [$expected]", 404)
        val bare = host.substringBefore(':')
        if (bare != expected) return Response.html("Panel [$resolvedId] expected host [$expected]", 404)
        return null
    }

    private fun shell(request: Request, body: String): String = draw(request, emptyMap(), body)

    private fun authForm(css: String, title: String, action: String, error: String?): String {
        val message = if (error == null) "" else """<p class="ff-auth-error">${escapeHtml(error)}</p>"""
        return """<section class="$css"><h1>${escapeHtml(title)}</h1>$message<form method="post" action="${escapeHtml(uri(action))}"><input name="name"><input name="email"><input name="password" type="password"><button type="submit">${escapeHtml(title)}</button></form></section>"""
    }

    private fun topHtml(type: NavigationType, path: String, brand: String, get: Get): String {
        val brandHtml = """<span class="ff-brand">${escapeHtml(brand)}</span>"""
        if (type == NavigationType.SIDE) return brandHtml
        val nested = type == NavigationType.TOP
        return brandHtml + """<nav class="ff-nav ff-nav-top">${renderItems(items, path, spaMode, get, nested)}</nav>"""
    }

    private fun sideHtml(type: NavigationType, path: String, get: Get): String {
        if (type == NavigationType.TOP) return ""
        val nodes = if (type == NavigationType.APP) appSide(path) else items
        return """<nav class="ff-nav ff-nav-side">${renderItems(nodes, path, spaMode, get, true)}</nav>"""
    }

    private fun appSide(path: String): List<NavigationItem> {
        var active: NavigationItem? = null
        for (item in items) {
            if (activeItem(path, item.urlText(SchemaBridge.open(emptyMap())))) active = item
        }
        val chosen = if (active == null) items.firstOrNull() else active
        if (chosen == null) return emptyList()
        return chosen.children
    }

    private fun tenantId(request: Request): String? {
        if (tenantList.isEmpty()) return null
        val raw = request.query["tenant"]
        if (raw == null || raw.isBlank()) return null
        if (tenantList.none { it.id == raw }) throw FireflyException("Unknown tenant [$raw]")
        return raw
    }

    private fun tenantHtml(request: Request): String {
        if (tenantList.isEmpty()) return ""
        val current = request.query["tenant"]
        val links = StringBuilder()
        for (tenant in tenantList) {
            val active = if (tenant.id == current) " ff-active" else ""
            val href = request.path + "?tenant=" + tenant.id
            links.append("""<a class="ff-tenant$active" href="${escapeHtml(href)}">${escapeHtml(tenant.label())}</a>""")
        }
        return """<nav class="ff-tenants">$links</nav>"""
    }

    private fun userHtml(path: String, get: Get): String {
        val id = panelGuard.id()
        val who = if (id == null) "Guest" else id
        val menu = renderItems(userItems, path, spaMode, get, true)
        return """<div class="ff-user-menu"><span class="ff-user">$who</span>$menu</div>"""
    }

    private fun uri(slug: String): String = panelUri(resolvedPrefix, slug)

    private fun routeName(suffix: String): String = "firefly.$resolvedId.$suffix"

    private fun resolveId(get: Get): String {
        val provider = idUsing
        if (provider != null) {
            val value = provider.apply(get)
            if (value.isBlank()) throw FireflyException("Panel id is blank")
            return value
        }
        return idLiteral
    }

    /**
     * When [path] was not called, the URL prefix is the panel id.
     * [path] with `""` serves the site root. Any other value is that prefix.
     */
    private fun resolvePath(get: Get, id: String): String {
        val provider = pathUsing
        if (provider != null) return provider.apply(get)
        if (!pathExplicit) return id
        return pathLiteral
    }

    private fun resolveBrand(get: Get): String {
        val provider = brandUsing
        if (provider != null) {
            val value = provider.apply(get)
            if (value.isBlank()) throw FireflyException("Panel brand is blank")
            return value
        }
        return brandLiteral
    }

    private fun resolveDomain(get: Get): String? {
        val provider = domainUsing
        if (provider != null) {
            val value = provider.apply(get)
            if (value.isBlank()) throw FireflyException("Panel domain is blank")
            return value
        }
        return domainLiteral
    }

    private fun resolveNavigation(get: Get): NavigationType {
        val provider = navigationUsing
        if (provider != null) return provider.apply(get)
        return navigationLiteral
    }

    private class Planned(val method: String, val uri: String, val name: String, val handler: Handler)

    private class Discovered(val pages: List<PanelPage>, val resources: List<Resource>, val widgets: List<Widget>)
}
