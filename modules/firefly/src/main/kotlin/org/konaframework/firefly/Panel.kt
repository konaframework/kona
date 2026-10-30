package org.konaframework.firefly

import org.konaframework.auth.Auth
import org.konaframework.auth.Guard
import org.konaframework.firefly.notifications.FireflyNotification
import org.konaframework.firefly.notifications.FireflyToasts
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.support.AuthLayout
import org.konaframework.firefly.support.BrandDisplay
import org.konaframework.firefly.support.BrandingOverrideContext
import org.konaframework.firefly.support.ChromeMode
import org.konaframework.firefly.support.DefaultLayout
import org.konaframework.firefly.support.FireflyBranding
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.FireflyTheme
import org.konaframework.firefly.support.Hook
import org.konaframework.firefly.support.Icon
import org.konaframework.firefly.support.KonaTheme
import org.konaframework.firefly.support.MaxWidth
import org.konaframework.firefly.support.PanelBranding
import org.konaframework.firefly.support.PanelLayout
import org.konaframework.firefly.support.brandMarkHtml
import org.konaframework.firefly.support.mergePanelBranding
import org.konaframework.firefly.support.resolveEffectiveBranding
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.schemas.htmlAttributes
import org.konaframework.firefly.widgets.Widget
import org.konaframework.http.Handler
import org.konaframework.http.Request
import org.konaframework.http.RequestSession
import org.konaframework.http.Response
import org.konaframework.http.Router
import org.konaframework.icons.Icons
import java.net.URLEncoder
import java.nio.file.Path
import java.util.function.Function
import org.konaframework.firefly.support.Panel as Chrome
import org.konaframework.firefly.tables.BulkActionResult

private const val BULK_ACTION = "_bulk_action"
private const val BULK_KEYS = "_bulk_keys"
private const val BULK_ALL = "_bulk_all"

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
    private var panelBranding: PanelBranding = PanelBranding()
    private var brandingOverrides: Function<BrandingOverrideContext, PanelBranding?>? = null
    private var profilePageOverride: ProfilePage? = null
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
    private var bindAppGuard: ((Request) -> Unit)? = null
    private var authenticatePages: Boolean = true
    private var spaMode: Boolean = false
    private var directory: Path? = null
    private val tenantList = ArrayList<Tenant>()
    private val plugins = ArrayList<FireflyPlugin>()
    private var mounted: Boolean = false
    private var resolvedPrefix: String = "admin"
    private var resolvedId: String = "admin"
    private var maxWidthLiteral: String? = null

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
        panelBranding = panelBranding.copy(name = value)
    }

    fun brand(provider: Function<Get, String>): Panel = apply {
        brandUsing = provider
    }

    /** Merge panel chrome branding (Shamar `panel.branding(...)`). */
    fun branding(value: PanelBranding): Panel = apply {
        panelBranding = mergePanelBranding(panelBranding, value)!!
        val name = panelBranding.name?.trim().orEmpty()
        if (name.isEmpty()) return@apply
        brandLiteral = name
        brandUsing = null
    }

    fun brandDisplay(mode: BrandDisplay): Panel = branding(PanelBranding(brandDisplay = mode))

    fun brandLogoOnly(): Panel = brandDisplay(BrandDisplay.LOGO)

    fun brandNameOnly(): Panel = brandDisplay(BrandDisplay.NAME)

    fun brandLogoAndName(): Panel = brandDisplay(BrandDisplay.BOTH)

    fun logo(url: String): Panel = branding(PanelBranding(logo = url))

    fun logoDark(url: String): Panel = branding(PanelBranding(logoDark = url))

    fun logoHeight(value: Any): Panel = branding(PanelBranding(logoHeight = value))

    /**
     * Runtime branding overrides (DB settings, company logo, etc.).
     * Merged over [branding] when rendering shell and auth.
     */
    fun resolveBrandingOverrides(resolver: Function<BrandingOverrideContext, PanelBranding?>): Panel =
        apply { brandingOverrides = resolver }

    /** Per-panel [ProfilePage]. Wins over [Firefly.defaultProfilePage]. */
    fun profilePage(page: ProfilePage): Panel = apply { profilePageOverride = page }

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

    /**
     * Default content max-width for pages and resources (`xs`…`7xl`, `full`, `screen`, `prose`).
     * Pages and resources can narrow further with their own [org.konaframework.firefly.widgets.Page.maxWidth].
     * Tables fill that column; only a page/resource max width shrinks them.
     * Alias of Filament-style `maxContentWidth`.
     */
    fun maxWidth(width: String): Panel = apply {
        maxWidthLiteral = MaxWidth.requireKnown(width)
    }

    /** Filament-shaped alias for [maxWidth]. */
    fun maxContentWidth(width: String): Panel = maxWidth(width)

    fun hook(hook: Hook, html: String): Panel = apply { hooks.add(hook to html) }

    fun spa(on: Boolean): Panel = apply { spaMode = on }

    fun authenticate(on: Boolean): Panel = apply { authenticatePages = on }

    fun guard(guard: PanelGuard): Panel = apply {
        panelGuard = guard
        guardNameLiteral = null
        usesAppGuard = false
        bindAppGuard = null
    }

    fun useAuthGuard(guard: Guard): Panel = apply {
        panelGuard = AuthPanelGuard(guard)
        guardNameLiteral = null
        usesAppGuard = true
        bindAppGuard = { guard.setRequest(it) }
    }

    fun reuseAppGuard(name: String = "web"): Panel = apply {
        if (name.isBlank()) throw FireflyException("Panel guard name is blank")
        guardNameLiteral = name
        usesAppGuard = true
        panelGuard = AuthPanelGuard { Auth.guard(name) }
        bindAppGuard = { Auth.setRequest(it) }
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
        val branding = effectiveBranding(request)
        val brand = branding.brandName
        val token = FireflyCsrf.ensure(request)
        val chrome = Chrome().theme(theme).layout(layout)
            .csrfToken(token)
            .documentTitle(brand)
            .branding(branding)
            .mode(ChromeMode.PANEL)
        val width = maxWidthLiteral
        if (width != null) chrome.maxWidth(width)
        for (hook in hooks) chrome.hook(hook.first, hook.second)
        val guard = activeGuard(request)
        val user = ChromeUser(guard.id(), guard.name(), guard.email())
        chrome.hook(Hook.TOPBAR_START, topHtml(type, request.path, branding, get))
        chrome.hook(Hook.TOPBAR_END, tenantHtml(request) + userHtml(request, request.path, user, get))
        chrome.hook(Hook.SIDEBAR_START, sideHtml(type, request.path, branding, user, get))
        chrome.hook(Hook.BODY_START, body)
        val toasts = flashToasts(request)
        if (toasts.isNotEmpty()) chrome.hook(Hook.BODY_END, toasts)
        return chrome.render()
    }

    /** Pull flashed Firefly toasts for this response (session flash key `firefly.notifications`). */
    private fun flashToasts(request: Request): String {
        val session = request.session() ?: return ""
        return try {
            FireflyNotification.pull(session)
        } catch (_: FireflyException) {
            ""
        }
    }

    private fun flashNotice(request: Request, notice: (RequestSession) -> Unit) {
        val session = request.session() ?: return
        notice(session)
    }

    private fun drawAuth(request: Request, title: String, body: String): String {
        val branding = effectiveBranding(request)
        val brand = branding.brandName
        val token = FireflyCsrf.ensure(request)
        return Chrome()
            .theme(theme)
            .layout(AuthLayout())
            .csrfToken(token)
            .documentTitle("$brand · $title")
            .branding(branding)
            .mode(ChromeMode.AUTH)
            .overridePartial("body", """<main class="ff-auth-main"><!-- ff-hook:body.start -->$body<!-- ff-hook:body.end --></main>""")
            .render()
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
        if (pages.none { it.slug().isEmpty() }) {
            pages.add(0, welcomeHome())
        }
        val seen = HashSet<String>()
        for (page in pages) {
            val key = page.slug()
            if (!seen.add(key)) throw FireflyException("Panel page [$key] is declared more than once")
        }
        if (items.isEmpty()) {
            for (page in pages) {
                val isHome = page.slug().isEmpty()
                val node = NavigationItem(page.slug().ifEmpty { "dashboard" })
                    .label(page.navigationLabel())
                    .url(panelUri(resolvedPrefix, page.slug()))
                    .dashboard(isHome)
                val pageIcon = page.navigationIcon()
                if (!pageIcon.isNullOrBlank()) node.icon(pageIcon)
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
                // One sidebar entry per resource — it links to the resource index (list/manage)
                // page and stays active across create/edit/view. Those operations are page
                // header and row actions, never standalone navigation links (Shamar admin shell).
                val resourcePages = resource.pages()
                val index = resourceIndexPage(resourcePages)
                val indexSlug = if (index == null) "" else index.slug()
                val node = NavigationItem(resource.slug())
                    .label(resource.pluralLabel())
                    .url(panelUri(resolvedPrefix, resourcePagePath(resource.slug(), indexSlug)))
                    .matchPrefix(panelUri(resolvedPrefix, resource.slug()))
                val resourceIcon = resource.navigationIcon()
                if (!resourceIcon.isNullOrBlank()) node.icon(resourceIcon)
                items.add(node)
            }
        }
        if (widgets.isNotEmpty()) {
            // adopt always ensures an empty-slug home before this runs
            pages.first { it.slug().isEmpty() }.widgets(*widgets.toTypedArray())
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
        planned.add(Planned("POST", uri("logout"), routeName("logout")) { request -> logoutPost(request) })
    }

    private fun pageRoutes(planned: MutableList<Planned>) {
        planned.add(
            Planned("GET", uri("widgets/{widgetId}"), routeName("widgets.show")) { request ->
                showWidget(request, widgetIdFrom(request))
            },
        )
        for (page in pages) {
            planned.add(Planned("GET", uri(page.slug()), routeName("pages." + page.slug().ifEmpty { "dashboard" })) { request ->
                showPage(request, page)
            })
            planned.add(Planned("POST", uri(page.slug()), routeName("pages." + page.slug().ifEmpty { "dashboard" } + ".bulk")) { request ->
                bulkAction(request, page)
            })
            val optionsSlug = if (page.slug().isEmpty()) "form-options" else page.slug() + "/form-options"
            planned.add(Planned("GET", uri(optionsSlug), routeName("pages.$optionsSlug")) { request ->
                pageFormOptions(request, page)
            })
        }
        for (resource in resources) {
            val resourcePages = resource.pages()
            val index = resourceIndexPage(resourcePages) ?: resourcePages.firstOrNull()
            val listSlug = if (index == null) {
                resource.slug()
            } else {
                resourcePagePath(resource.slug(), index.slug())
            }
            for (page in resourcePages) {
                val slug = resourcePagePath(resource.slug(), page.slug())
                planned.add(
                    Planned("GET", uri(slug), routeName("resources.$slug")) { request ->
                        showPage(
                            request,
                            page,
                            mapOf(
                                "panelHome" to uri(""),
                                "resourceList" to uri(listSlug),
                                "resourceBase" to uri(resource.slug()),
                            ),
                        )
                    },
                )
                if (page is CreatePage || page is EditPage) {
                    planned.add(
                        Planned("POST", uri(slug), routeName("resources.$slug.store")) { request ->
                            storeResource(request, resource, page)
                        },
                    )
                } else {
                    // List / manage tables post their bulk-action forms back to the page URL.
                    planned.add(
                        Planned("POST", uri(slug), routeName("resources.$slug.bulk")) { request ->
                            bulkAction(request, page, mapOf("panelHome" to uri(""), "resourceList" to uri(listSlug)))
                        },
                    )
                }
            }
            // Simple resources only GET ManagePage — still need create/edit store POSTs for dialogs.
            val hasManage = resourcePages.any { it is ManagePage }
            val hasCreate = resourcePages.any { it is CreatePage }
            val hasEdit = resourcePages.any { it is EditPage }
            if (hasManage && !hasCreate) {
                val createSlug = resourcePagePath(resource.slug(), "create")
                planned.add(
                    Planned("POST", uri(createSlug), routeName("resources.$createSlug.store")) { request ->
                        storeResource(request, resource, CreatePage("create"))
                    },
                )
            }
            if (hasManage && !hasEdit) {
                val editSlug = resourcePagePath(resource.slug(), "edit")
                planned.add(
                    Planned("POST", uri(editSlug), routeName("resources.$editSlug.store")) { request ->
                        storeResource(request, resource, EditPage("edit"))
                    },
                )
            }
            val deleteSlug = resourcePagePath(resource.slug(), "delete")
            planned.add(
                Planned("POST", uri(deleteSlug), routeName("resources.$deleteSlug")) { request ->
                    deleteResource(request, resource)
                },
            )
            val optionsSlug = resource.slug() + "/form-options"
            planned.add(
                Planned("GET", uri(optionsSlug), routeName("resources.$optionsSlug")) { request ->
                    formOptions(request, resource)
                },
            )
            if (resourcePages.any { it is EditPage || it is ViewPage }) relationRoutes(planned, resource)
        }
    }

    /**
     * Relation manager endpoints under `{resource}/relations/`: one POST per operation, the bulk
     * POST, and the associate / attach picker search. Query `relation`, `owner`, and `page` name
     * the manager, the owner record, and the edit / view page to return to.
     */
    private fun relationRoutes(planned: MutableList<Planned>, resource: Resource) {
        val base = resource.slug() + "/" + Resource.RELATIONS_PATH
        for (operation in RelationOperation.entries) {
            if (operation == RelationOperation.VIEW) continue
            val slug = "$base/${operation.token}"
            planned.add(
                Planned("POST", uri(slug), routeName("resources.$slug")) { request ->
                    relationPost(request, resource, operation)
                },
            )
        }
        planned.add(
            Planned("GET", uri("$base/options"), routeName("resources.$base/options")) { request ->
                relationOptions(request, resource)
            },
        )
    }

    private fun relationPost(request: Request, resource: Resource, operation: RelationOperation): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(uri("login"))
        }
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        val tenant = tenantId(request)
        return FireflyTenant.use(tenant) {
            val runtime = relationRuntime(request, resource)
            if (runtime !is RelationRuntime) return@use runtime as Response
            val back = uri(resourcePagePath(resource.slug(), runtime.pageSlug)) +
                "?id=${URLEncoder.encode(runtime.ownerKey, Charsets.UTF_8)}" +
                "&relation=${URLEncoder.encode(runtime.manager.name(), Charsets.UTF_8)}"
            val input = request.all().filterKeys { it !in request.query && it != FireflyCsrf.TOKEN_KEY }
            if (operation == RelationOperation.BULK) {
                if (runtime.readOnly) return@use Response.html("Relation is read-only here", 403)
                val name = input[BULK_ACTION]
                if (name.isNullOrBlank()) return@use Response.html("Bulk action is required", 422)
                val table = runtime.manager.table(runtime, emptyMap())
                val keys = input[BULK_KEYS].orEmpty().split(',').map { it.trim() }
                val data = input.filterKeys { !it.startsWith("_") }
                val result = try {
                    RelationScope.with(runtime) { table.runBulkAction(name, keys, data, input[BULK_ALL] == "1") }
                } catch (e: FireflyException) {
                    return@use Response.html(escapeHtml(e.message!!), 422)
                }
                flashBulk(request, result)
                return@use Response.redirect(back)
            }
            val outcome = runtime.manager.perform(runtime, operation, input)
            if (outcome.status == 403 || outcome.status == 404) return@use Response.html(escapeHtml(outcome.title), outcome.status)
            val session = request.session()
            if (session != null) {
                val toast = FireflyNotification.make("relation-${operation.token}").title(outcome.title)
                if (outcome.messages.isNotEmpty()) toast.body(outcome.messages.joinToString(" "))
                if (outcome.isSuccessful()) toast.success() else toast.danger()
                toast.send(session)
            }
            Response.redirect(back)
        }
    }

    /** Picker options for associate / attach: `[{value, label, enabled}]`, searched by `q`. */
    private fun relationOptions(request: Request, resource: Resource): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(uri("login"))
        }
        val tenant = tenantId(request)
        return FireflyTenant.use(tenant) {
            val runtime = relationRuntime(request, resource)
            if (runtime !is RelationRuntime) return@use runtime as Response
            val operation = RelationOperation.fromToken(request.input("op").orEmpty())
            if (operation != RelationOperation.ASSOCIATE && operation != RelationOperation.ATTACH) {
                return@use Response.html("Unknown picker operation", 422)
            }
            val options = runtime.manager.options(runtime, operation, request.input("q").orEmpty()).map { (value, label) ->
                linkedMapOf("value" to value, "label" to label, "enabled" to true)
            }
            Response.json(options)
        }
    }

    /** The manager bound to the posted owner on its page, or the error [Response] to return. */
    private fun relationRuntime(request: Request, resource: Resource): Any {
        val pageSlug = request.query["page"].orEmpty()
        val page = resource.pages().firstOrNull { it.slug() == pageSlug && (it is EditPage || it is ViewPage) }
            ?: return Response.html("Unknown relation page", 422)
        val name = request.query["relation"].orEmpty()
        val manager = resource.relationManagers().firstOrNull { it.name() == name }
            ?: return Response.html("Unknown relation", 404)
        val key = request.query["owner"].orEmpty()
        val owner = (if (key.isBlank()) null else resource.relationOwner(key))
            ?: return Response.html("Record not found", 404)
        val runtime = try {
            manager.bind(owner, page, uri(resource.slug() + "/" + Resource.RELATIONS_PATH), "")
        } catch (e: FireflyException) {
            return Response.html(escapeHtml(e.message!!), 422)
        }
        if (!manager.visibleFor(runtime)) return Response.html("Not authorized", 403)
        return runtime
    }

    /** JSON options for relationship (and searchable) Select comboboxes. */
    private fun formOptions(request: Request, resource: Resource): Response {
        val field = request.input("field").orEmpty()
        val query = request.input("q").orEmpty()
        val options = resource.searchFormOptions(field, query).map { option ->
            linkedMapOf(
                "value" to option.value,
                "label" to option.label,
                "enabled" to option.enabled,
            )
        }
        return Response.json(options)
    }

    /** The page a resource nav entry links to: its list, else manage, else first page. */
    private fun resourceIndexPage(pages: List<PanelPage>): PanelPage? {
        val list = pages.firstOrNull { it is ListPage }
        if (list != null) return list
        val manage = pages.firstOrNull { it is ManagePage }
        if (manage != null) return manage
        return pages.firstOrNull()
    }

    /** Joins a resource slug and a page slug, mounting an empty-slug page at the resource root. */
    private fun resourcePagePath(resourceSlug: String, pageSlug: String): String =
        if (pageSlug.isEmpty()) resourceSlug else "$resourceSlug/$pageSlug"

    private fun showPage(
        request: Request,
        page: PanelPage,
        extras: Map<String, Any?> = emptyMap(),
    ): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(uri("login"))
        }
        val tenant = tenantId(request)
        return FireflyTenant.use(tenant) {
            Response.html(draw(request, emptyMap(), page.render(pageState(request, dashboardExtras(extras)))))
        }
    }

    private fun widgetIdFrom(request: Request): String? {
        val routed = request.route("widgetId") ?: request.input("widgetId")
        if (!routed.isNullOrBlank()) return routed
        val marker = "/widgets/"
        val idx = request.path.indexOf(marker)
        if (idx < 0) return null
        val rest = request.path.substring(idx + marker.length).trim('/')
        return rest.substringBefore('/').ifBlank { null }
    }

    /** Fragment HTML for one dashboard widget (lazy load / polling). */
    private fun showWidget(request: Request, widgetId: String?): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(uri("login"))
        }
        if (widgetId.isNullOrBlank()) return Response.html("Widget id is required", 422)
        val decoded = java.net.URLDecoder.decode(widgetId, Charsets.UTF_8)
        val home = pages.firstOrNull { it.slug().isEmpty() }
            ?: return Response.html("Dashboard not found", 404)
        val widget = home.findWidget(decoded)
            ?: return Response.html("Widget not found", 404)
        val tenant = tenantId(request)
        return FireflyTenant.use(tenant) {
            val state = pageState(
                request,
                dashboardExtras(
                    mapOf(
                        "hydrateWidget" to decoded,
                        "hydrateWidgets" to true,
                    ),
                ),
            )
            home.prepare(state)
            Response.html(widget.render(state))
        }
    }

    private fun dashboardExtras(extras: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["widgetRefreshBase"] = uri("").trimEnd('/')
        out["navigationCards"] = navigationCards()
        out.putAll(extras)
        return out
    }

    private fun navigationCards(): List<org.konaframework.firefly.widgets.NavigationCard> {
        val get = SchemaBridge.open(emptyMap())
        return items.map { item ->
            org.konaframework.firefly.widgets.NavigationCard(
                label = item.labelText(get),
                href = item.urlText(get),
                icon = item.iconName(get),
            )
        }
    }

    /**
     * Run a table bulk action posted from [page] (`_bulk_action`, comma-separated `_bulk_keys`,
     * `_bulk_all=1` for every matching record), flash the result, and redirect back.
     */
    private fun bulkAction(request: Request, page: PanelPage, extras: Map<String, Any?> = emptyMap()): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(uri("login"))
        }
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        val name = request.input(BULK_ACTION)
        if (name.isNullOrBlank()) {
            if (page.mountedForm() != null) return pageFormSubmit(request, page)
            return Response.html("Bulk action is required", 422)
        }
        val tenant = tenantId(request)
        return FireflyTenant.use(tenant) {
            page.prepare(pageState(request, extras))
            val table = page.mountedTable() ?: return@use Response.html("Page has no table", 404)
            val keys = request.input(BULK_KEYS).orEmpty().split(',').map { it.trim() }
            val data = request.all().filterKeys { !it.startsWith("_") && it !in request.query }
            val result = try {
                table.runBulkAction(name, keys, data, request.input(BULK_ALL) == "1")
            } catch (e: FireflyException) {
                return@use Response.html(escapeHtml(e.message!!), 422)
            }
            flashBulk(request, result)
            val query = request.query.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
            }
            Response.redirect(if (query.isEmpty()) request.path else "${request.path}?$query")
        }
    }

    /**
     * A custom page's mounted form posted to the page (Enter key or a `submitForm` action).
     * Hands the posted fields to [PanelPage.onFormSubmit], flashes its notification, and redirects back.
     */
    private fun pageFormSubmit(request: Request, page: PanelPage): Response {
        if (page is SettingsPage && !page.canSave(activeGuard(request))) {
            return Response.html("You cannot save settings", 403)
        }
        val input = request.all().filterKeys { it != FireflyCsrf.TOKEN_KEY && !it.startsWith("_") && it !in request.query }
        val title = FireflyTenant.use(tenantId(request)) { page.submitForm(input) }
        val session = request.session()
        if (session != null) FireflyNotification.make("page-form").title(title).success().send(session)
        return Response.redirect(request.path)
    }

    /** JSON options for a custom page's relationship / searchable Select (`{page}/form-options`). */
    private fun pageFormOptions(request: Request, page: PanelPage): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(uri("login"))
        }
        val form = page.mountedForm() ?: return Response.json(emptyList<Any>())
        val select = form.component(request.input("field").orEmpty()) as? org.konaframework.firefly.forms.Select
            ?: return Response.json(emptyList<Any>())
        val options = FireflyTenant.use(tenantId(request)) { select.search(form, request.input("q").orEmpty()) }
        return Response.json(
            options.map { option ->
                linkedMapOf("value" to option.value, "label" to option.label, "enabled" to option.enabled)
            },
        )
    }

    /** Filament bulk notification: success, warning when some records failed, danger when none succeeded. */
    private fun flashBulk(request: Request, result: BulkActionResult) {
        val session = request.session() ?: return
        val title = result.notificationTitle()
        if (title == null && result.messages.isEmpty()) return
        val toast = FireflyNotification.make("bulk-${result.action}").title(title ?: "Bulk action finished")
        if (result.messages.isNotEmpty()) toast.body(result.messages.joinToString(" "))
        when {
            result.isSuccessful() -> toast.success()
            result.successCount > 0 -> toast.warning()
            else -> toast.danger()
        }
        toast.send(session)
    }

    /** CSRF field, form action, and list query params for page/table render (Shamar). */
    private fun pageState(request: Request, extras: Map<String, Any?>): Map<String, Any?> {
        val state = LinkedHashMap<String, Any?>()
        state["csrfField"] = FireflyCsrf.field(FireflyCsrf.ensure(request))
        state["formAction"] = request.path
        state["panelHome"] = uri("")
        val search = request.input("search")
        if (!search.isNullOrBlank()) state["search"] = search
        val sort = request.input("sort")
        if (!sort.isNullOrBlank()) state["sort"] = sort
        val direction = request.input("direction")
        if (!direction.isNullOrBlank()) state["direction"] = direction
        val page = request.input("page")
        if (!page.isNullOrBlank()) state["page"] = page
        val perPage = request.input("perPage")
        if (!perPage.isNullOrBlank()) state["perPage"] = perPage
        // Present even when empty so list soft-nav can hide all toggleable columns (#363).
        val columns = request.input("columns")
        if (columns != null) {
            state["columns"] = columns
        }
        val record = request.input("id") ?: request.input("record")
        if (!record.isNullOrBlank()) state["record"] = record
        val relation = request.input("relation")
        if (!relation.isNullOrBlank()) state["relation"] = relation
        // Table filters: `filters` / `{id}Filters` sentinels and `{prefix}.<name>[.<field>]` (#388).
        state["filterInput"] = request.all().filterKeys { isFilterQueryKey(it) }
        val session = request.session()
        if (session != null) state["filterSession"] = session
        state.putAll(extras)
        return state
    }

    /** True for `filters` / `filters.*` and namespaced `{id}Filters` / `{id}Filters.*` (#388). */
    private fun isFilterQueryKey(key: String): Boolean {
        if (key == "filters" || key.startsWith("filters.")) return true
        if (key.endsWith("Filters")) return true
        val dot = key.indexOf("Filters.")
        return dot > 0
    }

    /**
     * Delete a record by `id` / `record` and redirect to the resource list.
     */
    private fun deleteResource(request: Request, resource: Resource): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(uri("login"))
        }
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        val tenant = tenantId(request)
        return FireflyTenant.use(tenant) {
            val key = request.input("id") ?: request.input("record")
            if (key.isNullOrBlank()) {
                return@use Response.html("Record id is required", 422)
            }
            @Suppress("UNCHECKED_CAST")
            val store = org.konaframework.firefly.tables.RecordStores.open(resource.boundModel())
                as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
            val found = store.find(key) ?: return@use Response.html("Record not found", 404)
            store.delete(found)
            flashNotice(request, FireflyToasts::deleted)
            Response.redirect(uri(resourceListSlug(resource)))
        }
    }

    private fun resourceListSlug(resource: Resource): String {
        val index = resource.pages().firstOrNull { it is ListPage }
            ?: resource.pages().firstOrNull { it is ManagePage }
        return if (index == null) {
            resource.slug()
        } else {
            resourcePagePath(resource.slug(), index.slug())
        }
    }

    /**
     * Persist a create/edit form POST. Fields map onto the resource model via [RecordStores].
     * Redirects to the resource list on success (Shamar after-create).
     */
    private fun storeResource(request: Request, resource: Resource, page: PanelPage): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(uri("login"))
        }
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        val tenant = tenantId(request)
        return FireflyTenant.use(tenant) {
            @Suppress("UNCHECKED_CAST")
            val store = org.konaframework.firefly.tables.RecordStores.open(resource.boundModel())
                as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
            val attrs = LinkedHashMap<String, Any?>()
            for ((key, value) in request.all()) {
                if (key == FireflyCsrf.TOKEN_KEY || key == "id" || key.startsWith("_")) continue
                // Action modal fields used to nest under create./edit. — map to column names.
                attrs[storeAttributeKey(key)] = value
            }
            val record = if (page is EditPage) {
                val key = request.input("id") ?: request.input("record")
                if (key.isNullOrBlank()) {
                    return@use Response.html("Record id is required", 422)
                }
                val found = store.find(key)
                if (found == null) return@use Response.html("Record not found", 404)
                found
            } else {
                store.newRecord()
            }
            store.fill(record, attrs)
            store.save(record)
            if (page is EditPage) {
                flashNotice(request, FireflyToasts::saved)
            } else {
                flashNotice(request, FireflyToasts::created)
            }
            Response.redirect(uri(resourceListSlug(resource)))
        }
    }

    /** Map posted field names onto model attributes (`create.name` → `name`). */
    private fun storeAttributeKey(raw: String): String =
        if (raw.startsWith("create.") || raw.startsWith("edit.")) {
            raw.substringAfterLast('.')
        } else {
            raw
        }

    private fun loginGet(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (activeGuard(request).check()) return Response.redirect(uri(""))
        return Response.html(authShell(request, "Sign in", authForm(request, AuthScreen.LOGIN, null)))
    }

    private fun loginPost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val email = request.input("email")
        val password = request.input("password")
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Response.html(authShell(request, "Sign in", authForm(request, AuthScreen.LOGIN, "Email and password are required")), 422)
        }
        if (!activeGuard(request).attempt(email, password)) {
            return Response.html(authShell(request, "Sign in", authForm(request, AuthScreen.LOGIN, "Those credentials do not match")), 422)
        }
        return Response.redirect(uri(""))
    }

    private fun registerGet(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        return Response.html(authShell(request, "Register", authForm(request, AuthScreen.REGISTER, null)))
    }

    private fun registerPost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val name = request.input("name")
        val email = request.input("email")
        val password = request.input("password")
        val error = activeGuard(request).register(
            if (name == null) "" else name,
            if (email == null) "" else email,
            if (password == null) "" else password,
        )
        if (error != null) {
            return Response.html(authShell(request, "Register", authForm(request, AuthScreen.REGISTER, error)), 422)
        }
        return Response.redirect(uri("login"))
    }

    private fun resetGet(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        return Response.html(authShell(request, "Reset password", authForm(request, AuthScreen.RESET, null)))
    }

    private fun resetPost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val email = request.input("email")
        val error = activeGuard(request).requestReset(if (email == null) "" else email)
        if (error != null) {
            return Response.html(authShell(request, "Reset password", authForm(request, AuthScreen.RESET, error)), 422)
        }
        return Response.html(authShell(request, "Reset password", authForm(request, AuthScreen.RESET, "Reset link sent")))
    }

    private fun verifyGet(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        return Response.html(authShell(request, "Verify email", authForm(request, AuthScreen.VERIFY, null)))
    }

    private fun verifyPost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val email = request.input("email")
        val error = activeGuard(request).verify(if (email == null) "" else email)
        if (error != null) {
            return Response.html(authShell(request, "Verify email", authForm(request, AuthScreen.VERIFY, error)), 422)
        }
        return Response.html(authShell(request, "Verify email", authForm(request, AuthScreen.VERIFY, "Email verified")))
    }

    private fun profileGet(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val guard = activeGuard(request)
        if (!guard.check()) return Response.redirect(uri("login"))
        val page = resolveProfilePage()
        val state = pageState(request, page.fill(guard))
        return Response.html(shell(request, page.render(state)))
    }

    private fun profilePost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val guard = activeGuard(request)
        if (!guard.check()) return Response.redirect(uri("login"))
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        val page = resolveProfilePage()
        val input = LinkedHashMap<String, String>()
        for ((key, value) in request.all()) {
            if (key == FireflyCsrf.TOKEN_KEY) continue
            if (key.startsWith("_")) continue
            if (key in request.query) continue
            input[key] = value
        }
        return try {
            val title = page.save(guard, input)
            val session = request.session()
            if (session != null) FireflyNotification.make("profile").title(title).success().send(session)
            Response.redirect(uri("profile"))
        } catch (e: FireflyException) {
            Response.html(shell(request, page.render(pageState(request, page.fill(guard) + input + mapOf("error" to e.message)))), 422)
        }
    }

    private fun logoutPost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        activeGuard(request).logout()
        return Response.redirect(uri("login"))
    }

    private fun resolveProfilePage(): ProfilePage =
        profilePageOverride
            ?: Firefly.resolveDefaultProfilePage()
            ?: ProfilePage()

    /** Binds the request onto the app Auth guard when this panel reuses it. */
    private fun activeGuard(request: Request): PanelGuard {
        bindAppGuard?.invoke(request)
        return panelGuard
    }

    /** True when [request] comes from a signed-in user of this panel (FileUpload temp uploads). */
    internal fun allowsUpload(request: Request): Boolean = gate(request) == null && activeGuard(request).check()

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

    private fun authShell(request: Request, title: String, body: String): String = drawAuth(request, title, body)

    private fun authForm(request: Request, screen: AuthScreen, error: String?): String {
        val message =
            if (error == null) {
                ""
            } else {
                """<p class="ff-auth-alert" role="alert">${escapeHtml(error)}</p>"""
            }
        val token = FireflyCsrf.ensure(request)
        val fields = StringBuilder()
        if (screen == AuthScreen.REGISTER) {
            fields.append(
                """<div class="ff-auth-field"><label for="ff-auth-name">Name</label><input id="ff-auth-name" name="name" type="text" autocomplete="name" required /></div>""",
            )
        }
        when (screen) {
            AuthScreen.LOGIN, AuthScreen.REGISTER -> {
                fields.append(
                    """<div class="ff-auth-field"><label for="ff-auth-email">Email</label><input id="ff-auth-email" name="email" type="email" autocomplete="username" required autofocus /></div>""",
                )
                val passwordAutocomplete = if (screen == AuthScreen.LOGIN) "current-password" else "new-password"
                fields.append(
                    """<div class="ff-auth-field"><label for="ff-auth-password">Password</label><input id="ff-auth-password" name="password" type="password" autocomplete="$passwordAutocomplete" required /></div>""",
                )
            }
            AuthScreen.RESET, AuthScreen.VERIFY -> {
                fields.append(
                    """<div class="ff-auth-field"><label for="ff-auth-email">Email</label><input id="ff-auth-email" name="email" type="email" autocomplete="username" required autofocus /></div>""",
                )
            }
        }
        return """
            <div class="ff-auth-panel ${screen.css}">
              <h1 class="ff-auth-title">${escapeHtml(screen.title)}</h1>
              $message
              <form class="ff-auth-form" method="post" action="${escapeHtml(uri(screen.action))}">
                ${FireflyCsrf.field(token)}
                $fields
                <button type="submit" class="ff-auth-submit">${escapeHtml(screen.submit)}</button>
              </form>
            </div>
        """.trimIndent()
    }

    private fun welcomeHome(): DashboardPage {
        val page = DashboardPage()
        page.title("Dashboard")
        page.schema(
            Schema().components(
                Text.make("Welcome. Add pages under pages/ and resources under resources/ to grow this panel."),
            ),
        )
        return page
    }

    private enum class AuthScreen(
        val css: String,
        val title: String,
        val action: String,
        val submit: String,
    ) {
        LOGIN("ff-login", "Sign in", "login", "Sign in"),
        REGISTER("ff-register", "Create account", "register", "Register"),
        RESET("ff-password-reset", "Reset password", "password-reset", "Send reset link"),
        VERIFY("ff-email-verify", "Verify email", "email-verify", "Verify email"),
    }

    private fun topHtml(type: NavigationType, path: String, branding: FireflyBranding, get: Get): String {
        val lead = StringBuilder("""<div class="ff-topbar-lead">""")
        // SIDE/APP own a sidebar, so the bar gets a drawer toggle + a mobile-only brand.
        // TOP has no sidebar, so its brand stays visible in the bar on every width.
        if (type == NavigationType.TOP) {
            lead.append(brandMarkHtml(branding))
        } else {
            lead.append(
                """<button type="button" class="ff-hamburger" data-ff-sidebar-toggle aria-label="Open navigation">${Icon.BARS.svg()}</button>""",
            )
            lead.append(brandMarkHtml(branding, "ff-topbar-brand"))
        }
        // APP: nested levels of the active L1 root (Shamar topbar secondary). TOP: full tree.
        when (type) {
            NavigationType.TOP ->
                lead.append("""<nav class="ff-nav ff-nav-top">${renderItems(items, path, spaMode, get, true)}</nav>""")
            NavigationType.APP -> {
                val nested = appTop(path, get)
                if (nested.isNotEmpty()) {
                    lead.append(
                        """<nav class="ff-nav ff-nav-top">${renderItems(nested, path, spaMode, get, true)}</nav>""",
                    )
                }
            }
            NavigationType.SIDE -> Unit
        }
        lead.append("</div>")
        return lead.toString()
    }

    private fun sideHtml(type: NavigationType, path: String, branding: FireflyBranding, user: ChromeUser, get: Get): String {
        if (type == NavigationType.TOP) return ""
        // Brand block is the elevated top strip; it carries the drawer close button on mobile.
        val logoClass = if (branding.showLogo) " ff-sidebar-brand--logo" else ""
        val brandBlock =
            """<div class="ff-sidebar-brand$logoClass">${brandMarkHtml(branding)}""" +
                """<button type="button" class="ff-sidebar-close" data-ff-sidebar-close aria-label="Close navigation">${Icon.X.svg()}</button></div>"""
        // APP: L1 roots only (children go to the top bar). SIDE: full nested tree in the sidebar.
        val nested = type == NavigationType.SIDE
        val nav = """<nav class="ff-nav ff-nav-side">${renderItems(items, path, spaMode, get, nested)}</nav>"""
        return brandBlock + nav + sidebarFootHtml(user, branding)
    }

    /** Sidebar footer with the "Signed in as …" line (Shamar sidebar user block). */
    private fun sidebarFootHtml(user: ChromeUser, branding: FireflyBranding): String {
        val copyrightText = branding.copyrightText
        val copyright =
            if (copyrightText.isNullOrBlank()) {
                ""
            } else {
                """<p class="ff-sidebar-copyright">${escapeHtml(copyrightText)}</p>"""
            }
        return """<div class="ff-sidebar-foot">$copyright<span>Signed in as <span class="ff-signed-in">${escapeHtml(user.primary)}</span></span></div>"""
    }

    /**
     * Children of the active level-1 item for [NavigationType.APP] top navigation.
     * Active root is the last L1 whose own URL or any descendant matches [path]; otherwise the first L1.
     */
    private fun appTop(path: String, get: Get): List<NavigationItem> {
        if (items.isEmpty()) return emptyList()
        var active: NavigationItem? = null
        for (item in items) {
            if (itemOrDescendantActive(item, path, get)) active = item
        }
        return (active ?: items.first()).children
    }

    private fun itemOrDescendantActive(item: NavigationItem, path: String, get: Get): Boolean {
        if (item.isActive(path, get)) return true
        for (child in item.children) {
            if (itemOrDescendantActive(child, path, get)) return true
        }
        return false
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

    private fun userHtml(request: Request, path: String, user: ChromeUser, get: Get): String {
        val initial = avatarInitial(user.primary)
        val profileLink = """<a class="ff-menu-link" href="${escapeHtml(uri("profile"))}">Profile</a>"""
        val extraLinks = userMenuLinksHtml(path, get)
        val signOut =
            if (user.signedIn) {
                val token = FireflyCsrf.ensure(request)
                """<div class="ff-menu-sep"></div><form class="ff-menu-form" method="post" action="${escapeHtml(uri("logout"))}">${FireflyCsrf.field(token)}<button type="submit" class="ff-menu-item">Sign out</button></form>"""
            } else {
                ""
            }
        val userMenu = """
            <div class="ff-menu ff-user" data-ff-menu>
              <button type="button" class="ff-user-trigger" data-ff-menu-trigger aria-haspopup="true" aria-label="Account menu">
                <span class="ff-avatar" aria-hidden="true">${escapeHtml(initial)}</span>
              </button>
              <div class="ff-menu-panel" data-ff-menu-panel>
                <div class="ff-menu-head"><span class="ff-menu-name">${escapeHtml(user.primary)}</span><span class="ff-menu-sub">${escapeHtml(user.secondary)}</span></div>
                $profileLink$extraLinks$signOut
              </div>
            </div>
        """.trimIndent()
        return """<div class="ff-user-menu">${themeToggleHtml()}${notificationsHtml()}$userMenu</div>"""
    }

    /** Renders [userMenu] entries as dropdown links, keeping their extra attributes. */
    private fun userMenuLinksHtml(path: String, get: Get): String {
        if (userItems.isEmpty()) return ""
        val out = StringBuilder()
        for (item in userItems.sortedBy { it.labelText(get) }) {
            val url = item.urlText(get)
            val href = if (url.isEmpty()) "" else " href=\"${escapeHtml(url)}\""
            val active = if (activeItem(path, url)) " ff-active" else ""
            out.append(
                """<a class="ff-menu-link$active"$href${htmlAttributes(item.attributes(get))}>${escapeHtml(item.labelText(get))}</a>""",
            )
        }
        return out.toString()
    }

    /** First letter/digit of the signed-in identifier, for the avatar. */
    private fun avatarInitial(who: String): String {
        for (ch in who) {
            if (ch.isLetterOrDigit()) return ch.uppercaseChar().toString()
        }
        return "?"
    }

    private fun themeToggleHtml(): String =
        """<button type="button" class="ff-icon-btn ff-theme-toggle" data-ff-theme-toggle data-ff-theme="system" aria-label="Toggle theme" title="Toggle theme">""" +
            SUN_ICON + MOON_ICON + SYSTEM_ICON + "</button>"

    private fun notificationsHtml(): String =
        """<div class="ff-menu ff-notifications" data-ff-menu>""" +
            """<button type="button" class="ff-icon-btn" data-ff-menu-trigger aria-haspopup="true" aria-label="Notifications">$BELL_ICON</button>""" +
            """<div class="ff-menu-panel" data-ff-menu-panel><div class="ff-menu-head"><span class="ff-menu-name">Notifications</span></div>""" +
            """<p class="ff-menu-empty">You're all caught up.</p></div></div>"""

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

    private fun effectiveBranding(request: Request): FireflyBranding {
        val get = SchemaBridge.open(emptyMap())
        // [brand] / [brandUsing] remain the display-name source of truth.
        val name = resolveBrand(get)
        val config = panelBranding.copy(name = name)
        val guard = activeGuard(request)
        val override =
            brandingOverrides?.apply(
                BrandingOverrideContext(
                    panelId = resolvedId,
                    companyId = tenantId(request),
                    userId = guard.id(),
                ),
            )
        return resolveEffectiveBranding(config, override)
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

/**
 * The person the chrome greets. The guard hands us an id and, when the backing user exposes them,
 * a display name and email. Chrome leads with the friendliest label it has — name, then email, then
 * the raw id — so a numeric primary key never becomes the headline. The id stays available as
 * metadata; it is only the fallback label when nothing nicer is known.
 */
private class ChromeUser(id: String?, name: String?, email: String?) {
    val id: String? = id?.takeIf { it.isNotBlank() }
    val name: String? = name?.takeIf { it.isNotBlank() }
    val email: String? = email?.takeIf { it.isNotBlank() }

    /** Signed in when the guard yields an identifier (guests have none). */
    val signedIn: Boolean = this.id != null

    /** Headline label: name, else email, else the raw id, else "Guest". */
    val primary: String = this.name ?: this.email ?: this.id ?: "Guest"

    /** Secondary line: the email when it adds something, otherwise the "Signed in as …" phrase. */
    val secondary: String =
        if (this.email != null && this.email != primary) this.email else "Signed in as $primary"
}

private val SUN_ICON: String =
    Icons.svg("heroicon-o-sun").className("ff-icon ff-theme-icon ff-theme-icon--light").toHtml()

private val MOON_ICON: String =
    Icons.svg("heroicon-o-moon").className("ff-icon ff-theme-icon ff-theme-icon--dark").toHtml()

private val SYSTEM_ICON: String =
    Icons.svg("heroicon-o-computer-desktop").className("ff-icon ff-theme-icon ff-theme-icon--system").toHtml()

private val BELL_ICON: String =
    Icons.svg("heroicon-o-bell").className("ff-icon").toHtml()
