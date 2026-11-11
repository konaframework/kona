package org.konaframework.firefly

import org.konaframework.auth.Auth
import org.konaframework.auth.Authenticatable
import org.konaframework.auth.Guard
import org.konaframework.cadence.Cadence
import org.konaframework.firefly.actions.ActionResult
import org.konaframework.firefly.tables.RecordScopes
import org.konaframework.firefly.forms.Field
import org.konaframework.firefly.forms.FieldValidation
import org.konaframework.firefly.imports.ImportContext
import org.konaframework.firefly.imports.ImportPageModel
import org.konaframework.firefly.imports.ImportPaths
import org.konaframework.firefly.imports.ImportResult
import org.konaframework.firefly.imports.ImportRuntime
import org.konaframework.firefly.imports.Importer
import org.konaframework.firefly.imports.validateImporter
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
import org.konaframework.firefly.support.plainTextFromHtml
import org.konaframework.firefly.support.mergePanelBranding
import org.konaframework.firefly.support.resolveEffectiveBranding
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Text
import org.konaframework.firefly.schemas.htmlAttributes
import org.konaframework.firefly.widgets.Breadcrumb
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
import java.util.function.Predicate
import java.util.function.Supplier
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
 *
 * Prefer [panel] on a subclass when you want a typed chain. Configuration in `init` still
 * applies; [mount] and rendering call [panel] once on top of it.
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
    /**
     * SIDE group chrome. Static is the default (open headings, tree line, no active parent).
     * Accordion collapses groups that do not contain the current page.
     */
    private var sideNavModeKind: SideNavMode = SideNavMode.STATIC
    private var theme: FireflyTheme = KonaTheme()
    private var layout: PanelLayout = DefaultLayout()
    private val hooks = ArrayList<Pair<Hook, String>>()
    private val items = ArrayList<NavigationItem>()
    private val userItems = ArrayList<NavigationItem>()
    private val navigationGroupList = ArrayList<NavigationGroup>()
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
    private var tenancy: TenantConfig? = null
    private val plugins = ArrayList<FireflyPlugin>()
    private val importImporters = ArrayList<Importer>()
    private var importMaxBytes: Int = 2_000_000
    private var mounted: Boolean = false
    private var resolvedPrefix: String = "admin"
    private var resolvedId: String = "admin"
    private var maxWidthLiteral: String? = null
    private var accessUsing: Predicate<Authenticatable?>? = null
    private var allowWithoutPolicy: Boolean? = null
    private var panelConfigured: Boolean = false

    /**
     * Preferred configuration hook (#501). [mount] and chrome rendering call this once with
     * this panel.
     *
     * The default returns [panel] unchanged, so setters called from `init` or the constructor
     * still configure the panel. Override it and return the chain:
     *
     * ```
     * override fun panel(panel: Panel): Panel =
     *     panel.id("admin").path("admin").reuseAppGuard().authenticate(true)
     * ```
     *
     * When the subclass is also named `Panel`, import the framework type
     * (`import org.konaframework.firefly.Panel as FireflyPanel`) so the parameter type is the
     * framework panel and completion lists these setters. Fluent setters return this instance.
     * Returning a different [Panel] throws [FireflyException].
     *
     * Pages, widgets, and resources are not scanned from the classpath. Register them with
     * [page], [pages], [resource], [resources], [widget], [widgets], [plugin], and [plugins],
     * or point [discover] at the panel directory (`pages/`, `widgets/`, `resources/`).
     * [Firefly.mount] discovers panel classes under `app/panels`. There is no panel-wide HTTP
     * middleware stack; tenant requests use [tenantMiddleware]. Sign-in stays on [authenticate],
     * [reuseAppGuard], [useAuthGuard], [guard], and [canAccess].
     */
    open fun panel(panel: Panel): Panel = panel

    private fun applyPanelConfiguration() {
        if (panelConfigured) return
        panelConfigured = true
        val configured = panel(this)
        if (configured !== this) {
            panelConfigured = false
            throw FireflyException("Panel.panel() must return this panel")
        }
    }

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
     * CSS height of the brand logo in the sidebar, top bar, and auth mark.
     * Width stays `auto` unless [brandLogoWidth] is also set, so the SVG keeps its aspect ratio.
     */
    fun brandLogoHeight(value: String): Panel = branding(PanelBranding(logoHeight = value))

    /**
     * CSS width of the brand logo in the sidebar, top bar, and auth mark.
     * Height stays `auto` unless [brandLogoHeight] or [logoHeight] is also set.
     */
    fun brandLogoWidth(value: String): Panel = branding(PanelBranding(logoWidth = value))

    /** Brand primary. Dark mode uses a shade of this color unless [darkPrimary] is set. */
    fun primary(color: String): Panel = branding(PanelBranding(primaryColor = color))

    /** Brand accent. Dark mode uses a shade of this color unless [darkAccent] is set. */
    fun accent(color: String): Panel = branding(PanelBranding(accentColor = color))

    /**
     * Dark-mode primary. Wins over the shade derived from [primary].
     * Leave it unset to follow the panel primary, or the Kona dark primary when the panel sets none.
     */
    fun darkPrimary(color: String): Panel = branding(PanelBranding(darkPrimaryColor = color))

    /** Dark-mode accent. Wins over the shade derived from [accent]. */
    fun darkAccent(color: String): Panel = branding(PanelBranding(darkAccentColor = color))

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

    /**
     * How SIDE groups render. [SideNavMode.STATIC] (the default) keeps headings open and marks the
     * active branch with a tree line, not a highlighted parent. [SideNavMode.ACCORDION] collapses
     * groups that do not contain the current page and may highlight that parent. APP, INVERSE,
     * and TOP ignore the mode. Accordion also sets `data-ff-nav-accordion` so opening one group
     * closes its siblings.
     */
    fun sideNavMode(mode: SideNavMode): Panel = apply { sideNavModeKind = mode }

    fun sideNavMode(): SideNavMode = sideNavModeKind

    /**
     * Selects [SideNavMode.ACCORDION] when [on] is true, otherwise [SideNavMode.STATIC].
     * SIDE only. APP, INVERSE, and TOP ignore it.
     */
    @JvmOverloads
    fun navigationAccordion(on: Boolean = true): Panel =
        sideNavMode(if (on) SideNavMode.ACCORDION else SideNavMode.STATIC)

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

    /**
     * Who may enter this panel (Filament `canAccessPanel`). [predicate] receives the signed-in user.
     * It runs on every panel route except sign-in, registration, password reset, email verification
     * and sign-out. A user model that implements [FireflyUser] is asked as well, and both must allow.
     * A denied user gets 403, and cannot sign in to the panel.
     */
    fun canAccess(predicate: Predicate<Authenticatable?>): Panel = apply { accessUsing = predicate }

    /** Whether [user] may enter this panel: [canAccess] and [FireflyUser.canAccessPanel] both allow. */
    fun permits(user: Authenticatable?): Boolean {
        val predicate = accessUsing
        if (predicate != null && !predicate.test(user)) return false
        if (user is FireflyUser && !user.canAccessPanel(this)) return false
        return true
    }

    /**
     * What resources of this panel answer when their model has no policy: true allows everything,
     * false is strict mode. A resource's own [Resource.authorizeWithoutPolicy] wins.
     */
    fun authorizeWithoutPolicy(on: Boolean): Panel = apply { allowWithoutPolicy = on }

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

    /**
     * Declares sidebar groups, with icon, collapsibility, and [NavigationGroup.navigationSort].
     * Items join a group by name (`Resource.navigationGroup`, `PanelPage.navigationGroup`,
     * `NavigationItem.group`). Top-level links and groups share one list ordered by that sort
     * (unset matches an unset item sort), then by definition order. A group with no visible item
     * is not drawn. Groups left undeclared follow the declared ones.
     */
    fun navigationGroups(vararg groups: NavigationGroup): Panel = apply {
        val seen = HashSet<String>()
        for (group in groups) {
            if (!seen.add(group.label)) throw FireflyException("Navigation group [${group.label}] is declared more than once")
        }
        navigationGroupList.clear()
        navigationGroupList.addAll(groups)
    }

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

    /** Registers each page. Same as calling [page] once per entry. */
    fun pages(vararg pages: PanelPage): Panel = apply {
        for (entry in pages) page(entry)
    }

    fun resource(resource: Resource): Panel = apply { resources.add(resource) }

    /** Registers each resource. Same as calling [resource] once per entry. */
    fun resources(vararg resources: Resource): Panel = apply {
        for (entry in resources) resource(entry)
    }

    fun widget(widget: Widget): Panel = apply { widgets.add(widget) }

    /** Registers each widget on the dashboard. Same as calling [widget] once per entry. */
    fun widgets(vararg widgets: Widget): Panel = apply {
        for (entry in widgets) widget(entry)
    }

    fun discover(panelDirectory: Path): Panel = apply { directory = panelDirectory }

    /** Sets the directory of pages, widgets, and resources when the panel did not call [discover]. */
    internal fun adoptHome(path: Path) {
        if (directory == null) directory = path
    }

    fun tenant(tenant: Tenant): Panel = apply {
        if (tenancy != null) throw FireflyException("Panel [$resolvedId] already uses a tenant model")
        tenantList.add(tenant)
    }

    /**
     * Makes [model] the panel's tenant (Filament multi-tenancy). The signed-in user must implement
     * [HasTenants]; every panel route that serves a tenant checks [HasTenants.canAccessTenant] and
     * answers 403 for a tenant the user does not belong to. The tenant sits in the URL
     * (`/admin/{tenant}/orders`) unless [tenantDomain] puts it in the host.
     *
     * Models related to the tenant through [ownershipRelationship] (a `belongsTo` or `belongsToMany`
     * on the owned model, default the tenant model's lower-case class name) are limited to the
     * current tenant everywhere Firefly reads them (tables, relation managers, select options, record
     * lookups) and new rows are stamped with it. A resource opts out with
     * `scopedToTenant(false)`. [slugAttribute] names the attribute used in the URL (default the key).
     */
    @JvmOverloads
    fun tenant(model: Class<*>, ownershipRelationship: String? = null, slugAttribute: String? = null): Panel = apply {
        if (tenantList.isNotEmpty()) throw FireflyException("Panel [$resolvedId] already uses static tenants")
        val ownership = ownershipRelationship
            ?: model.simpleName.replaceRange(0, 1, model.simpleName.first().lowercaseChar().toString())
        tenancy = TenantConfig(model, ownership, slugAttribute)
    }

    /** Puts the tenant in the host (`{tenant}.example.com`) instead of the path. The panel serves only matching hosts. */
    fun tenantDomain(pattern: String): Panel = apply { tenancyConfig().domain = TenantDomain(pattern) }

    /** Runs [middleware] around every request served for a tenant, in order, once access was granted. */
    fun tenantMiddleware(vararg middleware: TenantMiddleware): Panel = apply { tenancyConfig().middleware.addAll(middleware) }

    /** Shows or hides the tenant menu in the top bar (default shown). */
    fun tenantMenu(on: Boolean): Panel = apply { tenancyConfig().menu = on }

    /** Extra links at the end of the tenant menu. A `{tenant}` in a url stands for the current tenant's slug. */
    fun tenantMenuItems(vararg items: NavigationItem): Panel = apply { tenancyConfig().menuItems.addAll(items) }

    /** How the tenant menu names a tenant. Default: its `name` attribute, else its slug. */
    fun tenantLabel(provider: Function<Any, String>): Panel = apply { tenancyConfig().label(provider) }

    /** Lets signed-in users create a tenant at `/{prefix}/new`, linked from the tenant menu. */
    fun tenantRegistration(page: TenantRegistration): Panel = apply {
        val config = tenancyConfig()
        page.bindModel(config.model)
        config.registration = page
    }

    /** Lets members edit the current tenant at `/{prefix}/{tenant}/tenant-profile`, linked from the tenant menu. */
    fun tenantProfile(page: TenantProfile): Panel = apply { tenancyConfig().profile = page }

    private fun tenancyConfig(): TenantConfig =
        tenancy ?: throw FireflyException("Panel [$resolvedId] has no tenant model; call tenant(Model.class) first")

    fun plugin(plugin: FireflyPlugin): Panel = apply {
        val ready = FireflyPlugin.finish(plugin)
        if (plugins.any { it.id() == ready.id() }) {
            throw FireflyException("Plugin [${ready.id()}] is already registered")
        }
        ready.register(this)
        plugins.add(ready)
    }

    /** Registers each plugin. Same as calling [plugin] once per entry. */
    fun plugins(vararg plugins: FireflyPlugin): Panel = apply {
        for (entry in plugins) plugin(entry)
    }

    /**
     * Mounts `{panel}/import` for these importers and remembers them for [org.konaframework.firefly.imports.ImportAction].
     * The page is not a navigation item. A later call replaces the list. Empty registers nothing.
     * [importMaxBytes] defaults to 2 MB.
     */
    fun imports(vararg importers: Importer): Panel = apply {
        for (importer in importers) validateImporter(importer)
        val seen = HashSet<String>()
        for (importer in importers) {
            if (!seen.add(importer.key)) {
                throw FireflyException("Import [${importer.key}] is already registered")
            }
        }
        importImporters.clear()
        importImporters.addAll(importers)
    }

    /** Largest upload accepted by the import page, in bytes. */
    fun importMaxBytes(bytes: Int): Panel = apply {
        if (bytes < 1) throw FireflyException("Import max bytes must be positive")
        importMaxBytes = bytes
    }

    fun mount(router: Router): Panel = mount(router, emptyMap())

    fun mount(router: Router, state: Map<String, Any?>): Panel {
        applyPanelConfiguration()
        if (mounted) throw FireflyException("Panel [$resolvedId] is already mounted")
        val get = SchemaBridge.open(state)
        resolvedId = resolveId(get)
        resolvedPrefix = normalizePrefix(resolvePath(get, resolvedId))
        resolveBrand(get)
        resolveDomain(get)
        val config = tenancy
        if (config != null && config.domain == null && resolvedPrefix.isEmpty()) {
            throw FireflyException("Panel [$resolvedId] needs a path to put the tenant in the URL; call path(\"admin\") or tenantDomain(...)")
        }
        for (plugin in plugins) plugin.boot(this)
        val discovered = loadDiscovered()
        adopt(discovered)
        val planned = ArrayList<Planned>()
        authRoutes(planned)
        tenantRoutes(planned)
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
            router.match(listOf(route.method), route.uri, secured(route)).name(route.name)
        }
        mounted = true
        return this
    }

    fun render(request: Request): String = draw(request, emptyMap(), "")

    fun render(request: Request, state: Map<String, Any?>): String = draw(request, state, "")

    private fun draw(request: Request, state: Map<String, Any?>, body: String): String {
        applyPanelConfiguration()
        val get = SchemaBridge.open(state)
        val type = resolveNavigation(get)
        val branding = effectiveBranding(request)
        val brand = plainTextFromHtml(branding.brandName)
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
        val path = navPath(request)
        // TOP still emits a sidebar, but only for the narrow-screen drawer. Desktop CSS
        // collapses that aside so the bar stays full width (#502).
        chrome.hook(Hook.TOPBAR_START, topHtml(type, path, branding, get))
        chrome.hook(Hook.TOPBAR_END, tenantHtml(request) + userHtml(request, path, user, get))
        chrome.hook(Hook.SIDEBAR_START, sideHtml(type, path, branding, user, get))
        chrome.hook(Hook.BODY_START, body)
        val toasts = flashToasts(request)
        if (toasts.isNotEmpty()) chrome.hook(Hook.BODY_END, toasts)
        chrome.hook(Hook.FOOTER, footerCopyrightHtml(branding))
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
        applyPanelConfiguration()
        val branding = effectiveBranding(request)
        val brand = plainTextFromHtml(branding.brandName)
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
        for (resource in resources) {
            resource.inheritAuthorizeWithoutPolicy(allowWithoutPolicy)
            resource.discoverPolicy()
            val relationship = resource.tenantOwnershipRelationshipName()
            val config = tenancy
            if (relationship != null && config != null) {
                config.ownerships.name(resource.boundModel(), relationship)
            }
        }
        if (items.isEmpty()) {
            for (page in pages) {
                val isHome = page.slug().isEmpty()
                val node = NavigationItem(page.slug().ifEmpty { "dashboard" })
                    .label(page.navigationLabel())
                    .url(uri(page.slug()))
                    .visible(Predicate { page.shouldRegisterNavigation() && page.canAccess() })
                    .badge(Function { page.navigationBadge() })
                    .dashboard(isHome)
                val pageGroup = page.navigationGroup()
                if (!pageGroup.isNullOrBlank()) node.group(pageGroup)
                val pageSort = page.navigationSort()
                if (pageSort != null) node.sort(pageSort)
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
                    .label(resource.navigationLabel())
                    .url(uri(resourcePagePath(resource.slug(), indexSlug)))
                    .matchPrefix(uri(resource.slug()))
                    .visible(Predicate { resource.shouldRegisterNavigation() && resource.canViewAny() })
                    .badge(Function { resource.navigationBadge() })
                val resourceGroup = resource.navigationGroup()
                if (!resourceGroup.isNullOrBlank()) node.group(resourceGroup)
                val resourceSort = resource.navigationSort()
                if (resourceSort != null) node.sort(resourceSort)
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

    /**
     * Wraps a route: opens the [FireflyAuth] scope for the signed-in user and, unless the route is
     * [Planned.open], answers 403 to a signed-in user [permits] rejects. Guests fall through to the
     * route, which redirects them to sign-in when the panel authenticates. A route that serves a
     * tenant of a model-backed tenancy runs through [tenantRoute].
     */
    private fun secured(route: Planned): Handler = Handler { request ->
        val guard = activeGuard(request)
        val signedIn = guard.check()
        // Prefer the guard; fall back to Auth.user() after setRequest so FireflyAuth is never empty
        // when the session is signed in (nav canViewAny / ResourcePolicy need a non-null user).
        val user = when {
            !signedIn -> null
            else -> guard.user() ?: FireflyPolicies.authUserOrNull()
        }
        FireflyAuth.actingAs(user, Supplier {
            val config = tenancy
            if (!route.open && signedIn && !permits(user)) {
                forbidden("You do not have access to this panel")
            } else if (config != null && route.tenant) {
                tenantRoute(config, route, request, user)
            } else {
                route.handler.handle(request)
            }
        })
    }

    /**
     * Serves [route] for the tenant the request names: 404 for an unknown tenant, 403 unless the
     * user [HasTenants.canAccessTenant]. The tenant is then current ([FireflyTenant.current]), its
     * ownership scope is active for every Cadence store, and the tenant middleware wraps the route.
     */
    private fun tenantRoute(config: TenantConfig, route: Planned, request: Request, user: Authenticatable?): Response {
        if (user == null) {
            return if (authenticatePages) Response.redirect(plainUri("login")) else forbidden("Sign in to open a tenant")
        }
        // Domain mode: a host that does not match yields a null slug (404). Path mode always names a segment.
        val slug = config.slugFrom(request, resolvedPrefix) ?: return Response.html("Tenant not found", 404)
        val tenant = config.find(slug) ?: return Response.html("Tenant not found", 404)
        if (user !is HasTenants || !user.canAccessTenant(tenant)) return forbidden("You do not have access to this tenant")
        request.session()?.put(tenantSessionKey(), tenant.getKey().toString())
        val resource = route.resource
        val unscoped = if (resource == null || resource.isScopedToTenant()) emptySet() else setOf(resource.boundModel())
        val scope = TenantScope(tenant, config.ownerships, unscoped)
        return FireflyTenant.useModel(tenant, Supplier {
            RecordScopes.use(scope, Supplier { config.around(request, tenant, Supplier { route.handler.handle(request) }) })
        })
    }

    private fun tenantSessionKey(): String = "firefly.$resolvedId.tenant"

    /** `GET /{prefix}`: sends the user to the tenant they used last, their default tenant or their first. */
    private fun tenantRoot(config: TenantConfig, request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val guard = activeGuard(request)
        if (!guard.check()) {
            return if (authenticatePages) Response.redirect(plainUri("login")) else forbidden("Sign in to open a tenant")
        }
        val tenant = config.landing(guard.user(), this, request.session(), tenantSessionKey())
        if (tenant != null) return Response.redirect(config.homeUrl(tenant, resolvedPrefix, request))
        val registration = config.registration
        if (registration != null) return Response.redirect(plainUri(registration.slug()))
        return forbidden("You do not belong to any tenant")
    }

    /** `GET` / `POST /{prefix}/new`: the tenant registration page. */
    private fun tenantRegistrationRoute(config: TenantConfig, page: TenantRegistration, request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val guard = activeGuard(request)
        if (!guard.check()) return Response.redirect(plainUri("login"))
        if (!page.canAccess()) return forbidden("This action is unauthorized.")
        if (request.method != "POST") return Response.html(shell(request, page.render(pageState(request, emptyMap()))))
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        val input = postedFields(request)
        val tenant = try {
            page.register(input, guard.user()) as Cadence<*>
        } catch (e: FireflyException) {
            return Response.html(shell(request, page.render(pageState(request, input + mapOf("error" to e.message)))), 422)
        }
        return Response.redirect(config.homeUrl(tenant, resolvedPrefix, request))
    }

    /** `GET` / `POST /{prefix}/{tenant}/tenant-profile`: the current tenant's profile page. */
    private fun tenantProfileRoute(page: TenantProfile, request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (!page.canAccess()) return forbidden("This action is unauthorized.")
        val tenant = FireflyTenant.current(Cadence::class.java)!!
        if (request.method != "POST") return Response.html(shell(request, page.render(pageState(request, page.fill(tenant)))))
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        val input = postedFields(request)
        return try {
            val title = page.save(tenant, input)
            val session = request.session()
            if (session != null) {
                FireflyNotification.make("tenant-profile").title(title).success().send(session)
            }
            Response.redirect(at(page.slug()))
        } catch (e: FireflyException) {
            Response.html(shell(request, page.render(pageState(request, page.fill(tenant) + input + mapOf("error" to e.message)))), 422)
        }
    }

    /** Posted form fields without the CSRF token, hidden `_` fields and query parameters. */
    private fun postedFields(request: Request): Map<String, String> {
        val input = LinkedHashMap<String, String>()
        for ((key, value) in request.all()) {
            if (key == FireflyCsrf.TOKEN_KEY) continue
            if (key.startsWith("_")) continue
            if (key in request.query) continue
            input[key] = value
        }
        return input
    }

    /** The routes of a model-backed tenancy that sit outside a tenant: the root redirect and registration. */
    private fun tenantRoutes(planned: MutableList<Planned>) {
        val config = tenancy ?: return
        if (config.domain == null) {
            planned.add(Planned("GET", plainUri(""), routeName("root")) { request -> tenantRoot(config, request) })
        }
        val page = config.registration
        if (page != null) {
            planned.add(Planned("GET", plainUri(page.slug()), routeName("tenants.new")) { request -> tenantRegistrationRoute(config, page, request) })
            planned.add(Planned("POST", plainUri(page.slug()), routeName("tenants.new.store")) { request -> tenantRegistrationRoute(config, page, request) })
        }
    }

    private fun forbidden(message: String): Response = Response.html(escapeHtml(message), 403)

    private fun authRoutes(planned: MutableList<Planned>) {
        planned.add(Planned("GET", plainUri("login"), routeName("login"), open = true) { request -> loginGet(request) })
        planned.add(Planned("POST", plainUri("login"), routeName("login.store"), open = true) { request -> loginPost(request) })
        planned.add(Planned("GET", plainUri("register"), routeName("register"), open = true) { request -> registerGet(request) })
        planned.add(Planned("POST", plainUri("register"), routeName("register.store"), open = true) { request -> registerPost(request) })
        planned.add(Planned("GET", plainUri("password-reset"), routeName("password"), open = true) { request -> resetGet(request) })
        planned.add(Planned("POST", plainUri("password-reset"), routeName("password.store"), open = true) { request -> resetPost(request) })
        planned.add(Planned("GET", plainUri("email-verify"), routeName("verify"), open = true) { request -> verifyGet(request) })
        planned.add(Planned("POST", plainUri("email-verify"), routeName("verify.store"), open = true) { request -> verifyPost(request) })
        planned.add(Planned("GET", plainUri("profile"), routeName("profile")) { request -> profileGet(request) })
        planned.add(Planned("POST", plainUri("profile"), routeName("profile.store")) { request -> profilePost(request) })
        planned.add(Planned("POST", plainUri("logout"), routeName("logout"), open = true) { request -> logoutPost(request) })
    }

    private fun pageRoutes(planned: MutableList<Planned>) {
        val start = planned.size
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
            val resourceStart = planned.size
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
                                "panelHome" to at(""),
                                "resourceList" to at(listSlug),
                                "resourceBase" to at(resource.slug()),
                            ),
                            resource,
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
                            bulkAction(request, page, mapOf("panelHome" to at(""), "resourceList" to at(listSlug)), resource)
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
            val actionSlug = "${resource.slug()}/${Resource.ACTIONS_PATH}/{action}"
            planned.add(
                Planned("POST", uri(actionSlug), routeName("resources.${resource.slug()}.actions")) { request ->
                    resourceRecordAction(request, resource)
                },
            )
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
            tagTenant(planned, resourceStart, resource)
        }
        val profile = tenancy?.profile
        if (profile != null) {
            planned.add(Planned("GET", uri(profile.slug()), routeName("tenants.profile")) { request ->
                tenantProfileRoute(profile, request)
            }.forTenant(null))
            planned.add(Planned("POST", uri(profile.slug()), routeName("tenants.profile.store")) { request ->
                tenantProfileRoute(profile, request)
            }.forTenant(null))
        }
        if (importImporters.isNotEmpty()) {
            ImportPaths.remember(plainUri("import"))
            planned.add(Planned("GET", uri("import"), routeName("import")) { request -> showImport(request) })
            planned.add(Planned("POST", uri("import"), routeName("import.upload")) { request ->
                postImport(request) { it.upload(request) }
            })
            planned.add(Planned("POST", uri("import/run"), routeName("import.run")) { request ->
                postImport(request) { it.run(request) }
            })
            planned.add(Planned("POST", uri("import/cancel"), routeName("import.cancel")) { request ->
                postImport(request) { it.cancel(request) }
            })
            planned.add(Planned("GET", uri("import/template"), routeName("import.template")) { request ->
                importDenied(request) ?: importResponse(request, runtime(request).template(request))
            })
        }
        tagTenant(planned, start, null)
    }

    /** Marks routes from [start] that are not yet tenant-aware as serving a tenant (and optionally [resource]). */
    private fun tagTenant(planned: MutableList<Planned>, start: Int, resource: Resource?) {
        if (tenancy == null) return
        for (i in start until planned.size) {
            val route = planned[i]
            if (!route.tenant) planned[i] = route.forTenant(resource)
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
        planned.add(
            Planned("POST", uri("$base/${Resource.ACTIONS_PATH}/{action}"), routeName("resources.$base/actions")) { request ->
                relationRecordAction(request, resource)
            },
        )
    }

    private fun relationPost(request: Request, resource: Resource, operation: RelationOperation): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(plainUri("login"))
        }
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        return inTenant(request) {
            val runtime = relationRuntime(request, resource)
            if (runtime !is RelationRuntime) return@inTenant runtime as Response
            val back = relationBack(resource, runtime)
            val input = request.all().filterKeys { it !in request.query && it != FireflyCsrf.TOKEN_KEY }
            if (operation == RelationOperation.BULK) {
                if (runtime.readOnly) return@inTenant Response.html("Relation is read-only here", 403)
                val name = input[BULK_ACTION]
                if (name.isNullOrBlank()) return@inTenant Response.html("Bulk action is required", 422)
                val table = runtime.manager.table(runtime, emptyMap())
                val keys = input[BULK_KEYS].orEmpty().split(',').map { it.trim() }
                val data = input.filterKeys { !it.startsWith("_") }
                val result = try {
                    RelationScope.with(runtime) { table.runBulkAction(name, keys, data, input[BULK_ALL] == "1") }
                } catch (e: FireflyException) {
                    return@inTenant Response.html(escapeHtml(e.message!!), 422)
                }
                flashBulk(request, result)
                return@inTenant Response.redirect(back)
            }
            val outcome = runtime.manager.perform(runtime, operation, input)
            if (outcome.status == 403 || outcome.status == 404) return@inTenant Response.html(escapeHtml(outcome.title), outcome.status)
            var location = back
            if (outcome.createAnother) {
                location += "&dialog=${RelationOperation.CREATE.token}"
            } else if (outcome.reopen != null) {
                location += "&dialog=${URLEncoder.encode(outcome.reopen, Charsets.UTF_8)}"
                val recordId = outcome.recordId
                if (!recordId.isNullOrBlank()) {
                    location += "&dialog_id=${URLEncoder.encode(recordId, Charsets.UTF_8)}"
                }
            }
            val session = request.session()
            if (session != null) {
                val toast = FireflyNotification.make("relation-${operation.token}").title(outcome.title)
                if (outcome.messages.isNotEmpty()) toast.body(outcome.messages.joinToString(" "))
                if (outcome.isSuccessful()) toast.success() else toast.danger()
                toast.send(session)
                if (outcome.fieldErrors.isNotEmpty()) session.flash(RelationManager.ERRORS_FLASH, outcome.fieldErrors)
                if (outcome.oldInput.isNotEmpty()) session.flashInput(outcome.oldInput)
            }
            var response = Response.redirect(location)
            if (outcome.fieldErrors.isNotEmpty()) response = response.withErrors(outcome.fieldErrors)
            if (outcome.oldInput.isNotEmpty()) response = response.withInput(outcome.oldInput)
            response
        }
    }

    /** The edit / view page of the owner with the manager's tab open. */
    private fun relationBack(resource: Resource, runtime: RelationRuntime): String =
        at(resourcePagePath(resource.slug(), runtime.pageSlug)) +
            "?id=${URLEncoder.encode(runtime.ownerKey, Charsets.UTF_8)}" +
            "&relation=${URLEncoder.encode(runtime.manager.name(), Charsets.UTF_8)}"

    /** `POST {resource}/relations/actions/{name}`: a record action of the manager's table on a related record. */
    private fun relationRecordAction(request: Request, resource: Resource): Response = guardedPost(request) {
        inTenant(request) {
            val runtime = relationRuntime(request, resource)
            if (runtime !is RelationRuntime) return@inTenant runtime as Response
            if (runtime.readOnly) return@inTenant Response.html("Relation is read-only here", 403)
            @Suppress("UNCHECKED_CAST")
            val table = runtime.manager.table(runtime, emptyMap()) as org.konaframework.firefly.tables.Table<Any?, Any?>
            val action = table.findRecordAction(actionNameFrom(request)) ?: return@inTenant Response.html("Unknown action", 404)
            val source = RecordActionSource(
                action,
                { key -> RelationScope.with(runtime) { table.findRecord(key) } },
                { record -> table.recordAttributes(record) },
            )
            runRecordAction(request, source, relationBack(resource, runtime))
        }
    }

    /** `POST {resource}/actions/{name}`: an edit / view header action or list-table record action. */
    private fun resourceRecordAction(request: Request, resource: Resource): Response = guardedPost(request) {
        inTenant(request) {
            val source = resource.recordActionSource(actionNameFrom(request)) ?: return@inTenant Response.html("Unknown action", 404)
            runRecordAction(request, source, at(resourceListSlug(resource)))
        }
    }

    /** Panel host, sign-in, and CSRF checks shared by the record action POSTs. */
    private fun guardedPost(request: Request, block: () -> Response): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(plainUri("login"))
        }
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        return block()
    }

    /** The `{action}` segment of a record action route. */
    private fun actionNameFrom(request: Request): String = request.path.trimEnd('/').substringAfterLast('/')

    /**
     * Run a record action on the record posted as `id`: authorize against the record, validate the
     * dialog schema, call the handler with the validated data, flash the success / failure toast,
     * and redirect to the action's redirect URL, the `_return` location, or [fallback].
     */
    private fun runRecordAction(request: Request, source: RecordActionSource, fallback: String): Response {
        val key = request.input("id") ?: request.input("record")
        if (key.isNullOrBlank()) return Response.html("Record id is required", 422)
        val record = source.find(key) ?: return Response.html("Record not found", 404)
        val action = source.action
        val host = Schema.make().operation(action.name).record(record).key(key).fill(source.attributes(record))
        if (action.isHidden(host) || action.isDisabled(host)) return Response.html("Action not available", 403)
        val back = returnLocation(request, fallback)
        val components = action.dialogComponents()
        var data: Map<String, Any?> = emptyMap()
        if (components.isNotEmpty()) {
            val input = request.all().filterKeys { it != FireflyCsrf.TOKEN_KEY && !it.startsWith("_") && it !in request.query }
            val dialog = Schema.make().operation(action.name).record(record).components(*components.toTypedArray()).fill(input)
            val errors = FieldValidation.validate(dialog, dialog.components())
            if (errors.isNotEmpty()) {
                flashNotice(request) { session ->
                    FireflyNotification.make("action-${action.name}")
                        .title("Please fix the form")
                        .body(errors.values.flatten().joinToString(" "))
                        .danger()
                        .send(session)
                }
                return Response.redirect(back).withErrors(errors).withInput(input)
            }
            data = dialogData(dialog)
        }
        val result = action.call(host, data)
        flashActionResult(request, action.name, result)
        return Response.redirect(result.redirectUrl ?: back)
    }

    /** Validated dialog values keyed by field name. */
    private fun dialogData(dialog: Schema): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (component in dialog.walk()) {
            if (component !is Field<*> || component.isHidden(dialog) || !component.isDehydrated()) continue
            val path = component.absolutePath()!!
            out[path] = dialog.get(path)
        }
        return out
    }

    /** Success toast when the action named a title; danger toast for a failure; nothing for a halt. */
    private fun flashActionResult(request: Request, name: String, result: ActionResult) {
        flashNotice(request) { session ->
            when (result.status) {
                ActionResult.SUCCESS -> {
                    val title = result.notificationTitle
                    if (title != null) FireflyNotification.make("action-$name").title(title).success().send(session)
                }
                ActionResult.FAILED -> {
                    val toast = FireflyNotification.make("action-$name").title(result.notificationTitle ?: "Action failed")
                    // A failed result always carries the ActionFailure message.
                    toast.body(result.message!!).danger().send(session)
                }
            }
        }
    }

    /** `_return` when it is a same-site path, else [fallback]. */
    private fun returnLocation(request: Request, fallback: String): String {
        val raw = request.input("_return") ?: return fallback
        val local = raw.startsWith("/") && !raw.startsWith("//") && raw.none { it == '\\' || it.isISOControl() }
        return if (local) raw else fallback
    }

    /** The request path with its query string. */
    private fun locationOf(request: Request): String {
        val query = request.query.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
        }
        return if (query.isEmpty()) request.path else "${request.path}?$query"
    }

    /** Picker options for associate / attach: `[{value, label, enabled}]`, searched by `q`. */
    private fun relationOptions(request: Request, resource: Resource): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(plainUri("login"))
        }
        return inTenant(request) {
            val runtime = relationRuntime(request, resource)
            if (runtime !is RelationRuntime) return@inTenant runtime as Response
            val operation = RelationOperation.fromToken(request.input("op").orEmpty())
            if (operation != RelationOperation.ASSOCIATE && operation != RelationOperation.ATTACH) {
                return@inTenant Response.html("Unknown picker operation", 422)
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
        if (!resource.allowsPage(page, key)) return Response.html("Not authorized", 403)
        val runtime = try {
            manager.bind(owner, page, at(resource.slug() + "/" + Resource.RELATIONS_PATH), "")
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
        resource: Resource? = null,
    ): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(plainUri("login"))
        }
        return inTenant(request) {
            val allowed = if (resource == null) page.canAccess() else resource.allowsPage(page, recordKey(request))
            if (!allowed) return@inTenant forbidden("This action is unauthorized.")
            val body = ImportContext.use(at("import")) {
                page.render(pageState(request, dashboardExtras(extras)))
            }
            Response.html(draw(request, emptyMap(), body))
        }
    }

    private fun showImport(request: Request): Response {
        val denied = importDenied(request)
        if (denied != null) return denied
        return importResponse(request, runtime(request).show(request))
    }

    private fun postImport(request: Request, handle: (ImportRuntime) -> ImportResult): Response {
        val denied = importDenied(request)
        if (denied != null) return denied
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        return importResponse(request, handle(runtime(request)))
    }

    private fun importDenied(request: Request): Response? {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(plainUri("login"))
        }
        return null
    }

    private fun runtime(request: Request): ImportRuntime =
        ImportRuntime(
            importers = importImporters.toList(),
            maxBytes = importMaxBytes,
            pagePath = at("import"),
            csrfField = FireflyCsrf.field(FireflyCsrf.ensure(request)),
        )

    private fun importResponse(request: Request, result: ImportResult): Response = when (result) {
        is ImportResult.Page -> renderImport(request, result.model)
        is ImportResult.Redirect -> Response.redirect(result.location)
        is ImportResult.Download -> Response(200, result.body)
            .header("Content-Type", "text/csv; charset=utf-8")
            .header("Content-Disposition", "attachment; filename=\"${result.filename}\"")
        is ImportResult.Text -> Response.html(result.body, result.status)
    }

    private fun renderImport(request: Request, model: ImportPageModel): Response {
        val page = CustomPage("import")
        page.shouldRegisterNavigation(false)
        page.title(model.title)
        val home = at("")
        if (model.listLabel.isEmpty()) {
            page.breadcrumbs(Breadcrumb.make("Dashboard", home), Breadcrumb.make("Import"))
        } else {
            page.breadcrumbs(
                Breadcrumb.make("Dashboard", home),
                Breadcrumb.make(model.listLabel, model.listPath),
                Breadcrumb.make("Import"),
            )
        }
        page.infolistHtml(model.html)
        return Response.html(draw(request, emptyMap(), page.render(pageState(request, emptyMap()))))
    }

    /** The record a resource page names in `id` / `record`. */
    private fun recordKey(request: Request): String? = request.input("id") ?: request.input("record")

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
            return Response.redirect(plainUri("login"))
        }
        if (widgetId.isNullOrBlank()) return Response.html("Widget id is required", 422)
        val decoded = java.net.URLDecoder.decode(widgetId, Charsets.UTF_8)
        val home = pages.firstOrNull { it.slug().isEmpty() }
            ?: return Response.html("Dashboard not found", 404)
        val widget = home.findWidget(decoded)
            ?: return Response.html("Widget not found", 404)
        return inTenant(request) {
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
        out["widgetRefreshBase"] = at("").trimEnd('/')
        out["navigationCards"] = navigationCards()
        out.putAll(extras)
        return out
    }

    private fun navigationCards(): List<org.konaframework.firefly.widgets.NavigationCard> {
        val get = SchemaBridge.open(emptyMap())
        return items.filter { it.isVisible(get) }.map { item ->
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
    private fun bulkAction(
        request: Request,
        page: PanelPage,
        extras: Map<String, Any?> = emptyMap(),
        resource: Resource? = null,
    ): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(plainUri("login"))
        }
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        val name = request.input(BULK_ACTION)
        if (name.isNullOrBlank()) {
            if (page.mountedForm() != null) return pageFormSubmit(request, page)
            return Response.html("Bulk action is required", 422)
        }
        return inTenant(request) {
            if (resource != null && !resource.allowsBulk(name)) return@inTenant forbidden("This action is unauthorized.")
            page.prepare(pageState(request, extras))
            val table = page.mountedTable() ?: return@inTenant Response.html("Page has no table", 404)
            val keys = request.input(BULK_KEYS).orEmpty().split(',').map { it.trim() }
            val data = request.all().filterKeys { !it.startsWith("_") && it !in request.query }
            val result = try {
                table.runBulkAction(name, keys, data, request.input(BULK_ALL) == "1")
            } catch (e: FireflyException) {
                return@inTenant Response.html(escapeHtml(e.message!!), 422)
            }
            flashBulk(request, result)
            Response.redirect(locationOf(request))
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
        val title = inTenant(request) { page.submitForm(input) }
        val session = request.session()
        if (session != null) FireflyNotification.make("page-form").title(title).success().send(session)
        return Response.redirect(request.path)
    }

    /** JSON options for a custom page's relationship / searchable Select (`{page}/form-options`). */
    private fun pageFormOptions(request: Request, page: PanelPage): Response {
        val denied = gate(request)
        if (denied != null) return denied
        if (authenticatePages && !activeGuard(request).check()) {
            return Response.redirect(plainUri("login"))
        }
        val form = page.mountedForm() ?: return Response.json(emptyList<Any>())
        val select = form.component(request.input("field").orEmpty()) as? org.konaframework.firefly.forms.Select
            ?: return Response.json(emptyList<Any>())
        val options = inTenant(request) { select.search(form, request.input("q").orEmpty()) }
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
        state["panelHome"] = at("")
        state["returnUrl"] = locationOf(request)
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
        val dialog = request.input("dialog")
        if (!dialog.isNullOrBlank()) state["dialog"] = dialog
        val dialogId = request.input("dialog_id")
        if (!dialogId.isNullOrBlank()) state["dialogId"] = dialogId
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
            return Response.redirect(plainUri("login"))
        }
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        return inTenant(request) {
            val key = request.input("id") ?: request.input("record")
            if (key.isNullOrBlank()) {
                return@inTenant Response.html("Record id is required", 422)
            }
            @Suppress("UNCHECKED_CAST")
            val store = org.konaframework.firefly.tables.RecordStores.open(resource.boundModel())
                as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
            val found = store.find(key) ?: return@inTenant Response.html("Record not found", 404)
            if (!resource.canDelete(found)) return@inTenant forbidden("This action is unauthorized.")
            store.delete(found)
            flashNotice(request, FireflyToasts::deleted)
            Response.redirect(at(resourceListSlug(resource)))
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
            return Response.redirect(plainUri("login"))
        }
        val csrfError = FireflyCsrf.rejectIfInvalid(request)
        if (csrfError != null) return csrfError
        return inTenant(request) {
            @Suppress("UNCHECKED_CAST")
            val store = org.konaframework.firefly.tables.RecordStores.open(resource.boundModel())
                as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
            if (page !is EditPage && !resource.canCreate()) return@inTenant forbidden("This action is unauthorized.")
            val attrs = LinkedHashMap<String, Any?>()
            for ((key, value) in request.all()) {
                if (key == FireflyCsrf.TOKEN_KEY || key == "id" || key.startsWith("_")) continue
                // Action modal fields used to nest under create./edit. — map to column names.
                attrs[storeAttributeKey(key)] = value
            }
            val record = if (page is EditPage) {
                val key = request.input("id") ?: request.input("record")
                if (key.isNullOrBlank()) {
                    return@inTenant Response.html("Record id is required", 422)
                }
                val found = store.find(key)
                if (found == null) return@inTenant Response.html("Record not found", 404)
                if (!resource.canEdit(found)) return@inTenant forbidden("This action is unauthorized.")
                found
            } else {
                store.newRecord()
            }
            val submission = resource.submit(
                if (page is EditPage) "edit" else "create",
                record,
                if (page is EditPage) (request.input("id") ?: request.input("record")) else null,
                attrs,
            )
            if (submission.errors.isNotEmpty()) {
                return@inTenant invalidForm(request, resource, page, submission)
            }
            store.fill(record, submission.attributes)
            store.save(record)
            if (page is EditPage) {
                flashNotice(request, FireflyToasts::saved)
            } else {
                flashNotice(request, FireflyToasts::created)
            }
            Response.redirect(at(resourceListSlug(resource)))
        }
    }

    /**
     * A rejected create / edit post: flash a toast, the field errors and the old input, then send the
     * user back to the form page (or the list when the resource only has manage dialogs).
     */
    private fun invalidForm(request: Request, resource: Resource, page: PanelPage, submission: FormSubmission): Response {
        val edit = page is EditPage
        val real = resource.pages().any { if (edit) it is EditPage else it is CreatePage }
        val errors = submission.errors
        val old = submission.oldInput(request.all().filterKeys { !it.startsWith("_") })
        flashNotice(request) { session ->
            FireflyNotification.make("form-invalid")
                .title("Please fix the form")
                .body(errors.values.flatten().joinToString(" "))
                .danger()
                .send(session)
            if (real) {
                session.flash(FormSubmission.ERRORS_FLASH, errors)
                session.flashInput(old)
            }
        }
        if (!real) return Response.redirect(at(resourceListSlug(resource))).withErrors(errors).withInput(old)
        var location = at(resourcePagePath(resource.slug(), page.slug()))
        if (edit) {
            val id = request.input("id") ?: request.input("record")
            location += "?id=${URLEncoder.encode(id, Charsets.UTF_8)}"
        }
        return Response.redirect(location).withErrors(errors).withInput(old)
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
        val guard = activeGuard(request)
        // A signed-in user the panel rejects stays on the sign-in form to switch accounts.
        if (guard.check() && permits(guard.user())) return Response.redirect(plainUri(""))
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
        val guard = activeGuard(request)
        if (!guard.attempt(email, password)) {
            return Response.html(authShell(request, "Sign in", authForm(request, AuthScreen.LOGIN, "Those credentials do not match")), 422)
        }
        if (!permits(guard.user())) {
            guard.logout()
            return Response.html(authShell(request, "Sign in", authForm(request, AuthScreen.LOGIN, "You do not have access to this panel")), 403)
        }
        return Response.redirect(plainUri(""))
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
        return Response.redirect(plainUri("login"))
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
        if (!guard.check()) return Response.redirect(plainUri("login"))
        val page = resolveProfilePage()
        val state = pageState(request, page.fill(guard))
        return Response.html(shell(request, page.render(state)))
    }

    private fun profilePost(request: Request): Response {
        val denied = gate(request)
        if (denied != null) return denied
        val guard = activeGuard(request)
        if (!guard.check()) return Response.redirect(plainUri("login"))
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
            Response.redirect(plainUri("profile"))
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
        return Response.redirect(plainUri("login"))
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
    internal fun allowsUpload(request: Request): Boolean {
        if (gate(request) != null) return false
        val guard = activeGuard(request)
        return guard.check() && permits(guard.user())
    }

    private fun gate(request: Request): Response? {
        val tenantDomain = tenancy?.domain
        if (tenantDomain != null && tenantDomain.slug(request.header("Host")) == null) {
            return Response.html("Panel [$resolvedId] expected a tenant host matching [${tenantDomain.pattern}]", 404)
        }
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
              <form class="ff-auth-form" method="post" action="${escapeHtml(plainUri(screen.action))}">
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
        // Every layout can open the drawer. The control is hidden on wide screens.
        // TOP keeps its brand in the bar at every width; the others show it there only on mobile.
        lead.append(
            """<button type="button" class="ff-hamburger" data-ff-sidebar-toggle aria-label="Open navigation">${Icon.BARS.svg()}</button>""",
        )
        if (type == NavigationType.TOP) {
            lead.append(brandMarkHtml(branding))
        } else {
            lead.append(brandMarkHtml(branding, "ff-topbar-brand"))
        }
        // APP: children of the active L1 root (nested links are dropdowns).
        // INVERSE: L1 roots only (children stay in the sidebar).
        // TOP: groups are dropdown triggers; their links are not a second row.
        when (type) {
            NavigationType.TOP ->
                lead.append("""<nav class="ff-nav ff-nav-top">${renderGrouped(items, navigationGroupList, path, spaMode, get, false, true)}</nav>""")
            NavigationType.APP -> {
                val nested = activeLevelChildren(path, get)
                if (nested.isNotEmpty()) {
                    lead.append(
                        """<nav class="ff-nav ff-nav-top">${renderItems(nested, path, spaMode, get, true, true)}</nav>""",
                    )
                }
            }
            NavigationType.INVERSE -> {
                val roots = appLevelOnes(items, navigationGroupList, get)
                if (roots.isNotEmpty()) {
                    lead.append(
                        """<nav class="ff-nav ff-nav-top">${renderItems(roots, path, spaMode, get, false)}</nav>""",
                    )
                }
            }
            NavigationType.SIDE -> Unit
        }
        lead.append("</div>")
        return lead.toString()
    }

    private fun sideHtml(type: NavigationType, path: String, branding: FireflyBranding, user: ChromeUser, get: Get): String {
        // Brand block is the elevated top strip; it carries the drawer close button on mobile.
        val logoClass = if (branding.showLogo) " ff-sidebar-brand--logo" else ""
        val brandBlock =
            """<div class="ff-sidebar-brand$logoClass">${brandMarkHtml(branding)}""" +
                """<button type="button" class="ff-sidebar-close" data-ff-sidebar-close aria-label="Close navigation">${Icon.X.svg()}</button></div>"""
        // Marker so desktop CSS can drop the TOP column without removing the mobile drawer.
        val offcanvas = if (type == NavigationType.TOP) """<div class="ff-sidebar-offcanvas" hidden></div>""" else ""
        // APP: L1 roots only. INVERSE: children of the active L1 root. SIDE: full tree.
        // TOP has no desktop sidebar nav. Accordion on the desktop tree is SIDE-only.
        val nav = when (type) {
            NavigationType.TOP -> ""
            NavigationType.APP -> {
                val roots = appLevelOnes(items, navigationGroupList, get)
                """<nav class="ff-nav ff-nav-side">${renderItems(roots, path, spaMode, get, false)}</nav>"""
            }
            NavigationType.INVERSE -> {
                val nested = activeLevelChildren(path, get)
                if (nested.isEmpty()) {
                    ""
                } else {
                    """<nav class="ff-nav ff-nav-side">${renderItems(nested, path, spaMode, get, true)}</nav>"""
                }
            }
            NavigationType.SIDE -> {
                val accordion = if (sideNavModeKind == SideNavMode.ACCORDION) " data-ff-nav-accordion" else ""
                """<nav class="ff-nav ff-nav-side"$accordion>${renderGrouped(items, navigationGroupList, path, spaMode, get, true, false, sideMode = sideNavModeKind)}</nav>"""
            }
        }
        return offcanvas + brandBlock + nav + drawerNav(path, get) + sidebarFootHtml(user)
    }

    /**
     * Narrow-screen drawer: the full tree, one collapsible group at a time.
     * Hidden on wide screens. Desktop SIDE/APP/INVERSE nav above it is unchanged.
     */
    private fun drawerNav(path: String, get: Get): String {
        val tree = renderGrouped(items, navigationGroupList, path, spaMode, get, true, false, true)
        if (tree.isBlank()) return ""
        return """<nav class="ff-nav ff-nav-drawer" data-ff-nav-accordion>$tree</nav>"""
    }

    /** Sidebar footer with the "Signed in as …" line (Shamar sidebar user block). */
    private fun sidebarFootHtml(user: ChromeUser): String =
        """<div class="ff-sidebar-foot"><span>Signed in as <span class="ff-signed-in">${escapeHtml(user.primary)}</span></span></div>"""

    /**
     * Copyright lives in the main-column footer, centered, so the sidebar foot
     * stays the signed-in line only. Blank copyright adds nothing.
     * [FireflyBranding.copyrightText] is developer HTML and is not escaped.
     */
    private fun footerCopyrightHtml(branding: FireflyBranding): String {
        val copyrightText = branding.copyrightText
        if (copyrightText.isNullOrBlank()) return ""
        return """<p class="ff-footer-copyright">$copyrightText</p>"""
    }

    /**
     * Children of the active level-1 item.
     * [NavigationType.APP] draws them in the top bar; [NavigationType.INVERSE] draws them in the sidebar.
     * Active root is the last L1 whose own URL or any descendant matches [path]; otherwise the first L1.
     * Grouped resources are promoted to cluster roots via [appLevelOnes].
     */
    private fun activeLevelChildren(path: String, get: Get): List<NavigationItem> {
        val shown = appLevelOnes(items, navigationGroupList, get).filter { it.isVisible(get) }
        if (shown.isEmpty()) return emptyList()
        var active: NavigationItem? = null
        for (item in shown) {
            if (itemOrDescendantActive(item, path, get)) active = item
        }
        return (active ?: shown.first()).children
    }

    private fun tenantId(request: Request): String? {
        val current = FireflyTenant.id()
        if (current != null) return current
        if (tenantList.isEmpty()) return null
        val raw = request.query["tenant"]
        if (raw == null || raw.isBlank()) return null
        if (tenantList.none { it.id == raw }) throw FireflyException("Unknown tenant [$raw]")
        return raw
    }

    private fun tenantHtml(request: Request): String {
        val config = tenancy
        if (config != null) {
            if (!config.menu) return ""
            val user = activeGuard(request).user()
            if (user !is HasTenants) {
                return ""
            }
            val current = FireflyTenant.current(Cadence::class.java)
            return config.menuHtml(user, this, current, request, resolvedPrefix, { bindTenant(it, current) }, SchemaBridge.open(emptyMap()))
        }
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

    /**
     * Runs [block] with the request's tenant: model-backed tenancy already opened the scope in
     * [tenantRoute], so this is a no-op there; static tenants set [FireflyTenant.id] from `?tenant=`.
     */
    private fun <R> inTenant(request: Request, block: () -> R): R {
        if (tenancy != null) return block()
        return FireflyTenant.use(tenantId(request), java.util.function.Supplier { block() })
    }

    /** Request path with the current tenant slug replaced by `{tenant}` so nav urls match. */
    private fun navPath(request: Request): String {
        val config = tenancy
        val tenant = FireflyTenant.current(Cadence::class.java)
        if (config == null || config.domain != null || tenant == null) return request.path
        val slug = TenantConfig.encode(config.slugOf(tenant))
        return request.path.replace("/$slug", "/$TENANT_TOKEN")
    }

    private fun userHtml(request: Request, path: String, user: ChromeUser, get: Get): String {
        val initial = avatarInitial(user.primary)
        val profileLink = """<a class="ff-menu-link" href="${escapeHtml(plainUri("profile"))}">Profile</a>"""
        val extraLinks = userMenuLinksHtml(path, get)
        val signOut =
            if (user.signedIn) {
                val token = FireflyCsrf.ensure(request)
                """<div class="ff-menu-sep"></div><form class="ff-menu-form" method="post" action="${escapeHtml(plainUri("logout"))}">${FireflyCsrf.field(token)}<button type="submit" class="ff-menu-item">Sign out</button></form>"""
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

    /**
     * The route of a tenant page: with a tenant in the path it is `/{prefix}/{tenant}/{slug}`, where
     * `{tenant}` is a route parameter and the token navigation urls carry until a request fills it in.
     */
    private fun uri(slug: String): String {
        val config = tenancy
        if (config == null || config.domain != null) return panelUri(resolvedPrefix, slug)
        return panelUri("$resolvedPrefix/$TENANT_TOKEN", slug)
    }

    /** The URL of a page outside any tenant (sign-in, profile, sign-out, tenant registration, root). */
    private fun plainUri(slug: String): String = panelUri(resolvedPrefix, slug)

    /** [uri] for the tenant of the request on this thread; the same as [uri] without one. */
    private fun at(slug: String): String = bindTenant(uri(slug), FireflyTenant.current() as Cadence<*>?)

    /** Replaces the `{tenant}` token in [url] with the slug of [tenant]. */
    private fun bindTenant(url: String, tenant: Cadence<*>?): String {
        val config = tenancy
        if (config == null || tenant == null) return url
        return url.replace(TENANT_TOKEN, TenantConfig.encode(config.slugOf(tenant)))
    }

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

    /** A route to mount. [open] routes (sign-in, registration, reset, verify, sign-out) skip [canAccess]. */
    private class Planned(
        val method: String,
        val uri: String,
        val name: String,
        val open: Boolean = false,
        val tenant: Boolean = false,
        val resource: Resource? = null,
        val handler: Handler,
    ) {
        /** The same route, served for a tenant. [resource] names the resource it belongs to, when any. */
        fun forTenant(resource: Resource?): Planned = Planned(method, uri, name, open, true, resource, handler)
    }

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
