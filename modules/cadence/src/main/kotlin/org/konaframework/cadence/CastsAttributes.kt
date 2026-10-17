package org.konaframework.cadence

/**
 * Custom attribute cast (Laravel `CastsAttributes`).
 * Declare on a model with `override val casts = mapOf("money" to MoneyCast())`.
 */
interface CastsAttributes {
    /** Convert the stored (database) value to the value returned by [Cadence.getAttribute]. */
    fun get(model: Cadence<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any?

    /** Convert an assigned value into the form stored in attributes / the database. */
    fun set(model: Cadence<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any?
}
