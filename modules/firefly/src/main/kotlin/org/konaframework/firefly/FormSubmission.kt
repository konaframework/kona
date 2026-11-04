package org.konaframework.firefly

import org.konaframework.firefly.forms.Field
import org.konaframework.firefly.schemas.Schema
import org.konaframework.http.RequestSession

/**
 * Outcome of validating a posted resource form: field [errors] (empty when valid) and the
 * [attributes] to save. [passwordPaths] are never flashed back as old input.
 */
internal class FormSubmission(
    val errors: Map<String, List<String>>,
    val attributes: Map<String, Any?>,
    val passwordPaths: Set<String>,
) {
    /** Posted values worth redisplaying: everything except password fields. */
    fun oldInput(input: Map<String, String>): Map<String, String> = input.filterKeys { it !in passwordPaths }

    companion object {
        /** Session key holding the flashed field errors of a rejected create / edit post. */
        const val ERRORS_FLASH: String = "firefly.form.errors"
    }
}

/**
 * Refill a create / edit [schema] with the old input and per-field errors flashed by a rejected
 * post. Does nothing when no errors were flashed.
 */
internal fun applySubmitted(schema: Schema, state: Map<String, Any?>) {
    val session = state["filterSession"] as? RequestSession ?: return
    val flashed = session.get(FormSubmission.ERRORS_FLASH) as? Map<*, *> ?: return
    val old = session.oldInput()
    if (old.isNotEmpty()) schema.fill(old)
    for (field in schema.walk().filterIsInstance<Field<*>>()) {
        val path = field.absolutePath() ?: continue
        val messages = flashed[path] as? List<*> ?: continue
        field.helperText(messages.joinToString(" "))
        field.extraAttributes(mapOf("data-ff-invalid" to "true"))
    }
}
