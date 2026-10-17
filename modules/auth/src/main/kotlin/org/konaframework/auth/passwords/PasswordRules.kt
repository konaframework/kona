package org.konaframework.auth.passwords

/**
 * Builds validation rule lists for password fields (Laravel `Password` rule helper shape).
 *
 * Consumed by HTTP `Validator` rule strings — does not own validation itself.
 */
object PasswordRules {
    private var defaults: List<String> = listOf("required", "string", "min:8")

    @JvmStatic
    fun defaults(): List<String> = defaults.toList()

    @JvmStatic
    fun defaults(rules: List<String>) {
        defaults = rules.toList()
    }

    @JvmStatic
    fun resetDefaults() {
        defaults = listOf("required", "string", "min:8")
    }

    @JvmStatic
    @JvmOverloads
    fun min(length: Int = 8): List<String> = listOf("required", "string", "min:$length")

    @JvmStatic
    fun confirmed(): List<String> = defaults() + "confirmed"

    @JvmStatic
    fun uncompromised(): List<String> = defaults() // placeholder parity; no HIBP call in-process
}
