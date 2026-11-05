package org.konaframework.firefly

import org.konaframework.auth.Authenticatable
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ModelBuilder
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.htmlAttributes
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordScopes
import org.konaframework.firefly.tables.RecordStore
import org.konaframework.firefly.tables.RecordStores
import org.konaframework.http.Request
import org.konaframework.http.RequestSession
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.function.Function
import java.util.function.Supplier

/** `{tenant}` in a route, a navigation url or a tenant domain stands for the slug of the current tenant. */
internal const val TENANT_TOKEN = "{tenant}"

/** A tenant domain such as `{tenant}.example.com`: the host carries the tenant slug. */
internal class TenantDomain(val pattern: String) {
    private val parts = pattern.split(TENANT_TOKEN)
    private val regex = Regex(parts.joinToString("([^.]+)") { Regex.escape(it) })

    init {
        if (parts.size != 2) throw FireflyException("Tenant domain [$pattern] must contain $TENANT_TOKEN once")
    }

    /** The tenant slug in [host] (a port is ignored), or null when [host] does not match the pattern. */
    fun slug(host: String?): String? {
        if (host == null) return null
        val match = regex.matchEntire(host.substringBefore(':')) ?: return null
        return match.groupValues[1]
    }

    fun host(slug: String): String = pattern.replace(TENANT_TOKEN, slug)
}

/**
 * A panel's model-backed tenancy ([Panel.tenant] with a model class): which model is the tenant, how
 * its slug is read, how other models belong to it, and the menu, pages and middleware around it.
 */
internal class TenantConfig(val model: Class<*>, ownership: String, private val slugAttribute: String?) {
    val ownerships = Ownerships(model, ownership)
    var domain: TenantDomain? = null
    var menu: Boolean = true
    val menuItems = ArrayList<NavigationItem>()
    val middleware = ArrayList<TenantMiddleware>()
    var registration: TenantRegistration? = null
    var profile: TenantProfile? = null
    private var labelUsing: Function<Any, String>? = null

    init {
        if (!Cadence::class.java.isAssignableFrom(model)) {
            throw FireflyException("Tenant model [${model.name}] must be a Cadence model")
        }
        if (ownership.isBlank()) throw FireflyException("Tenant ownership relationship is blank")
        if (slugAttribute != null && slugAttribute.isBlank()) throw FireflyException("Tenant slug attribute is blank")
    }

    fun label(provider: Function<Any, String>) {
        labelUsing = provider
    }

    /** Display name: the label provider, else the `name` attribute, else the slug. */
    fun labelOf(tenant: Cadence<*>): String {
        val using = labelUsing
        if (using != null) return using.apply(tenant)
        val name = tenant.getAttribute("name")
        if (name != null) return name.toString()
        return slugOf(tenant)
    }

    /** The URL segment (or subdomain) of [tenant]: its slug attribute, else its key. */
    fun slugOf(tenant: Cadence<*>): String {
        val attribute = slugAttribute
        if (attribute != null) {
            val value = tenant.getAttribute(attribute)
            if (value != null) return value.toString()
        }
        return tenant.getKey().toString()
    }

    /** The tenant with [slug], or null. Never scoped. Looks up the slug attribute when set, else the key. */
    fun find(slug: String): Cadence<*>? = RecordScopes.use(null, Supplier {
        val store = store()
        val attribute = slugAttribute
        if (attribute == null) {
            store.find(slug)
        } else {
            (store.query() as ModelBuilder<*>).where(attribute, slug).first() ?: store.find(slug)
        }
    }) as Cadence<*>?

    /** The tenant with key [key], or null. Never scoped. */
    fun findKey(key: String): Cadence<*>? = RecordScopes.use(null, Supplier { store().find(key) }) as Cadence<*>?

    private fun store(): RecordStore<Any?, Any?> {
        @Suppress("UNCHECKED_CAST")
        return RecordStores.open(model) as RecordStore<Any?, Any?>
    }

    /** The tenant slug the request names: from the host in domain mode, else from the path after [base]. */
    fun slugFrom(request: Request, base: String): String? {
        val tenantDomain = domain
        if (tenantDomain != null) return tenantDomain.slug(request.header("Host"))
        return URLDecoder.decode(request.path.removePrefix("/$base/").substringBefore('/'), Charsets.UTF_8)
    }

    /**
     * Where a user lands without naming a tenant: the tenant used last in this session while still
     * allowed, else the user's default tenant, else the first of their tenants.
     */
    fun landing(user: Authenticatable?, panel: Panel, session: RequestSession?, sessionKey: String): Cadence<*>? {
        if (user !is HasTenants) return null
        val key = if (session == null) null else session.get(sessionKey)?.toString()
        if (key != null) {
            val remembered = findKey(key)
            if (remembered != null && user.canAccessTenant(remembered)) return remembered
        }
        if (user is HasDefaultTenant) {
            val preferred = user.getDefaultTenant(panel) as Cadence<*>?
            if (preferred != null) return preferred
        }
        val tenants = user.getTenants(panel)
        return if (tenants.isEmpty()) null else tenants.first() as Cadence<*>
    }

    /** Runs [block] through the tenant middleware, outermost first. */
    fun around(request: Request, tenant: Cadence<*>, block: Supplier<org.konaframework.http.Response>): org.konaframework.http.Response {
        var next = block
        for (layer in middleware.asReversed()) {
            val inner = next
            next = Supplier { layer.handle(request, tenant, inner) }
        }
        return next.get()
    }

    /** The URL of [tenant]'s home: its path under [base], or its host in domain mode. */
    fun homeUrl(tenant: Cadence<*>, base: String, request: Request): String {
        val slug = slugOf(tenant)
        val tenantDomain = domain
        if (tenantDomain == null) return panelUri("$base/${encode(slug)}", "")
        val hostHeader = request.header("Host") ?: ""
        val port = if (':' in hostHeader) hostHeader.substringAfter(':') else ""
        val forwarded = request.header("X-Forwarded-Proto")
        val scheme = if (forwarded == null) "http" else forwarded
        val host = if (port.isEmpty()) tenantDomain.host(slug) else tenantDomain.host(slug) + ":" + port
        return "$scheme://$host" + panelUri(base, "")
    }

    /**
     * The tenant menu: the current tenant as the trigger, a link for every tenant the user belongs
     * to, then the tenant profile, tenant registration and custom menu links.
     */
    fun menuHtml(
        user: HasTenants,
        panel: Panel,
        current: Cadence<*>?,
        request: Request,
        base: String,
        bind: (String) -> String,
        get: Get,
    ): String {
        val out = StringBuilder()
        for (entry in user.getTenants(panel)) {
            val tenant = entry as Cadence<*>
            val active = if (current != null && slugOf(tenant) == slugOf(current)) " ff-active" else ""
            out.append("""<a class="ff-menu-link$active" href="${escapeHtml(homeUrl(tenant, base, request))}">${escapeHtml(labelOf(tenant))}</a>""")
        }
        val extra = StringBuilder()
        val tenantProfile = profile
        if (tenantProfile != null && current != null) {
            val url = bind(panelUri("$base/$TENANT_TOKEN", tenantProfile.slug()))
            extra.append("""<a class="ff-menu-link" href="${escapeHtml(url)}">${escapeHtml(tenantProfile.navigationLabel())}</a>""")
        }
        val tenantRegistration = registration
        if (tenantRegistration != null) {
            val url = panelUri(base, tenantRegistration.slug())
            extra.append("""<a class="ff-menu-link" href="${escapeHtml(url)}">${escapeHtml(tenantRegistration.navigationLabel())}</a>""")
        }
        for (item in menuItems) {
            extra.append(
                """<a class="ff-menu-link" href="${escapeHtml(bind(item.urlText(get)))}"${htmlAttributes(item.attributes(get))}>${escapeHtml(item.labelText(get))}</a>""",
            )
        }
        val title = if (current == null) "Select tenant" else labelOf(current)
        val separator = if (extra.isEmpty()) "" else """<div class="ff-menu-sep"></div>"""
        return """<div class="ff-menu ff-tenant-menu" data-ff-menu>""" +
            """<button type="button" class="ff-tenant-trigger" data-ff-menu-trigger aria-haspopup="true" aria-label="Switch tenant">${escapeHtml(title)}</button>""" +
            """<div class="ff-menu-panel" data-ff-menu-panel>$out$separator$extra</div></div>"""
    }

    companion object {
        fun encode(slug: String): String = URLEncoder.encode(slug, Charsets.UTF_8).replace("+", "%20")
    }
}
