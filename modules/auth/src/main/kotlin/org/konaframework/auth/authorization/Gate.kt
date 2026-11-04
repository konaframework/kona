package org.konaframework.auth.authorization

import org.konaframework.auth.Authenticatable
import java.lang.reflect.Method

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
 *
 * Prefer ability-named methods (`viewAny`, `create`, `update`, …) on the concrete policy.
 * [Gate] invokes those first, then falls back to [authorizeClass] / [authorize].
 */
interface Policy {
    /**
     * Optional short-circuit before ability resolution.
     * Return `null` to continue; `true`/`false` to allow/deny.
     * Runs for both instance and class-level checks.
     */
    fun before(user: Authenticatable?, ability: String): Boolean? = null

    /**
     * Fallback when no ability-named method exists for an instance argument.
     */
    fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean

    /**
     * Fallback when no ability-named method exists for a [Class] argument
     * (abilities such as `viewAny` / `create`). Defaults to [authorize] with the class.
     */
    fun authorizeClass(user: Authenticatable?, ability: String, modelClass: Class<*>): Boolean =
        authorize(user, ability, modelClass)
}

/**
 * Authorization gate with Java-callable registration.
 *
 * When the first ability argument is a [Class], the policy registered for that class is used
 * (not `java.lang.Class`). Ability-named policy methods are preferred over [Policy.authorize].
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

    /**
     * Resolves a policy for [model]. When [model] is a [Class], looks up that class
     * (so `allows("create", Post::class.java)` uses the Post policy).
     */
    fun getPolicyFor(model: Any): Policy? {
        if (model is Class<*>) {
            return getPolicyFor(model)
        }
        return getPolicyFor(model.javaClass)
    }

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
                return invokePolicy(policy, user, ability, arguments)
            }
        }
        val callback = abilities[ability] ?: return false
        return callback.allow(user, arguments)
    }

    internal fun invokePolicy(
        policy: Policy,
        user: Authenticatable?,
        ability: String,
        arguments: Array<out Any?>,
    ): Boolean {
        policy.before(user, ability)?.let { return it }

        val methodName = abilityMethodName(ability)
        val method = findAbilityMethod(policy, methodName)
        if (method != null) {
            return invokeAbilityMethod(policy, method, user, arguments)
        }

        val first = arguments[0] ?: return false
        return if (first is Class<*>) {
            policy.authorizeClass(user, ability, first)
        } else {
            policy.authorize(user, ability, first)
        }
    }

    companion object {
        @Volatile
        private var defaultGate: Gate? = null

        /** Sets the process-wide gate used by [Authorizable] defaults. */
        @JvmStatic
        fun setDefault(gate: Gate) {
            defaultGate = gate
        }

        @JvmStatic
        fun clearDefault() {
            defaultGate = null
        }

        @JvmStatic
        fun getDefault(): Gate {
            val gate = defaultGate
            if (gate == null) {
                throw IllegalStateException(
                    "Default Gate is not set; register AuthServiceProvider or call Gate.setDefault",
                )
            }
            return gate
        }

        @JvmStatic
        fun hasDefault(): Boolean {
            return defaultGate != null
        }

        internal fun abilityMethodName(ability: String): String {
            if (!ability.contains('-') && !ability.contains('_')) {
                return ability
            }
            val result = StringBuilder()
            var capitalizeNext = false
            for (ch in ability) {
                if (ch == '-' || ch == '_') {
                    capitalizeNext = true
                } else if (capitalizeNext) {
                    result.append(ch.titlecaseChar())
                    capitalizeNext = false
                } else {
                    result.append(ch)
                }
            }
            return result.toString()
        }

        internal fun findAbilityMethod(policy: Policy, methodName: String): Method? {
            if (methodName in RESERVED_POLICY_METHODS) {
                return null
            }
            var clazz: Class<*>? = policy.javaClass
            while (clazz != null) {
                if (clazz != Object::class.java) {
                    for (method in clazz.declaredMethods) {
                        if (method.name == methodName && isBooleanReturn(method)) {
                            return method
                        }
                    }
                }
                clazz = clazz.superclass
            }
            return null
        }

        private fun isBooleanReturn(method: Method): Boolean {
            val type = method.returnType
            return type == Boolean::class.javaPrimitiveType || type == java.lang.Boolean::class.java
        }

        private val RESERVED_POLICY_METHODS = setOf(
            "before",
            "authorize",
            "authorizeClass",
            "equals",
            "hashCode",
            "toString",
        )

        internal fun invokeAbilityMethod(
            policy: Policy,
            method: Method,
            user: Authenticatable?,
            arguments: Array<out Any?>,
        ): Boolean {
            method.isAccessible = true
            val callArgs = buildMethodArguments(method, user, arguments)
            val result = try {
                method.invoke(policy, *callArgs)
            } catch (ex: ReflectiveOperationException) {
                throw IllegalStateException(
                    "Failed to invoke policy method ${method.declaringClass.name}.${method.name}",
                    ex,
                )
            }
            return result == true
        }

        internal fun buildMethodArguments(
            method: Method,
            user: Authenticatable?,
            arguments: Array<out Any?>,
        ): Array<Any?> {
            val params = method.parameterTypes
            if (params.isEmpty()) {
                return emptyArray()
            }

            val remaining = arguments.toMutableList()
            if (remaining.isNotEmpty() && remaining[0] is Class<*>) {
                val second = params.getOrNull(1)
                val keepClassArgument = second != null && acceptsClassArgument(second)
                if (!keepClassArgument) {
                    remaining.removeAt(0)
                }
            }

            val built = ArrayList<Any?>(params.size)
            built.add(user)
            for (arg in remaining) {
                if (built.size >= params.size) {
                    break
                }
                built.add(arg)
            }
            while (built.size < params.size) {
                built.add(null)
            }
            return built.toTypedArray()
        }

        private fun acceptsClassArgument(parameterType: Class<*>): Boolean =
            parameterType == Class::class.java || parameterType == Object::class.java
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
