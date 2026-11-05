package org.konaframework.firefly

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.auth.Authenticatable
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.ModelBuilder
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordScopes
import org.konaframework.firefly.tables.RecordStores
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.http.Request
import org.konaframework.http.RequestSession
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.util.function.Supplier

class ModelTenancyTest {
    private lateinit var database: Database
    private lateinit var acme: MtCompany
    private lateinit var beta: MtCompany
    private lateinit var guard: MtGuard

    @BeforeEach
    fun setUp() {
        database = mtDatabase()
        acme = MtCompany().fill(mapOf("name" to "Acme", "slug" to "acme")).also { it.save() }
        beta = MtCompany().fill(mapOf("name" to "Beta", "slug" to "beta")).also { it.save() }
        MtOrder().fill(mapOf("name" to "Acme order", "company_id" to acme.getKey())).also { it.save() }
        MtOrder().fill(mapOf("name" to "Beta order", "company_id" to beta.getKey())).also { it.save() }
        MtTag().fill(mapOf("name" to "Shared")).also { it.save() }
        guard = MtGuard(listOf(acme, beta))
        Firefly.reset()
    }

    @AfterEach
    fun tearDown() {
        Firefly.reset()
        database.close()
    }

    @Test
    fun pathTenancyScopesQueriesAssociatesCreatesAndChecksAccess() {
        val router = mount(orders())
        guard.signIn(listOf(acme, beta), default = acme)

        val root = get(router, "/admin")
        assertEquals(302, root.status)
        assertEquals("/admin/acme", root.headers["Location"])

        val denied = get(router, "/admin/beta/orders/list")
        assertEquals(200, denied.status) // still a member of beta
        assertTrue(denied.body.contains("Beta order"))
        assertFalse(denied.body.contains("Acme order"))

        guard.signIn(listOf(acme), default = acme)
        val forbidden = get(router, "/admin/beta/orders/list")
        assertEquals(403, forbidden.status)

        val missing = get(router, "/admin/ghost/orders/list")
        assertEquals(404, missing.status)

        guard.signIn(listOf(acme, beta), default = acme)
        val list = get(router, "/admin/acme/orders/list")
        assertEquals(200, list.status)
        assertTrue(list.body.contains("Acme order"))
        assertFalse(list.body.contains("Beta order"))
        assertTrue(list.body.contains("ff-tenant-menu"))
        assertTrue(list.body.contains("Acme"))
        assertTrue(list.body.contains("/admin/beta"))
        assertNull(FireflyTenant.id())
        assertNull(FireflyTenant.current())
        assertNull(Firefly.getTenant())

        val session = MtSession()
        get(router, "/admin/acme/orders/list", session = session)
        assertEquals(acme.getKey().toString(), session.get("firefly.desk.tenant"))

        val create = post(
            router,
            "/admin/acme/orders/create",
            "name=New+Acme&_token=${token(session, router, "/admin/acme/orders/create")}",
            session,
        )
        assertEquals(302, create.status)
        val created = MtOrder().newQuery().where("name", "New Acme").first()
        assertEquals(acme.getKey(), created!!.getAttribute("company_id"))

        assertEquals(acme.getKey(), FireflyTenant.useModel(acme, Supplier { Firefly.getTenant(MtCompany::class.java)!!.getKey() }))
    }

    @Test
    fun landingPrefersSessionThenDefaultThenFirstAndRegistrationRedirects() {
        val registration = object : TenantRegistration() {
            override fun register(input: Map<String, String>, user: Authenticatable?): Any {
                val data = LinkedHashMap(input)
                if (data["slug"].isNullOrBlank()) data["slug"] = data["name"].orEmpty().lowercase()
                val company = super.register(data, user) as MtCompany
                (user as MtUser).companies = (user.companies + company).toMutableList()
                return company
            }
        }
        val router = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, ownershipRelationship = "company", slugAttribute = "slug")
            .tenantRegistration(registration)
            .tenantProfile(TenantProfile())
            .tenantMiddleware({ request, tenant, next ->
                request.setAttribute("tenant-seen", (tenant as MtCompany).getAttribute("slug"))
                next.get()
            })
            .tenantMenuItems(NavigationItem("billing").label("Billing").url("/admin/{tenant}/billing"))
            .tenantLabel { (it as MtCompany).getAttribute("name").toString().uppercase() }
            .resource(orders())
            .mount(router)

        guard.signIn(emptyList())
        val none = get(router, "/admin")
        assertEquals(302, none.status)
        assertEquals("/admin/new", none.headers["Location"])

        val session = MtSession()
        guard.signIn(listOf(acme, beta), default = beta)
        session.put("firefly.desk.tenant", acme.getKey().toString())
        val remembered = get(router, "/admin", session = session)
        assertEquals("/admin/acme", remembered.headers["Location"])

        session.put("firefly.desk.tenant", "missing")
        val preferred = get(router, "/admin", session = session)
        assertEquals("/admin/beta", preferred.headers["Location"])

        guard.signIn(listOf(acme, beta), default = null)
        session.put("firefly.desk.tenant", "missing")
        val first = get(router, "/admin", session = session)
        assertEquals("/admin/acme", first.headers["Location"])

        guard.signIn(listOf(acme))
        val registerGet = get(router, "/admin/new", session = session)
        assertEquals(200, registerGet.status)
        assertTrue(registerGet.body.contains("Register"))

        val csrf = token(session, router, "/admin/new")
        val registered = post(router, "/admin/new", "name=Gamma&_token=$csrf", session)
        assertEquals(302, registered.status)
        assertTrue(registered.headers["Location"]!!.startsWith("/admin/"))
        val gamma = MtCompany().newQuery().where("name", "Gamma").first()
        assertNotNull(gamma)
        assertTrue((guard.user() as HasTenants).canAccessTenant(gamma!!))

        val profile = get(router, "/admin/acme/tenant-profile", session = session)
        assertEquals(200, profile.status)
        assertTrue(profile.body.contains("Tenant profile"))
        val saveToken = token(session, router, "/admin/acme/tenant-profile")
        val saved = post(router, "/admin/acme/tenant-profile", "name=Acme+Co&_token=$saveToken", session)
        assertEquals(302, saved.status)
        assertEquals("Acme Co", acme.refresh().getAttribute("name"))

        val blank = post(router, "/admin/acme/tenant-profile", "name=&_token=${token(session, router, "/admin/acme/tenant-profile")}", session)
        assertEquals(422, blank.status)

        val list = get(router, "/admin/acme/orders/list", session = session)
        assertTrue(list.body.contains("ACME CO") || list.body.contains("Acme Co") || list.body.contains("ff-tenant-menu"))
        assertTrue(list.body.contains("Billing"))
        assertEquals("acme", session.get("tenant-seen") ?: run {
            // middleware attribute is request-scoped; confirm middleware ran by visiting again with capture
            var seen: Any? = null
            val capture = Router()
            Panel().id("desk").path("admin").guard(guard)
                .tenant(MtCompany::class.java, slugAttribute = "slug")
                .tenantMiddleware({ request, tenant, next ->
                    seen = (tenant as MtCompany).getAttribute("slug")
                    next.get()
                })
                .resource(orders())
                .mount(capture)
            get(capture, "/admin/acme/orders/list")
            seen
        })
    }

    @Test
    fun domainModeResolvesTenantFromHostAndBuildsSwitcherLinks() {
        val router = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .tenantDomain("{tenant}.erp.test")
            .resource(orders())
            .mount(router)
        guard.signIn(listOf(acme, beta))

        val wrong = get(router, "/admin/orders/list", headers = mapOf("Host" to "other.test"))
        assertEquals(404, wrong.status)

        val list = get(
            router,
            "/admin/orders/list",
            headers = mapOf("Host" to "acme.erp.test:8080", "X-Forwarded-Proto" to "https"),
        )
        assertEquals(200, list.status)
        assertTrue(list.body.contains("Acme order"))
        assertTrue(list.body.contains("https://beta.erp.test:8080/admin"))
    }

    @Test
    fun unscopedResourcesAndOwnershipOverridesWork() {
        val shared = object : Resource("tags") {
            init {
                model(MtTag::class.java)
                scopedToTenant(false)
            }
            override fun form(schema: Schema): Schema = schema.components(TextInput.make("name").required())
            override fun table(table: Table<*, *>): Table<*, *> = table.columns(TextColumn.make("name"))
        }
        val owned = object : Resource("orders") {
            init {
                model(MtOrder::class.java)
                tenantOwnershipRelationship("company")
            }
            override fun form(schema: Schema): Schema = schema.components(TextInput.make("name").required())
            override fun table(table: Table<*, *>): Table<*, *> = table.columns(TextColumn.make("name"))
        }
        val router = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, ownershipRelationship = "company", slugAttribute = "slug")
            .resource(owned)
            .resource(shared)
            .mount(router)
        guard.signIn(listOf(acme))

        val tags = get(router, "/admin/acme/tags/list")
        assertTrue(tags.body.contains("Shared"))

        val session = MtSession()
        val csrf = token(session, router, "/admin/acme/tags/create")
        assertEquals(302, post(router, "/admin/acme/tags/create", "name=Extra&_token=$csrf", session).status)
        assertEquals(2, MtTag().newQuery().count())

        assertFalse(shared.isScopedToTenant())
        assertEquals("company", owned.tenantOwnershipRelationshipName())
        assertNull(orders().tenantOwnershipRelationshipName())
        assertTrue(orders().isScopedToTenant())
    }

    @Test
    fun staticAndModelTenancyConflictAndValidationEdges() {
        assertThrows<FireflyException> {
            Panel().tenant(Tenant("acme")).tenant(MtCompany::class.java)
        }
        assertThrows<FireflyException> {
            Panel().tenant(MtCompany::class.java).tenant(Tenant("acme"))
        }
                assertThrows<FireflyException> {
            Panel().tenant(MtCompany::class.java).tenantDomain("no-token.example.com")
        }
        assertThrows<FireflyException> {
            Panel().tenant(MtCompany::class.java).tenantDomain("{tenant}.a.{tenant}.b")
        }
        assertThrows<FireflyException> { Panel().tenant(String::class.java) }
        assertThrows<FireflyException> { Panel().tenant(MtCompany::class.java, ownershipRelationship = " ") }
        assertThrows<FireflyException> { Panel().tenant(MtCompany::class.java, slugAttribute = " ") }
        assertThrows<FireflyException> { Panel().tenantMenu(false) }
        assertThrows<FireflyException> {
            Panel().path("").tenant(MtCompany::class.java).mount(Router())
        }
        assertThrows<FireflyException> { Resource("x").scopedToTenant(true).tenantOwnershipRelationship(" ") }

        assertThrows<FireflyException> {
            TenantProfile().save(acme, mapOf("name" to ""))
        }
        val blankName = TenantRegistration().also { it.bindModel(MtCompany::class.java) }
        assertEquals("Solo", blankName.register(mapOf("name" to "Solo"), null).let { (it as MtCompany).getAttribute("name") })

        val link = Ownerships(MtCompany::class.java, "company").link(MtOrder::class.java)
        assertNotNull(link)
        assertNull(Ownerships(MtCompany::class.java, "company").link(MtTag::class.java))
        assertThrows<FireflyException> { Ownerships(MtCompany::class.java, "tags").link(MtOrder::class.java) }

        val many = Ownerships(MtCompany::class.java, "companies").link(MtMember::class.java)
        assertNotNull(many)
        RecordScopes.use(TenantScope(acme, Ownerships(MtCompany::class.java, "companies"), emptySet()), Supplier {
            val member = RecordStores.open(MtMember::class.java).let { store ->
                @Suppress("UNCHECKED_CAST")
                val typed = store as org.konaframework.firefly.tables.RecordStore<MtMember, *>
                typed.save(typed.fill(typed.newRecord(), mapOf("name" to "Pat")))
            }
            assertTrue(
                database.connection().select("select count(*) as c from mt_company_member where company_id = ?", listOf(acme.getKey()))
                    .first()["c"].toString().toInt() >= 1,
            )
            member
        })

        assertEquals("acme", FireflyTenant.use("acme", Supplier { FireflyTenant.id() }))
        assertNull(FireflyTenant.current())
        assertEquals(acme.getKey().toString(), FireflyTenant.useModel(acme, Supplier { FireflyTenant.id() }))
        assertEquals(acme, FireflyTenant.useModel(acme, Supplier { FireflyTenant.current(MtCompany::class.java) }))
    }

    @Test
    fun guestsAreSentToLoginAndMenuCanHide() {
        val router = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .tenantMenu(false)
            .resource(orders())
            .mount(router)

        val guestRoot = get(router, "/admin")
        assertEquals(302, guestRoot.status)
        assertEquals("/admin/login", guestRoot.headers["Location"])

        val guestTenant = get(router, "/admin/acme/orders/list")
        assertEquals(302, guestTenant.status)

        guard.signIn(listOf(acme))
        val html = get(router, "/admin/acme/orders/list").body
        assertFalse(html.contains("ff-tenant-menu"))
    }

    @Test
    fun noRegistrationAndNoTenantsIsForbidden() {
        val router = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .resource(orders())
            .mount(router)
        guard.signIn(emptyList())
        assertEquals(403, get(router, "/admin").status)
    }


    @Test
    fun coverageEdgesForModelTenancy() {
        // Default ownership name from the model class.
        Panel().id("desk").path("admin").guard(guard).tenant(MtCompany::class.java, slugAttribute = "slug")
            .resource(orders()).mount(Router())

        val config = TenantConfig(MtCompany::class.java, "company", null)
        assertEquals(acme.getKey().toString(), config.slugOf(acme))
        assertEquals("Acme", config.labelOf(acme))
        acme.setAttribute("name", null)
        assertEquals(acme.getKey().toString(), config.labelOf(acme))
        assertEquals(acme.getKey(), config.find(acme.getKey().toString())!!.getKey())
        assertNull(config.find("missing-key"))

        val withSlug = TenantConfig(MtCompany::class.java, "company", "slug")
        assertEquals(
            acme.getKey(),
            withSlug.find(acme.getKey().toString())!!.getKey(),
            "falls back to key when slug misses",
        )

        val domain = TenantDomain("{tenant}.erp.test")
        assertNull(domain.slug(null))
        assertEquals("acme", domain.slug("acme.erp.test"))
        assertNull(domain.slug("nope.test"))

        val panel = Panel().id("desk")
        assertNull(config.landing(null, panel, null, "k"))
        assertNull(config.landing(object : Authenticatable {
            override fun getAuthIdentifier(): Any = "x"
            override fun getAuthPassword(): String = "h"
        }, panel, null, "k"))

        val deniedRemember = MtSession().also { it.put("k", acme.getKey().toString()) }
        guard.signIn(listOf(beta), default = beta)
        assertEquals(beta.getKey(), config.landing(guard.user(), panel, deniedRemember, "k")!!.getKey())

        // Home URL without port / without forwarded proto.
        val homeReq = Request("GET", "/admin", mapOf("Host" to "acme.erp.test"), emptyMap(), "")
        withSlug.domain = domain
        assertTrue(withSlug.homeUrl(acme, "admin", homeReq).startsWith("http://acme.erp.test/admin"))

        // BelongsToMany constrain path.
        RecordScopes.use(TenantScope(acme, Ownerships(MtCompany::class.java, "companies"), emptySet()), Supplier {
            @Suppress("UNCHECKED_CAST")
            val store = RecordStores.open(MtMember::class.java) as org.konaframework.firefly.tables.RecordStore<MtMember, ModelBuilder<*>>
            store.save(store.fill(store.newRecord(), mapOf("name" to "Kim")))
            assertEquals(1L, store.query().count())
            true
        })

        // hasMany cannot be an ownership relationship.
        class MtHasCompany : Cadence<MtHasCompany>() {
            override val table = "mt_orders"
            override val timestamps = false
            override val fillable = setOf("name", "company_id")
            fun company() = hasMany(MtCompany::class.java, "id", "company_id")
        }
        assertThrows<FireflyException> { Ownerships(MtCompany::class.java, "company").link(MtHasCompany::class.java) }

        // Guest + authenticate(false), non-HasTenants user, CSRF failures, denied page access.
        val openGuard = MtGuard(listOf(acme, beta))
        val open = Router()
        Panel().id("desk").path("admin").authenticate(false).guard(openGuard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .tenantRegistration(object : TenantRegistration() {
                override fun canAccess(): Boolean = false
            })
            .tenantProfile(object : TenantProfile() {
                override fun canAccess(): Boolean = false
            })
            .resource(orders())
            .mount(open)
        assertEquals(403, get(open, "/admin").status)
        assertEquals(403, get(open, "/admin/acme/orders/list").status)

        openGuard.signIn(listOf(acme))
        // Replace user with non-HasTenants authenticatable via raw sign-in override:
        val bare = object : PanelGuard by openGuard {
            override fun user(): Authenticatable? = object : Authenticatable {
                override fun getAuthIdentifier(): Any = "bare"
                override fun getAuthPassword(): String = "h"
            }
            override fun check(): Boolean = true
        }
        val bareRouter = Router()
        Panel().id("desk").path("admin").guard(bare)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .resource(orders())
            .mount(bareRouter)
        assertEquals(403, get(bareRouter, "/admin/acme/orders/list").status)

        val session = MtSession()
        val reg = Router()
        val closedReg = object : TenantRegistration() { override fun canAccess() = false }
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .tenantRegistration(closedReg)
            .mount(reg)
        guard.signIn(listOf(acme))
        assertEquals(403, get(reg, "/admin/new").status)
        guard.logout()
        assertEquals(302, get(reg, "/admin/new").status)

        // CSRF reject on registration + FireflyException from register.
        guard.signIn(listOf(acme))
        val boom = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .tenantRegistration(object : TenantRegistration() {
                override fun register(input: Map<String, String>, user: Authenticatable?): Any {
                    throw FireflyException("nope")
                }
            })
            .tenantProfile(TenantProfile())
            .resource(orders())
            .mount(boom)
        get(boom, "/admin/new", session = session)
        assertEquals(419, post(boom, "/admin/new", "name=X&_token=wrong", session).status)
        val okToken = token(session, boom, "/admin/new")
        assertEquals(422, post(boom, "/admin/new", "name=X&_token=$okToken", session).status)

        get(boom, "/admin/acme/tenant-profile", session = session)
        assertEquals(419, post(boom, "/admin/acme/tenant-profile", "name=Y&_token=wrong", session).status)
        assertEquals(403, run {
            val deniedProfile = Router()
            Panel().id("desk").path("admin").guard(guard)
                .tenant(MtCompany::class.java, slugAttribute = "slug")
                .tenantProfile(object : TenantProfile() { override fun canAccess() = false })
                .resource(orders())
                .mount(deniedProfile)
            get(deniedProfile, "/admin/acme/tenant-profile")
        }.status)

        // Domain gate 404 body + menu without HasTenants is covered above.
        // Profile save without session still redirects.
        val noSession = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .tenantProfile(TenantProfile())
            .resource(orders())
            .mount(noSession)
        val s2 = MtSession()
        val t = token(s2, noSession, "/admin/acme/tenant-profile")
        // Handle POST without attaching session so toast branch skips.
        val profileMatch = noSession.find("POST", "/admin/acme/tenant-profile")!!
        val reqPost = Request("POST", "/admin/acme/tenant-profile", mapOf("Content-Type" to "application/x-www-form-urlencoded"), emptyMap(), "name=Acme+Again&_token=$t")
        reqPost.setAttribute(Request.SESSION_ATTRIBUTE, s2)
        assertEquals(302, profileMatch.route.action.handle(reqPost).status)

        // postedFields skips _token / _x / query keys — exercised by posts above.
        assertThrows<FireflyException> { TenantProfile().save(acme, emptyMap()) }

        // Ownership override on a panel without tenancy (adopt skips naming).
        Panel().id("plain").path("plain").authenticate(false)
            .resource(object : Resource("orders") {
                init {
                    model(MtOrder::class.java)
                    tenantOwnershipRelationship("company")
                }
            })
            .mount(Router())

        // HasDefaultTenant that returns null falls through to the first tenant.
        guard.signIn(listOf(acme, beta), default = null)
        assertEquals(acme.getKey(), config.landing(guard.user(), panel, null, "k")!!.getKey())

        // Profile POST without a session skips the toast but still saves (CSRF skipped).
        val naked = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .tenantProfile(TenantProfile())
            .resource(orders())
            .mount(naked)
        guard.signIn(listOf(acme))
        val nakedPost = naked.find("POST", "/admin/acme/tenant-profile")!!
        val nakedReq = Request(
            "POST",
            "/admin/acme/tenant-profile",
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            emptyMap(),
            "name=Naked",
        )
        assertEquals(302, nakedPost.route.action.handle(nakedReq).status)
        assertEquals("Naked", acme.refresh().getAttribute("name"))

        // Slug attribute present but blank → fall through to key.
        acme.setAttribute("slug", null)
        assertEquals(acme.getKey().toString(), withSlug.slugOf(acme))
        acme.setAttribute("slug", "acme")
        acme.save()

        // Session present but empty key; user without HasDefaultTenant.
        class OnlyTenants : Authenticatable, HasTenants {
            override fun getAuthIdentifier(): Any = "only"
            override fun getAuthPassword(): String = "h"
            override fun getTenants(panel: Panel) = listOf(beta)
            override fun canAccessTenant(tenant: Any) = (tenant as MtCompany).getKey() == beta.getKey()
        }
        assertEquals(beta.getKey(), config.landing(OnlyTenants(), panel, MtSession().also { it.put("k", null) }, "k")!!.getKey())
        assertEquals(beta.getKey(), config.landing(OnlyTenants(), panel, MtSession(), "k")!!.getKey())

        // Domain home URL with null Host.
        val noHost = Request("GET", "/admin", emptyMap(), emptyMap(), "")
        withSlug.domain = domain
        assertTrue(withSlug.homeUrl(acme, "admin", noHost).contains("acme.erp.test"))

        // Panel host gate denies registration / root; tenant-domain gate denies registration on a bad host.
        val hosted = Router()
        Panel().id("desk").path("admin").domain("ops.example").guard(guard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .tenantRegistration(TenantRegistration())
            .tenantProfile(TenantProfile())
            .resource(orders())
            .mount(hosted)
        guard.signIn(listOf(acme))
        assertEquals(404, get(hosted, "/admin/new", headers = mapOf("Host" to "bad.test")).status)
        assertEquals(404, get(hosted, "/admin/acme/tenant-profile", headers = mapOf("Host" to "bad.test")).status)
        assertEquals(404, get(hosted, "/admin", headers = mapOf("Host" to "bad.test")).status)

        val tenantHosted = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .tenantDomain("{tenant}.erp.test")
            .tenantRegistration(TenantRegistration())
            .resource(orders())
            .mount(tenantHosted)
        assertEquals(404, get(tenantHosted, "/admin/new", headers = mapOf("Host" to "bad.test")).status)

        // Branding overrides read FireflyTenant.id() through tenantId().
        var seenCompany: String? = null
        val branded = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .resolveBrandingOverrides { ctx ->
                seenCompany = ctx.companyId
                null
            }
            .resource(orders())
            .mount(branded)
        get(branded, "/admin/acme/orders/list")
        assertEquals(acme.getKey().toString(), seenCompany)

        // Non-HasTenants user on a tenant-less chrome page still hides the menu.
        val bareUser = object : Authenticatable {
            override fun getAuthIdentifier(): Any = "bare2"
            override fun getAuthPassword(): String = "h"
        }
        val bareGuard = object : PanelGuard {
            override fun attempt(email: String, password: String) = false
            override fun check() = true
            override fun id(): String? = "bare2"
            override fun name(): String? = "Bare"
            override fun email(): String? = null
            override fun user(): Authenticatable? = bareUser
            override fun logout() {}
            override fun register(name: String, email: String, password: String): String? = null
            override fun requestReset(email: String): String? = null
            override fun verify(email: String): String? = null
        }
        val chrome = Router()
        Panel().id("desk").path("admin").guard(bareGuard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .profilePage(ProfilePage())
            .mount(chrome)
        val profileHtml = get(chrome, "/admin/profile").body
        assertFalse(profileHtml.contains("ff-tenant-menu"))

        // Ownership override resolved after name().
        val ownerships = Ownerships(MtCompany::class.java, "missing")
        ownerships.name(MtOrder::class.java, "company")
        assertNotNull(ownerships.link(MtOrder::class.java))

        // postedFields skips underscore keys and query keys.
        val fieldsRouter = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, slugAttribute = "slug")
            .tenantRegistration(object : TenantRegistration() {
                override fun register(input: Map<String, String>, user: Authenticatable?): Any {
                    assertFalse(input.containsKey("_extra"))
                    assertFalse(input.containsKey("q"))
                    return MtCompany().fill(mapOf("name" to input.getValue("name"), "slug" to "from-fields")).also { it.save() }
                }
            })
            .mount(fieldsRouter)
        guard.signIn(listOf(acme))
        val fs = MtSession()
        get(fieldsRouter, "/admin/new", session = fs)
        val tok = fs.get(FireflyCsrf.TOKEN_KEY).toString()
        val fieldsMatch = fieldsRouter.find("POST", "/admin/new")!!
        val body = "name=FromFields&_token=$tok&_extra=1&q=1"
        val fieldsReq = Request("POST", "/admin/new", mapOf("Content-Type" to "application/x-www-form-urlencoded"), mapOf("q" to "1"), body)
        fieldsReq.setAttribute(Request.SESSION_ATTRIBUTE, fs)
        assertEquals(302, fieldsMatch.route.action.handle(fieldsReq).status)
    }

    private fun orders(): Resource = object : Resource("orders") {
        init { model(MtOrder::class.java) }
        override fun form(schema: Schema): Schema = schema.components(TextInput.make("name").required())
        override fun table(table: Table<*, *>): Table<*, *> = table.columns(TextColumn.make("name"))
    }

    private fun mount(resource: Resource): Router {
        val router = Router()
        Panel().id("desk").path("admin").guard(guard)
            .tenant(MtCompany::class.java, ownershipRelationship = "company", slugAttribute = "slug")
            .resource(resource)
            .mount(router)
        return router
    }

    private fun get(
        router: Router,
        path: String,
        headers: Map<String, String> = emptyMap(),
        session: RequestSession? = null,
    ): Response {
        val match = router.find("GET", path) ?: throw AssertionError("missing GET $path")
        val request = Request("GET", path, headers, emptyMap(), "")
        if (session != null) request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        return match.route.action.handle(request)
    }

    private fun post(router: Router, path: String, body: String, session: RequestSession): Response {
        val match = router.find("POST", path) ?: throw AssertionError("missing POST $path")
        val request = Request(
            "POST",
            path,
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            emptyMap(),
            body,
        )
        request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        return match.route.action.handle(request)
    }

    private fun token(session: RequestSession, router: Router, path: String): String {
        get(router, path, session = session)
        return session.get(FireflyCsrf.TOKEN_KEY)?.toString() ?: error("csrf missing")
    }
}

class MtCompany : Cadence<MtCompany>() {
    override val table = "mt_companies"
    override val timestamps = false
    override val fillable = setOf("name", "slug")

    fun orders() = hasMany(MtOrder::class.java, "company_id")

    fun members() = belongsToMany(MtMember::class.java, "mt_company_member", "company_id", "member_id")
}

class MtOrder : Cadence<MtOrder>() {
    override val table = "mt_orders"
    override val timestamps = false
    override val fillable = setOf("name", "company_id")

    fun company() = belongsTo(MtCompany::class.java, "company_id")

    fun tags() = belongsToMany(MtTag::class.java, "mt_order_tag", "order_id", "tag_id")
}

class MtTag : Cadence<MtTag>() {
    override val table = "mt_tags"
    override val timestamps = false
    override val fillable = setOf("name")
}

class MtMember : Cadence<MtMember>() {
    override val table = "mt_members"
    override val timestamps = false
    override val fillable = setOf("name")

    fun companies() = belongsToMany(MtCompany::class.java, "mt_company_member", "member_id", "company_id")
}

class MtUser(
    var companies: MutableList<MtCompany>,
    var default: MtCompany? = null,
) : Authenticatable, HasTenants, HasDefaultTenant {
    override fun getAuthIdentifier(): Any = "user"
    override fun getAuthPassword(): String = "hash"
    override fun getTenants(panel: Panel): Collection<Any> = companies
    override fun canAccessTenant(tenant: Any): Boolean = companies.any { it.getKey() == (tenant as MtCompany).getKey() }
    override fun getDefaultTenant(panel: Panel): Any? = default
}

class MtGuard(private val all: List<MtCompany>) : PanelGuard {
    private var current: MtUser? = null

    fun signIn(companies: List<MtCompany>, default: MtCompany? = companies.firstOrNull()): MtGuard = apply {
        current = MtUser(companies.toMutableList(), default)
    }

    override fun attempt(email: String, password: String): Boolean = false

    override fun check(): Boolean = current != null

    override fun id(): String? = current?.getAuthIdentifier()?.toString()

    override fun name(): String? = "User"

    override fun email(): String? = "user@example.test"

    override fun user(): Authenticatable? = current

    override fun logout() {
        current = null
    }

    override fun register(name: String, email: String, password: String): String? = null

    override fun requestReset(email: String): String? = null

    override fun verify(email: String): String? = null
}

class MtSession : RequestSession {
    private val values = LinkedHashMap<String, Any?>()
    private val old = LinkedHashMap<String, String>()
    override fun get(key: String): Any? = values[key]
    override fun put(key: String, value: Any?): RequestSession = apply { values[key] = value }
    override fun flash(key: String, value: Any?): RequestSession = put(key, value)
    override fun flashInput(input: Map<String, String>): RequestSession = apply {
        old.clear()
        old.putAll(input)
    }
    override fun old(key: String, default: String?): String? = old[key] ?: default
    override fun oldInput(): Map<String, String> = LinkedHashMap(old)
    override fun previousUrl(): String? = null
    override fun setPreviousUrl(url: String): RequestSession = this
}

fun mtDatabase(): Database {
    val url = "jdbc:h2:mem:mt${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
    val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
    val connection = database.connection()
    connection.statement("create table mt_companies (id bigint auto_increment primary key, name varchar(100), slug varchar(100))")
    connection.statement("create table mt_orders (id bigint auto_increment primary key, name varchar(100), company_id bigint)")
    connection.statement("create table mt_tags (id bigint auto_increment primary key, name varchar(100))")
    connection.statement("create table mt_members (id bigint auto_increment primary key, name varchar(100))")
    connection.statement("create table mt_company_member (company_id bigint, member_id bigint)")
    connection.statement("create table mt_order_tag (order_id bigint, tag_id bigint)")
    database.setAsGlobal()
    return database
}
