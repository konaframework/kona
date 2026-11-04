package org.konaframework.auth.authorization

import org.konaframework.auth.Authenticatable

/**
 * Mixin for authenticatable users that expose `can` / `cannot` / `canAny` against a [Gate].
 *
 * Default implementations use [Gate.getDefault] (set by `AuthServiceProvider`).
 * Override [authorizableGate] when the user should target a specific gate instance.
 */
interface Authorizable : Authenticatable {
    /** Gate used by [can], [cannot], and [canAny]. Defaults to [Gate.getDefault]. */
    fun authorizableGate(): Gate = Gate.getDefault()

    fun can(ability: String, vararg arguments: Any?): Boolean =
        authorizableGate().allows(this, ability, *arguments)

    fun cannot(ability: String, vararg arguments: Any?): Boolean =
        !can(ability, *arguments)

    fun canAny(abilities: Collection<String>, vararg arguments: Any?): Boolean =
        authorizableGate().any(this, abilities, *arguments)
}
