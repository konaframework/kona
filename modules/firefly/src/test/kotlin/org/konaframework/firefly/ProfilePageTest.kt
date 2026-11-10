package org.konaframework.firefly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.konaframework.auth.Authenticatable
import org.konaframework.auth.CadenceUserProvider
import org.konaframework.auth.Guard
import org.konaframework.auth.HasEmail
import org.konaframework.auth.HasName
import org.konaframework.auth.Hash
import org.konaframework.auth.SessionGuard
import org.konaframework.auth.SimpleUser
import org.konaframework.auth.hashing.HashManager
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.CadenceCompanion
import org.konaframework.cadence.CadenceException
import org.konaframework.cadence.CastType
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.ModelObserver
import org.konaframework.firefly.support.FireflyException
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router

class ProfilePageTest {
    @Test
    fun signedInUserSeesIdentityFieldsAndSaveUpdatesThatUser() {
        val guard = MemoryPanelGuard()
        guard.register("Ada", "ada@acct.test", "secret")
        guard.register("Bea", "bea@acct.test", "other")
        val router = Router()
        Panel().path("admin").guard(guard).mount(router)

        val signedOut = Router()
        val guest = MemoryPanelGuard()
        guest.register("Bea", "bea@acct.test", "other")
        Panel().path("admin").guard(guest).mount(signedOut)
        val denied = send(signedOut, "GET", "/admin/profile")
        assertEquals(302, denied.status)
        assertEquals("/admin/login", denied.headers["Location"])
        assertFalse(denied.body.contains("bea@acct.test"))

        guard.attempt("ada@acct.test", "secret")
        val page = send(router, "GET", "/admin/profile")
        assertEquals(200, page.status)
        assertTrue(page.body.contains("""type="text" name="name" value="Ada""""), page.body)
        assertTrue(page.body.contains("""type="email" name="email" value="ada@acct.test""""), page.body)
        assertTrue(page.body.contains("""name="current_password""""))
        assertTrue(page.body.contains("""name="password""""))
        assertTrue(page.body.contains("""name="password_confirmation""""))
        assertTrue(page.body.contains("ff-action-label\">Save<"))
        assertFalse(page.body.contains("<div class=\"ff-schema\"></div>"))
        assertFalse(page.body.contains("bea@acct.test"))

        val blankCurrent = send(
            router,
            "POST",
            "/admin/profile",
            "name=Ada&email=ada@acct.test&current_password=&password=other&password_confirmation=other",
        )
        assertEquals(422, blankCurrent.status)
        assertTrue(blankCurrent.body.contains("Current password is blank"))
        assertTrue(blankCurrent.body.contains("ff-auth-alert"))

        val wrong = send(
            router,
            "POST",
            "/admin/profile",
            "name=Ada&email=ada@acct.test&current_password=nope&password=other&password_confirmation=other",
        )
        assertEquals(422, wrong.status)
        assertTrue(wrong.body.contains("Current password is incorrect"))
        assertTrue(guard.attempt("ada@acct.test", "secret"))

        val renamed = send(
            router,
            "POST",
            "/admin/profile",
            "name=Ada Lovelace&email=ada@acct.test&current_password=&password=&password_confirmation=",
        )
        assertEquals(302, renamed.status)
        assertEquals("Ada Lovelace", guard.name())
        assertTrue(send(router, "GET", "/admin/profile").body.contains("""value="Ada Lovelace""""))

        val changed = send(
            router,
            "POST",
            "/admin/profile",
            "name=Ada Lovelace&email=ada@acct.test&current_password=secret&password=newer&password_confirmation=newer",
        )
        assertEquals(302, changed.status)
        assertTrue(guard.attempt("ada@acct.test", "newer"))
        assertFalse(guard.attempt("ada@acct.test", "secret"))
    }

    @Test
    fun authUserProfileRendersAndPersistsThroughTheExistingUser() {
        Hash.setManager(HashManager("bcrypt", 4))
        val database = Database()
            .addConnection(
                "default",
                ConnectionConfig(
                    "jdbc:h2:mem:profile${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                    "sa",
                    "",
                    poolSize = 2,
                ),
            )
            .setAsGlobal()
        try {
            database.connection().statement(
                """create table profile_accounts (
                    id bigint auto_increment primary key,
                    name varchar(255),
                    email varchar(255) unique,
                    password varchar(255)
                )""",
            )
            val ada = ProfileAccount()
            ada.fill(mapOf("name" to "Ada", "email" to "ada@profile.test", "password" to "secret")).save()
            val id = ada.getKey()
            ProfileAccount().fill(mapOf("name" to "Bea", "email" to "bea@profile.test", "password" to "secret")).save()
            val guard = SessionGuard("web", CadenceUserProvider(ProfileAccount::class.java, Hash.manager().driver()))
            val store = linkedMapOf<String, Any?>()
            val router = Router()
            Panel().path("admin").useAuthGuard(guard).mount(router)

            val denied = send(router, "GET", "/admin/profile")
            assertEquals(302, denied.status)
            assertEquals("/admin/login", denied.headers["Location"])
            assertFalse(denied.body.contains("ada@profile.test"))

            assertEquals(302, send(router, "POST", "/admin/login", "email=ada@profile.test&password=secret", store).status)
            val page = send(router, "GET", "/admin/profile", session = store)
            assertEquals(200, page.status)
            assertTrue(page.body.contains("""type="text" name="name" value="Ada""""), page.body)
            assertTrue(page.body.contains("""type="email" name="email" value="ada@profile.test""""), page.body)
            assertFalse(page.body.contains("bea@profile.test"))
            assertTrue(page.body.contains("ff-action-label\">Save<"))

            val saved = send(
                router,
                "POST",
                "/admin/profile",
                "name=Ada Lovelace&email=ada@profile.test&current_password=&password=&password_confirmation=",
                store,
            )
            assertEquals(302, saved.status)
            assertEquals("Ada Lovelace", ProfileAccount.find(id)!!.getAttribute("name"))

            val wrong = send(
                router,
                "POST",
                "/admin/profile",
                "name=Ada Lovelace&email=ada@profile.test&current_password=nope&password=newer&password_confirmation=newer",
                store,
            )
            assertEquals(422, wrong.status)
            assertTrue(wrong.body.contains("Current password is incorrect"))
            assertTrue(Hash.check("secret", ProfileAccount.find(id)!!.getAuthPassword()))

            val changed = send(
                router,
                "POST",
                "/admin/profile",
                "name=Ada Lovelace&email=ada@profile.test&current_password=secret&password=newer&password_confirmation=newer",
                store,
            )
            assertEquals(302, changed.status)
            val fresh = ProfileAccount.find(id)!!
            assertTrue(Hash.check("newer", fresh.getAuthPassword()))
            assertEquals(fresh.getAuthPassword(), guard.sessionPasswordHash())

            val taken = assertThrows<FireflyException> {
                ProfilePage().save(
                    AuthPanelGuard(guard),
                    mapOf(
                        "name" to "Ada Lovelace",
                        "email" to "bea@profile.test",
                        "password" to "",
                        "password_confirmation" to "",
                    ),
                )
            }
            assertFalse(taken.message!!.isBlank())
            assertEquals("ada@profile.test", ProfileAccount.find(id)!!.getAttribute("email"))
        } finally {
            database.close()
            Database.clearGlobal()
            Hash.clearManager()
        }
    }

    @Test
    fun saveRejectsGuestsAndAccountsItCannotUpdate() {
        val guest = assertThrows<FireflyException> {
            ProfilePage().save(MemoryPanelGuard(), mapOf("name" to "A", "email" to "a@a.test"))
        }
        assertEquals("Not signed in", guest.message)

        val locked = scripted(LockedProfileAccount())
        val lockedError = assertThrows<FireflyException> {
            ProfilePage().save(AuthPanelGuard(locked), mapOf("name" to "A", "email" to "a@a.test"))
        }
        assertTrue(lockedError.message!!.contains("override ProfilePage.save"))

        val nameOnly = scripted(NameOnlyProfileAccount())
        val nameOnlyError = assertThrows<FireflyException> {
            ProfilePage().save(AuthPanelGuard(nameOnly), mapOf("name" to "A", "email" to "a@a.test"))
        }
        assertTrue(nameOnlyError.message!!.contains("override ProfilePage.save"))

        Hash.setManager(HashManager("bcrypt", 4))
        try {
            val hash = Hash.make("secret")
            val noPassword = NoPasswordProfileAccount()
            noPassword.hash = hash
            val cannot = assertThrows<FireflyException> {
                ProfilePage().save(
                    AuthPanelGuard(scripted(noPassword)),
                    mapOf(
                        "name" to "A",
                        "email" to "a@a.test",
                        "password" to "newer",
                        "password_confirmation" to "newer",
                        "current_password" to "secret",
                    ),
                )
            }
            assertEquals("This account cannot change its password", cannot.message)

            val plain = scripted(SimpleUser("1", "secret", "plain@acct.test"))
            val plainError = assertThrows<FireflyException> {
                ProfilePage().save(
                    AuthPanelGuard(plain),
                    mapOf(
                        "name" to "A",
                        "email" to "plain@acct.test",
                        "password" to "newer",
                        "password_confirmation" to "newer",
                        "current_password" to "secret",
                    ),
                )
            }
            assertTrue(plainError.message!!.contains("override ProfilePage.save"))
            assertEquals("plain@acct.test", plain.seenLogin)

            val noUser = scripted(null)
            noUser.idValue = null
            noUser.accept = false
            val missing = assertThrows<FireflyException> {
                ProfilePage().save(
                    AuthPanelGuard(noUser),
                    mapOf(
                        "name" to "A",
                        "email" to "a@a.test",
                        "password" to "newer",
                        "password_confirmation" to "newer",
                        "current_password" to "secret",
                    ),
                )
            }
            assertEquals("Current password is incorrect", missing.message)
            assertEquals("", noUser.seenLogin)

            noUser.idValue = "7"
            val byId = assertThrows<FireflyException> {
                ProfilePage().save(
                    AuthPanelGuard(noUser),
                    mapOf(
                        "name" to "A",
                        "email" to "a@a.test",
                        "password" to "newer",
                        "password_confirmation" to "newer",
                        "current_password" to "secret",
                    ),
                )
            }
            assertEquals("Current password is incorrect", byId.message)
            assertEquals("7", noUser.seenLogin)

            val hashed = scripted(SimpleUser("1", hash, "hash@acct.test"))
            val wrongHash = assertThrows<FireflyException> {
                ProfilePage().save(
                    AuthPanelGuard(hashed),
                    mapOf(
                        "name" to "A",
                        "email" to "hash@acct.test",
                        "password" to "newer",
                        "password_confirmation" to "newer",
                        "current_password" to "nope",
                    ),
                )
            }
            assertEquals("Current password is incorrect", wrongHash.message)

            val boom = BoomProfileAccount()
            boom.hash = hash
            BoomProfileAccount.failure = ""
            val blank = assertThrows<FireflyException> {
                ProfilePage().save(
                    AuthPanelGuard(scripted(boom)),
                    mapOf(
                        "name" to "A",
                        "email" to "a@a.test",
                        "password" to "newer",
                        "password_confirmation" to "newer",
                        "current_password" to "secret",
                    ),
                )
            }
            assertEquals("Profile could not be saved", blank.message)

            BoomProfileAccount.failure = BoomProfileAccount.NULL_MESSAGE
            val unnamed = assertThrows<FireflyException> {
                ProfilePage().save(
                    AuthPanelGuard(scripted(boom)),
                    mapOf(
                        "name" to "A",
                        "email" to "a@a.test",
                        "password" to "newer",
                        "password_confirmation" to "newer",
                        "current_password" to "secret",
                    ),
                )
            }
            assertEquals("Profile could not be saved", unnamed.message)

            BoomProfileAccount.failure = "Email is already registered"
            val detailed = assertThrows<FireflyException> {
                ProfilePage().save(
                    AuthPanelGuard(scripted(boom)),
                    mapOf(
                        "name" to "A",
                        "email" to "a@a.test",
                        "password" to "newer",
                        "password_confirmation" to "newer",
                        "current_password" to "secret",
                    ),
                )
            }
            assertEquals("Email is already registered", detailed.message)

            BoomProfileAccount.failure = null
            val boomDb = Database()
                .addConnection(
                    "default",
                    ConnectionConfig(
                        "jdbc:h2:mem:boom${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                        "sa",
                        "",
                        poolSize = 2,
                    ),
                )
                .setAsGlobal()
            try {
                boomDb.connection().statement(
                    """create table boom_profile_accounts (
                        id bigint auto_increment primary key,
                        name varchar(255),
                        email varchar(255),
                        password varchar(255)
                    )""",
                )
                val exploding = scripted(boom)
                exploding.explode = true
                assertEquals(
                    "Profile updated",
                    ProfilePage().save(
                        AuthPanelGuard(exploding),
                        mapOf(
                            "name" to "Ada",
                            "email" to "ada@acct.test",
                            "password" to "newer",
                            "password_confirmation" to "newer",
                            "current_password" to "secret",
                        ),
                    ),
                )
                assertEquals("Ada", boom.getAttribute("name"))
            } finally {
                boomDb.close()
                Database.clearGlobal()
                BoomProfileAccount.failure = null
            }
        } finally {
            Hash.clearManager()
        }
    }

    @Test
    fun renderFillsIdentityFieldsAndShowsValidationText() {
        val empty = ProfilePage().render(emptyMap())
        assertTrue(empty.contains("""name="name""""))
        assertTrue(empty.contains("""value="""""))
        assertTrue(empty.contains("ff-action-label\">Save<"))
        assertFalse(empty.contains("ff-auth-alert"))

        val spaced = ProfilePage().render(mapOf("error" to "   "))
        assertFalse(spaced.contains("ff-auth-alert"))

        val invalid = ProfilePage().render(
            mapOf("name" to "Ada", "email" to "a@b.c", "error" to "Email <taken> & \"no\""),
        )
        assertTrue(invalid.contains("""type="text" name="name" value="Ada""""))
        assertTrue(invalid.contains("""type="email" name="email" value="a@b.c""""))
        assertTrue(invalid.contains("ff-auth-alert"))
        assertTrue(invalid.contains("Email &lt;taken&gt; &amp; &quot;no&quot;"))

        val page = ProfilePage()
        val field = org.konaframework.firefly.widgets.Page::class.java.getDeclaredField("formSchema")
        field.isAccessible = true
        field.set(page, null)
        val missing = page.render(mapOf("error" to "profile-missing-slot"))
        assertFalse(missing.contains("profile-missing-slot"))
        assertFalse(missing.contains("ff-auth-alert"))
        assertTrue(missing.contains("ff-profile"))
    }

    private fun scripted(account: Authenticatable?): ScriptedGuard {
        val guard = ScriptedGuard()
        guard.account = account
        return guard
    }

    private fun send(
        router: Router,
        method: String,
        path: String,
        body: String = "",
        session: MutableMap<String, Any?>? = null,
    ): Response {
        val match = router.find(method, path) ?: throw AssertionError("missing $method $path")
        val request = Request(method, path, emptyMap(), emptyMap(), body)
        if (session != null) request.setAttribute("session", session)
        return match.route.action.handle(request)
    }
}

class ProfileAccount : Cadence<ProfileAccount>(), Authenticatable, HasName, HasEmail {
    override val table = "profile_accounts"
    override val timestamps = false
    override val fillable = setOf("name", "email", "password")
    override val casts = mapOf("password" to CastType.HASHED)

    companion object : CadenceCompanion<ProfileAccount>(ProfileAccount::class.java)

    override fun getAuthIdentifier(): Any = getKey() ?: 0

    override fun getAuthPassword(): String = getAttribute("password")?.toString() ?: ""

    override fun getName(): String? = getAttribute("name")?.toString()

    override fun getEmail(): String? = getAttribute("email")?.toString()
}

class LockedProfileAccount : Cadence<LockedProfileAccount>(), Authenticatable {
    override val table = "locked_profile_accounts"
    override val timestamps = false

    override fun getAuthIdentifier(): Any = 1

    override fun getAuthPassword(): String = "secret"
}

class NameOnlyProfileAccount : Cadence<NameOnlyProfileAccount>(), Authenticatable {
    override val table = "name_only_profile_accounts"
    override val timestamps = false
    override val fillable = setOf("name")

    override fun getAuthIdentifier(): Any = 1

    override fun getAuthPassword(): String = "secret"
}

class NoPasswordProfileAccount : Cadence<NoPasswordProfileAccount>(), Authenticatable, HasName, HasEmail {
    override val table = "no_password_profile_accounts"
    override val timestamps = false
    override val fillable = setOf("name", "email")
    var hash: String = ""

    override fun getAuthIdentifier(): Any = 1

    override fun getAuthPassword(): String = hash

    override fun getName(): String? = "Ada"

    override fun getEmail(): String? = "ada@acct.test"
}

class BoomProfileAccount : Cadence<BoomProfileAccount>(), Authenticatable, HasName, HasEmail {
    override val table = "boom_profile_accounts"
    override val timestamps = false
    override val fillable = setOf("name", "email", "password")
    override val casts = mapOf("password" to CastType.HASHED)
    var hash: String = ""

            companion object : CadenceCompanion<BoomProfileAccount>(BoomProfileAccount::class.java) {
        const val NULL_MESSAGE = "\u0000"
        var failure: String? = null

        init {
            observe(object : ModelObserver<BoomProfileAccount>() {
                override fun saving(model: BoomProfileAccount) {
                    val message = failure ?: return
                    if (message == NULL_MESSAGE) {
                        throw object : CadenceException("") {
                            override val message: String? get() = null
                        }
                    }
                    throw CadenceException(message)
                }
            })
        }
    }

    override fun getAuthIdentifier(): Any = 1

    override fun getAuthPassword(): String = hash

    override fun getName(): String? = getAttribute("name")?.toString()

    override fun getEmail(): String? = getAttribute("email")?.toString()
}

private class ScriptedGuard : Guard {
    var account: Authenticatable? = null
    var idValue: Any? = "1"
    var seenLogin: String? = null
    var explode: Boolean = false
    var accept: Boolean = true

    override fun attempt(credentials: Map<String, Any?>, remember: Boolean): Boolean {
        seenLogin = credentials["email"]?.toString()
        if (explode) throw IllegalStateException("SessionGuard has no request")
        return accept
    }

    override fun login(user: Authenticatable, remember: Boolean) {}

    override fun logout() {}

    override fun check(): Boolean = true

    override fun id(): Any? = idValue

    override fun user(): Authenticatable? = account

    override fun setRequest(request: Request): Guard = this

    override fun validate(credentials: Map<String, Any?>): Boolean = true

    override fun loginUsingId(id: Any, remember: Boolean): Authenticatable? = null

    override fun once(credentials: Map<String, Any?>): Boolean = true

    override fun onceUsingId(id: Any): Boolean = true
}
