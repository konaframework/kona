package org.konaframework.firefly

import org.konaframework.auth.Auth
import org.konaframework.auth.Authenticatable
import org.konaframework.auth.authorization.Gate
import org.konaframework.auth.authorization.Policy
import java.util.function.Supplier

/**
 * Implement on the user model to decide who may enter a [Panel] (Filament `FilamentUser`).
 * [Panel.canAccess] adds panel-level rules on top. Both must allow.
 */
interface FireflyUser {
    fun canAccessPanel(panel: Panel): Boolean
}

/**
 * The signed-in user while a panel request runs. The panel opens a scope around every route, so
 * resource hooks such as [Resource.canViewAny] ask the Kona [Gate] on behalf of that user.
 * The scope also remembers records loaded for authorization during one request, so a table
 * with a row policy loads each row once.
 */
object FireflyAuth {
    private class Scope(val user: Authenticatable?) {
        val records = HashMap<String, Any?>()
    }

    private val current = ThreadLocal<Scope?>()

    /** The user of the current panel request, or null for a guest or outside a request. */
    @JvmStatic
    fun user(): Authenticatable? = current.get()?.user

    /** Runs [block] as [user]. Tests call this to ask resource hooks as a given user. */
    @JvmStatic
    fun <R> actingAs(user: Authenticatable?, block: Supplier<R>): R {
        val previous = current.get()
        current.set(Scope(user))
        try {
            return block.get()
        } finally {
            current.set(previous)
        }
    }

    /** [load] once per [key] while a request scope is open; every call outside a scope loads. */
    internal fun remember(key: String, load: () -> Any?): Any? {
        val scope = current.get() ?: return load()
        if (scope.records.containsKey(key)) return scope.records[key]
        val value = load()
        scope.records[key] = value
        return value
    }
}

/** Asks the Kona [Gate] on behalf of [FireflyAuth.user] when the model has a registered policy. */
internal object FireflyPolicies {
    /**
     * Whether [ability] is allowed on [model] (class level when [record] is null). With no gate
     * or no policy for [model] the answer is [allowWithoutPolicy].
     */
    fun allows(model: Class<*>, ability: String, record: Any?, allowWithoutPolicy: Boolean): Boolean {
        val gate = Firefly.activeGate()
        if (gate == null || Firefly.policyFor(model) == null) return allowWithoutPolicy
        // Prefer the panel request scope; fall back to the app Auth guard (same user after
        // reuseAppGuard + Auth.setRequest) so nav/canViewAny still works if the scope is missing.
        val user = FireflyAuth.user() ?: runCatching { Auth.user() }.getOrNull()
        return gate.allows(user, ability, record ?: model)
    }

    fun hasPolicy(model: Class<*>): Boolean = Firefly.policyFor(model) != null

    /** True when [policy] has a public method named [ability]. */
    fun declares(policy: Policy, ability: String): Boolean = policy.javaClass.methods.any { it.name == ability }
}
