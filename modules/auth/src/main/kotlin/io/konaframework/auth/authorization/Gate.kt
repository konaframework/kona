package io.konaframework.auth.authorization

import io.konaframework.auth.Authenticatable

/**
 * Ability callback used by [Gate.define] (Java-friendly SAM).
 */
fun interface GateCallback {
    fun allow(user: Authenticatable?, arguments: Array<out Any?>): Boolean
}

/** Resolves the current user for [Gate.allows] without an explicit user argument. */
fun interface GateUserResolver {
    fun resolve(): Authenticatable?
}

/**
 * Global before callback. Return `null` to continue; `true`/`false` to allow/deny.
 */
fun interface GateBeforeCallback {
    fun before(user: Authenticatable?, ability: String, arguments: Array<out Any?>): Boolean?
}

/**
 * Model policy registered via [Gate.policy].
 */
interface Policy {
    /**
     * Optional short-circuit before ability resolution.
     * Return `null` to continue; `true`/`false` to allow/deny.
     */
    fun before(user: Authenticatable?, ability: String): Boolean? = null

    fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean
}

/**
 * Authorization gate (Laravel Gate shape) with Java-callable registration.
 */
class Gate {
    private val abilities = linkedMapOf<String, GateCallback>()
    private val policies = linkedMapOf<Class<*>, Policy>()
    private val befores = mutableListOf<GateBeforeCallback>()
    private var userResolver: GateUserResolver = GateUserResolver { null }

    fun define(ability: String, callback: GateCallback): Gate {
        abilities[ability] = callback
        return this
    }

    fun policy(modelClass: Class<*>, policy: Policy): Gate {
        policies[modelClass] = policy
        return this
    }

    fun before(callback: GateBeforeCallback): Gate {
        befores += callback
        return this
    }

    fun setUserResolver(resolver: GateUserResolver): Gate {
        userResolver = resolver
        return this
    }

    fun forUser(user: Authenticatable?): GateInspector = GateInspector(this, user)

    fun allows(ability: String, vararg arguments: Any?): Boolean =
        allows(userResolver.resolve(), ability, *arguments)

    fun allows(user: Authenticatable?, ability: String, vararg arguments: Any?): Boolean =
        inspect(user, ability, arguments)

    fun denies(ability: String, vararg arguments: Any?): Boolean = !allows(ability, *arguments)

    fun denies(user: Authenticatable?, ability: String, vararg arguments: Any?): Boolean =
        !allows(user, ability, *arguments)

    /** True when any listed ability is allowed. */
    fun any(abilities: Collection<String>, vararg arguments: Any?): Boolean =
        any(userResolver.resolve(), abilities, *arguments)

    fun any(user: Authenticatable?, abilities: Collection<String>, vararg arguments: Any?): Boolean =
        abilities.any { allows(user, it, *arguments) }

    /** True when none of the listed abilities are allowed. */
    fun none(abilities: Collection<String>, vararg arguments: Any?): Boolean =
        none(userResolver.resolve(), abilities, *arguments)

    fun none(user: Authenticatable?, abilities: Collection<String>, vararg arguments: Any?): Boolean =
        abilities.none { allows(user, it, *arguments) }

    fun authorize(ability: String, vararg arguments: Any?) {
        authorize(userResolver.resolve(), ability, *arguments)
    }

    fun authorize(user: Authenticatable?, ability: String, vararg arguments: Any?) {
        if (!allows(user, ability, *arguments)) {
            throw AuthorizationException()
        }
    }

    fun has(ability: String): Boolean = abilities.containsKey(ability)

    fun getPolicyFor(model: Any): Policy? = getPolicyFor(model.javaClass)

    fun getPolicyFor(modelClass: Class<*>): Policy? {
        policies[modelClass]?.let { return it }
        return policies.entries.firstOrNull { it.key.isAssignableFrom(modelClass) }?.value
    }

    /**
     * Registers a policy by conventional name `{SimpleName}Policy` under [policyPackage]
     * when the class exists and implements [Policy]. Returns true when registered.
     */
    fun discoverPolicy(modelClass: Class<*>, policyPackage: String): Boolean {
        val simple = modelClass.simpleName + "Policy"
        val fqn = "$policyPackage.$simple"
        return try {
            @Suppress("UNCHECKED_CAST")
            val policyClass = Class.forName(fqn) as Class<out Policy>
            val instance = policyClass.getDeclaredConstructor().newInstance()
            policy(modelClass, instance)
            true
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: ReflectiveOperationException) {
            false
        }
    }

    internal fun inspect(user: Authenticatable?, ability: String, arguments: Array<out Any?>): Boolean {
        for (before in befores) {
            before.before(user, ability, arguments)?.let { return it }
        }
        val model = arguments.firstOrNull()
        if (model != null) {
            val policy = getPolicyFor(model)
            if (policy != null) {
                policy.before(user, ability)?.let { return it }
                return policy.authorize(user, ability, model)
            }
        }
        val callback = abilities[ability] ?: return false
        return callback.allow(user, arguments)
    }
}

/**
 * User-scoped gate view (`Gate::forUser`).
 */
class GateInspector(
    private val gate: Gate,
    private val user: Authenticatable?,
) {
    fun allows(ability: String, vararg arguments: Any?): Boolean =
        gate.allows(user, ability, *arguments)

    fun denies(ability: String, vararg arguments: Any?): Boolean =
        gate.denies(user, ability, *arguments)

    fun any(abilities: Collection<String>, vararg arguments: Any?): Boolean =
        gate.any(user, abilities, *arguments)

    fun none(abilities: Collection<String>, vararg arguments: Any?): Boolean =
        gate.none(user, abilities, *arguments)

    fun authorize(ability: String, vararg arguments: Any?) {
        gate.authorize(user, ability, *arguments)
    }
}
