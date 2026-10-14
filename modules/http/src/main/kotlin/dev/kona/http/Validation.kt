package dev.kona.http

/**
 * Simple request validation (Laravel Validator shape for common rules).
 */
class Validator(
    private val data: Map<String, String>,
    private val rules: Map<String, List<String>>,
) {
    private val errors = linkedMapOf<String, MutableList<String>>()

    fun fails(): Boolean {
        runRules()
        return errors.isNotEmpty()
    }

    fun passes(): Boolean = !fails()

    fun errors(): Map<String, List<String>> = errors.mapValues { it.value.toList() }

    fun validate(): Map<String, String> {
        if (fails()) {
            throw ValidationException(errors())
        }
        return data.filterKeys { rules.containsKey(it) }
    }

    private fun runRules() {
        errors.clear()
        rules.forEach { (field, fieldRules) ->
            val value = data[field]
            fieldRules.forEach { rule ->
                applyRule(field, value, rule)
            }
        }
    }

    private fun applyRule(field: String, value: String?, rule: String) {
        val text = value ?: ""
        if (rule == "required") {
            if (text.isBlank()) {
                add(field, "The $field field is required.")
            }
            return
        }
        if (rule.startsWith("min:")) {
            if (text.length < rule.removePrefix("min:").toInt()) {
                add(field, "The $field field must be at least ${rule.removePrefix("min:")} characters.")
            }
            return
        }
        if (rule.startsWith("max:")) {
            if (text.length > rule.removePrefix("max:").toInt()) {
                add(field, "The $field field must not exceed ${rule.removePrefix("max:")} characters.")
            }
            return
        }
        if (rule == "email") {
            applyEmail(field, text)
            return
        }
        if (rule == "numeric") {
            applyNumeric(field, text)
        }
    }

    private fun applyEmail(field: String, text: String) {
        if (text.isEmpty()) {
            return
        }
        if (EMAIL.matches(text)) {
            return
        }
        add(field, "The $field field must be a valid email.")
    }

    private fun applyNumeric(field: String, text: String) {
        if (text.isEmpty()) {
            return
        }
        if (text.toDoubleOrNull() != null) {
            return
        }
        add(field, "The $field field must be numeric.")
    }

    private fun add(field: String, message: String) {
        errors.getOrPut(field) { mutableListOf() }.add(message)
    }

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        @JvmStatic
        fun make(data: Map<String, String>, rules: Map<String, List<String>>): Validator =
            Validator(data, rules)

        @JvmStatic
        fun make(request: Request, rules: Map<String, List<String>>): Validator =
            Validator(request.all(), rules)
    }
}

class ValidationException(
    val errors: Map<String, List<String>>,
) : RuntimeException("Validation failed")
