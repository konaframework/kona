package org.konaframework.firefly.actions

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Schema

/** Decides a named ability. When no gate is installed, a named ability is denied. */
fun interface ActionGate {
    fun allows(ability: String, component: Component, schema: Schema): Boolean
}

/** Result of [Action.call]. `url` is a navigation link. `redirectUrl` is where the caller may send the browser after success or failure. */
class ActionResult(
    val status: String,
    val url: String?,
    val redirectUrl: String?,
    val message: String?,
) {
    companion object {
        const val SUCCESS = "success"
        const val HALTED = "halted"
        const val FAILED = "failed"
    }
}

/** Stops the action before [Action.after] and keeps the result halted. */
class ActionHalt : RuntimeException("Action halted")

/** Fails the action with a message. [Action.after] does not run. */
class ActionFailure(message: String) : RuntimeException(message)

fun interface ActionCallback {
    fun run(action: Action, schema: Schema)
}

/** Where an action is drawn: in the schema body, or in the page header. */
class ActionPlacement private constructor(val token: String) {
    companion object {
        @JvmField val SCHEMA = ActionPlacement("schema")
        @JvmField val HEADER = ActionPlacement("header")
    }
}
