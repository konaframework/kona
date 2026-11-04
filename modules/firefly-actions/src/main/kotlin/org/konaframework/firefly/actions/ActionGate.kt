package org.konaframework.firefly.actions

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Schema

/** Decides a named ability. When no gate is installed, a named ability is denied. */
fun interface ActionGate {
    fun allows(ability: String, component: Component, schema: Schema): Boolean
}

/**
 * Result of [Action.call]. `url` is a navigation link. `redirectUrl` is where the caller may send the
 * browser after success or failure. `notificationTitle` is the toast title the action asked for
 * ([Action.successNotificationTitle] on success, [Action.failureNotificationTitle] on failure);
 * `message` is the failure or halt message.
 */
class ActionResult(
    val status: String,
    val url: String?,
    val redirectUrl: String?,
    val message: String?,
    val notificationTitle: String?,
) {
    constructor(status: String, url: String?, redirectUrl: String?, message: String?) :
        this(status, url, redirectUrl, message, null)

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

/**
 * Server handler that receives the record the action was mounted on (Filament `action(fn ($record))`).
 * The record is the model or row object of the page or table row; cast it to your type.
 */
fun interface RecordCallback {
    fun run(record: Any)
}

/**
 * Server handler for an action with a dialog [Action.schema]: receives the record and the validated
 * dialog values keyed by field name (Filament `action(fn ($record, array $data))`).
 */
fun interface RecordDataCallback {
    fun run(record: Any, data: Map<String, Any?>)
}

/** Where an action is drawn: in the schema body, or in the page header. */
class ActionPlacement private constructor(val token: String) {
    companion object {
        @JvmField val SCHEMA = ActionPlacement("schema")
        @JvmField val HEADER = ActionPlacement("header")
    }
}
