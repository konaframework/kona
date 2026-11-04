package org.konaframework.firefly

import org.konaframework.auth.Authenticatable
import org.konaframework.auth.HasCredentials
import org.konaframework.auth.SimpleUser
import org.konaframework.auth.authorization.Policy
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database

/** Models, policies, guard, and users shared by the authorization tests. */
class AzPost : Cadence<AzPost>() {
    override val table = "az_posts"
    override val timestamps = false
    override val fillable = setOf("title", "status", "team")

    fun notes() = hasMany(AzNote::class.java, "post_id")
}

class AzNote : Cadence<AzNote>() {
    override val table = "az_notes"
    override val timestamps = false
    override val fillable = setOf("post_id", "body")
}

/** A model with no policy anywhere. */
class AzPlain : Cadence<AzPlain>() {
    override val table = "az_plain"
    override val timestamps = false
    override val fillable = setOf("name")
}

/** A model whose policy is found by convention in `app.policies`. */
class AzDiscovered : Cadence<AzDiscovered>() {
    override val table = "az_plain"
    override val timestamps = false
    override val fillable = setOf("name")
}

fun role(user: Authenticatable?): String? = (user as? HasCredentials)?.getCredential("role")?.toString()

/** admin: everything. editor: view, create, edit, delete drafts. viewer: view only. */
class AzPostPolicy : Policy {
    private fun admin(user: Authenticatable?) = role(user) == "admin"

    private fun staff(user: Authenticatable?) = role(user) == "admin" || role(user) == "editor"

    fun viewAny(user: Authenticatable?): Boolean = role(user) != null && role(user) != "blocked"

    fun view(user: Authenticatable?, post: AzPost): Boolean = viewAny(user)

    fun create(user: Authenticatable?): Boolean = staff(user)

    fun update(user: Authenticatable?, post: AzPost): Boolean = staff(user)

    fun delete(user: Authenticatable?, post: AzPost): Boolean =
        admin(user) || (role(user) == "editor" && post.getAttribute("status") == "draft")

    fun deleteAny(user: Authenticatable?): Boolean = staff(user)

    fun restore(user: Authenticatable?, post: AzPost): Boolean = admin(user)

    fun restoreAny(user: Authenticatable?): Boolean = admin(user)

    fun forceDelete(user: Authenticatable?, post: AzPost): Boolean = admin(user)

    fun forceDeleteAny(user: Authenticatable?): Boolean = admin(user)

    fun replicate(user: Authenticatable?, post: AzPost): Boolean = staff(user)

    fun reorder(user: Authenticatable?): Boolean = admin(user)

    fun publish(user: Authenticatable?, post: AzPost): Boolean = admin(user)

    override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = false
}

/** Related-model policy for the relation manager tests. It declares `attach` but not `associate`. */
class AzNotePolicy : Policy {
    fun viewAny(user: Authenticatable?): Boolean = role(user) != "blocked"

    fun view(user: Authenticatable?, note: AzNote): Boolean = role(user) != "blocked"

    fun create(user: Authenticatable?): Boolean = role(user) == "admin"

    fun update(user: Authenticatable?, note: AzNote): Boolean = role(user) == "admin"

    fun delete(user: Authenticatable?, note: AzNote): Boolean = role(user) == "admin" && note.getAttribute("body") != "keep"

    fun reorder(user: Authenticatable?): Boolean = role(user) == "admin"

    fun deleteAny(user: Authenticatable?): Boolean = role(user) == "admin"

    fun attach(user: Authenticatable?): Boolean = role(user) == "admin"

    override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = false
}

/** Panel guard with one fixed account per role. */
class AzGuard : PanelGuard {
    var current: Authenticatable? = null
    val accounts = linkedMapOf<String, Authenticatable>()

    fun signIn(role: String?): AzGuard = apply { current = if (role == null) null else azUser(role) }

    override fun attempt(email: String, password: String): Boolean {
        val account = accounts[email] ?: return false
        current = account
        return true
    }

    override fun check(): Boolean = current != null

    override fun id(): String? = current?.getAuthIdentifier()?.toString()

    override fun name(): String? = role(current)

    override fun email(): String? = current?.let { role(it) + "@example.test" }

    override fun user(): Authenticatable? = current

    override fun logout() {
        current = null
    }

    override fun register(name: String, email: String, password: String): String? = null

    override fun requestReset(email: String): String? = null

    override fun verify(email: String): String? = null
}

fun azUser(role: String): SimpleUser =
    SimpleUser(role, "hash", "$role@example.test", linkedMapOf("name" to role, "role" to role))

/** A user model that decides panel access itself. */
class AzStaff(private val allowed: Boolean) : Authenticatable, FireflyUser {
    override fun getAuthIdentifier(): Any = "staff"

    override fun getAuthPassword(): String = "hash"

    override fun canAccessPanel(panel: Panel): Boolean = allowed && panel.guardName() == "firefly-desk"
}

fun azDatabase(): Database {
    val url = "jdbc:h2:mem:az${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
    val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
    val connection = database.connection()
    connection.statement("create table az_posts (id bigint auto_increment primary key, title varchar(100), status varchar(20), team varchar(20))")
    connection.statement("create table az_notes (id bigint auto_increment primary key, post_id bigint, body varchar(100))")
    connection.statement("create table az_plain (id bigint auto_increment primary key, name varchar(100))")
    database.setAsGlobal()
    return database
}

/** An application guard that is always signed in as [user]. */
class StubGuard(var user: Authenticatable?) : org.konaframework.auth.Guard {
    override fun attempt(credentials: Map<String, Any?>, remember: Boolean) = false

    override fun login(user: Authenticatable, remember: Boolean) {
        this.user = user
    }

    override fun logout() {
        user = null
    }

    override fun check(): Boolean = user != null

    override fun id(): Any? = user?.getAuthIdentifier()

    override fun user(): Authenticatable? = user

    override fun setRequest(request: org.konaframework.http.Request): org.konaframework.auth.Guard = this

    override fun validate(credentials: Map<String, Any?>) = false

    override fun loginUsingId(id: Any, remember: Boolean): Authenticatable? = null

    override fun once(credentials: Map<String, Any?>) = false

    override fun onceUsingId(id: Any) = false
}
