package dev.kona.http

import java.net.URI
import java.net.URISyntaxException

/**
 * Request validation (Laravel Validator shape).
 *
 * Rules: `required`, `nullable`, `bail`, `string`, `integer`, `numeric`, `boolean`, `email`, `url`, `array`,
 * `list`, `min:n`, `max:n`, `size:n`, `between:a,b`, `in:a,b`, `confirmed[:field]`, `same:field`,
 * `regex:/pattern/flags`. Rules may be pipe-joined (`"required|email"`) except `regex`.
 *
 * Like Laravel, only `required` runs for missing or blank fields; `nullable` skips the rest for null values.
 * `min`/`max`/`size`/`between` measure numbers by value (strings too under `numeric`/`integer`), collections by
 * item count, and everything else by character length. Unknown rules and malformed parameters fail closed.
 */
class Validator(
    private val data: Map<String, Any?>,
    private val rules: Map<String, List<String>>,
) {
    private val errors = linkedMapOf<String, MutableList<String>>()

    fun fails(): Boolean {
        runRules()
        return errors.isNotEmpty()
    }

    fun passes(): Boolean = !fails()

    fun errors(): Map<String, List<String>> = errors.mapValues { it.value.toList() }

    fun validate(): Map<String, Any?> {
        if (fails()) {
            throw ValidationException(errors())
        }
        return data.filterKeys { rules.containsKey(it) }
    }

    private fun runRules() {
        errors.clear()
        rules.forEach { (field, fieldRules) -> validateField(field, fieldRules.flatMap(::split).map(::ParsedRule)) }
    }

    private fun validateField(field: String, parsed: List<ParsedRule>) {
        val names = parsed.map { it.name }.toSet()
        val present = data.containsKey(field)
        val value = data[field]
        val context = Context(field, value, "numeric" in names || "integer" in names, data)
        for (rule in parsed) {
            val factory = CHECKS[rule.name]
            if (factory == null) {
                add(field, "Unknown validation rule [${rule.raw}].")
                continue
            }
            val check = try {
                factory(rule.param)
            } catch (_: IllegalArgumentException) {
                add(field, "Invalid validation rule [${rule.raw}].")
                continue
            }
            val implicit = rule.name == "required"
            if (!implicit && !validatable(present, value, "nullable" in names)) {
                continue
            }
            val message = check(context) ?: continue
            add(field, message)
            if (implicit || "bail" in names) {
                return
            }
        }
    }

    private fun validatable(present: Boolean, value: Any?, nullable: Boolean): Boolean {
        if (!present) {
            return false
        }
        if (value is String && value.isBlank()) {
            return false
        }
        return value != null || !nullable
    }

    private fun add(field: String, message: String) {
        errors.getOrPut(field) { mutableListOf() }.add(message)
    }

    private class ParsedRule(val raw: String) {
        val name: String = raw.substringBefore(':')
        val param: String? = if (raw.contains(':')) raw.substringAfter(':') else null
    }

    private class Context(
        val field: String,
        val value: Any?,
        val numeric: Boolean,
        val data: Map<String, Any?>,
    ) {
        val label: String = label(field)
    }

    private enum class Kind { NUMERIC, STRING, ARRAY }

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val NUMBER = Regex("^\\s*[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?\\s*$")
        private val INTEGER = Regex("^[+-]?(0|[1-9]\\d*)$")
        private val BOOLEANS: Set<Any> = setOf(true, false, 0, 1, "0", "1", "true", "false")
        private val REGEX_FLAGS: Map<Char, Set<RegexOption>> = mapOf(
            'i' to setOf(RegexOption.IGNORE_CASE),
            'm' to setOf(RegexOption.MULTILINE),
            's' to setOf(RegexOption.DOT_MATCHES_ALL),
            'x' to setOf(RegexOption.COMMENTS),
            'u' to emptySet(),
        )

        private val MIN = mapOf(
            Kind.NUMERIC to "must be at least :a.",
            Kind.STRING to "must be at least :a characters.",
            Kind.ARRAY to "must have at least :a items.",
        )
        private val MAX = mapOf(
            Kind.NUMERIC to "must not be greater than :a.",
            Kind.STRING to "must not be greater than :a characters.",
            Kind.ARRAY to "must not have more than :a items.",
        )
        private val SIZE = mapOf(
            Kind.NUMERIC to "must be :a.",
            Kind.STRING to "must be :a characters.",
            Kind.ARRAY to "must contain :a items.",
        )
        private val BETWEEN = mapOf(
            Kind.NUMERIC to "must be between :a and :b.",
            Kind.STRING to "must be between :a and :b characters.",
            Kind.ARRAY to "must have between :a and :b items.",
        )

        /** Rule name → parameter parser (throws [IllegalArgumentException] when malformed) → check. */
        private val CHECKS: Map<String, (String?) -> (Context) -> String?> = mapOf(
            "required" to { _ -> { c -> fail(isEmptyValue(c.value), c, "is required.") } },
            "nullable" to { _ -> { _ -> null } },
            "bail" to { _ -> { _ -> null } },
            "string" to { _ -> { c -> fail(c.value !is String, c, "must be a string.") } },
            "integer" to { _ -> { c -> fail(!isInteger(c.value), c, "must be an integer.") } },
            "numeric" to { _ -> { c -> fail(!isNumeric(c.value), c, "must be a number.") } },
            "boolean" to { _ -> { c -> fail(c.value !in BOOLEANS, c, "must be true or false.") } },
            "email" to { _ -> { c -> fail(!isEmail(c.value), c, "must be a valid email address.") } },
            "url" to { _ -> { c -> fail(!isUrl(c.value), c, "must be a valid URL.") } },
            "array" to { _ -> { c -> fail(!isArray(c.value), c, "must be an array.") } },
            "list" to { _ -> { c -> fail(!isList(c.value), c, "must be a list.") } },
            "min" to { p ->
                val a = required(p)
                val limit = number(a)
                check { c -> sized(c, MIN, a, "") { it >= limit } }
            },
            "max" to { p ->
                val a = required(p)
                val limit = number(a)
                check { c -> sized(c, MAX, a, "") { it <= limit } }
            },
            "size" to { p ->
                val a = required(p)
                val limit = number(a)
                check { c -> sized(c, SIZE, a, "") { it == limit } }
            },
            "between" to { p ->
                val bounds = required(p).split(',')
                require(bounds.size == 2)
                val low = number(bounds[0])
                val high = number(bounds[1])
                check { c -> sized(c, BETWEEN, bounds[0], bounds[1]) { it in low..high } }
            },
            "in" to { p ->
                val allowed = required(p).split(',').toSet()
                check { c -> if (inSet(c.value, allowed)) null else "The selected ${c.label} is invalid." }
            },
            "confirmed" to { p ->
                { c ->
                    val other = c.data[p ?: "${c.field}_confirmation"]
                    fail(other != c.value, c, "confirmation does not match.")
                }
            },
            "same" to { p ->
                val other = required(p)
                check { c -> fail(c.data[other] != c.value, c, "must match ${label(other)}.") }
            },
            "regex" to { p ->
                val pattern = pcre(required(p))
                check { c -> fail(!(c.value is String && pattern.containsMatchIn(c.value)), c, "format is invalid.") }
            },
        )

        @JvmStatic
        fun make(data: Map<String, Any?>, rules: Map<String, List<String>>): Validator =
            Validator(data, rules)

        @JvmStatic
        fun make(request: Request, rules: Map<String, List<String>>): Validator =
            Validator(request.data(), rules)

        private fun check(body: (Context) -> String?): (Context) -> String? = body

        private fun split(rule: String): List<String> =
            if (rule.startsWith("regex:")) listOf(rule) else rule.split('|')

        private fun label(field: String): String = field.replace('_', ' ')

        private fun fail(failed: Boolean, c: Context, suffix: String): String? =
            if (failed) "The ${c.label} field $suffix" else null

        private fun required(param: String?): String {
            require(!param.isNullOrEmpty())
            return param
        }

        private fun number(param: String): Double = param.trim().toDouble()

        private fun sized(
            c: Context,
            messages: Map<Kind, String>,
            a: String,
            b: String,
            ok: (Double) -> Boolean,
        ): String? {
            val (size, kind) = measure(c)
            if (ok(size)) {
                return null
            }
            return "The ${c.label} field " + messages.getValue(kind).replace(":a", a).replace(":b", b)
        }

        private fun measure(c: Context): Pair<Double, Kind> {
            return when (val v = c.value) {
                is Number -> v.toDouble() to Kind.NUMERIC
                is Collection<*> -> v.size.toDouble() to Kind.ARRAY
                is Map<*, *> -> v.size.toDouble() to Kind.ARRAY
                is Array<*> -> v.size.toDouble() to Kind.ARRAY
                else -> {
                    val text = if (v == null) "" else v.toString()
                    if (c.numeric && NUMBER.matches(text)) {
                        text.trim().toDouble() to Kind.NUMERIC
                    } else {
                        text.codePointCount(0, text.length).toDouble() to Kind.STRING
                    }
                }
            }
        }

        private fun isEmptyValue(value: Any?): Boolean = when (value) {
            null -> true
            is String -> value.isBlank()
            is Collection<*> -> value.isEmpty()
            is Map<*, *> -> value.isEmpty()
            is Array<*> -> value.isEmpty()
            else -> false
        }

        private fun isInteger(value: Any?): Boolean {
            if (value !is Number && value !is String) {
                return false
            }
            val text = value.toString().trim()
            return INTEGER.matches(text) && text.toLongOrNull() != null
        }

        private fun isNumeric(value: Any?): Boolean =
            value is Number || (value is String && NUMBER.matches(value))

        private fun isEmail(value: Any?): Boolean = value is String && EMAIL.matches(value)

        private fun isUrl(value: Any?): Boolean {
            if (value !is String) {
                return false
            }
            val uri = try {
                URI(value)
            } catch (_: URISyntaxException) {
                return false
            }
            return uri.scheme != null && uri.host != null
        }

        private fun isArray(value: Any?): Boolean = value is Collection<*> || value is Map<*, *> || value is Array<*>

        private fun isList(value: Any?): Boolean = value is List<*> || value is Array<*>

        private fun inSet(value: Any?, allowed: Set<String>): Boolean {
            if (value is Collection<*>) {
                return value.all { it.toString() in allowed }
            }
            return value.toString() in allowed
        }

        /** PCRE-style `/pattern/flags` (any non-alphanumeric delimiter), as Laravel's `regex` rule expects. */
        private fun pcre(param: String): Regex {
            val delimiter = param[0]
            require(!delimiter.isLetterOrDigit())
            val end = param.lastIndexOf(delimiter)
            require(end > 0)
            val options = param.substring(end + 1).flatMap { flag ->
                requireNotNull(REGEX_FLAGS[flag])
            }.toSet()
            return Regex(param.substring(1, end), options)
        }
    }
}

class ValidationException(
    val errors: Map<String, List<String>>,
) : RuntimeException(summarize(errors)) {
    private companion object {
        /** Laravel `ValidationException::summarize`: first message plus "(and N more errors)". */
        fun summarize(errors: Map<String, List<String>>): String {
            val messages = errors.values.flatten()
            val first = messages.firstOrNull() ?: return "The given data was invalid."
            val more = messages.size - 1
            if (more == 0) {
                return first
            }
            return "$first (and $more more ${if (more == 1) "error" else "errors"})"
        }
    }
}
