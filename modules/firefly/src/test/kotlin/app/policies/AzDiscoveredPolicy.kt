package app.policies

import org.konaframework.auth.Authenticatable
import org.konaframework.auth.authorization.Policy

/** Found by convention (`app.policies.{Model}Policy`) for the `AzDiscovered` test model. */
class AzDiscoveredPolicy : Policy {
    fun viewAny(user: Authenticatable?): Boolean = user != null

    fun create(user: Authenticatable?): Boolean = false

    override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = false
}
