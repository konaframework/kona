package app.policies

import app.models.Order
import org.konaframework.auth.Authenticatable
import org.konaframework.auth.authorization.Policy

/**
 * Found by Firefly through the `app.policies.{Model}Policy` name, so nothing registers it.
 * Orders can be listed, created and edited, but never deleted from the desk.
 */
class OrderPolicy : Policy {
    fun viewAny(user: Authenticatable?): Boolean = true

    fun view(user: Authenticatable?, order: Order): Boolean = true

    fun create(user: Authenticatable?): Boolean = true

    fun update(user: Authenticatable?, order: Order): Boolean = true

    fun delete(user: Authenticatable?, order: Order): Boolean = false

    fun deleteAny(user: Authenticatable?): Boolean = false

    override fun authorize(user: Authenticatable?, ability: String, model: Any): Boolean = false
}
