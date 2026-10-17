package org.konaframework.http

import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Request validation (Laravel Validator shape).
 *
 * Rules may be pipe-joined (`"required|email"`) except `regex` / `not_regex` / `date_format` when the
 * parameter contains `|`.
 *
 * Like Laravel, only implicit rules (`required`, `filled`, `present`, `required_*`, …) run for missing
 * or blank fields; `nullable` skips the rest for null values; `sometimes` skips the field when absent.
 * `min`/`max`/`size`/`between` measure numbers by value (strings too under `numeric`/`integer`), collections
 * by item count, files by kilobytes, and everything else by character length. Unknown rules and malformed
 * parameters fail closed.
 *
 * DB-aware `unique` / `exists` use [ExistenceChecker] when registered; without a checker they fail closed
 * with a clear message (no Cadence dependency in `kona-http`).
 */
class Validator(
    private val data: Map<String, Any?>,
    private val rules: Map<String, List<String>>,
) {
    private val errors = linkedMapOf<String, MutableList<String>>()
    private val excluded = linkedSetOf<String>()

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
        return data.filterKeys { rules.containsKey(it) && it !in excluded }
    }

    private fun runRules() {
        errors.clear()
        excluded.clear()
        rules.forEach { (field, fieldRules) -> validateField(field, fieldRules.flatMap(::split).map(::ParsedRule)) }
    }

    private fun validateField(field: String, parsed: List<ParsedRule>) {
        val names = parsed.map { it.name }.toSet()
        val present = data.containsKey(field)
        val value = data[field]
        if ("sometimes" in names && !present) {
            return
        }
        val context = Context(field, value, "numeric" in names || "integer" in names || "digits" in names, data)
        for (rule in parsed) {
            if (rule.name in EXCLUDE_RULES) {
                val exclude = try {
                    shouldExclude(rule, context)
                } catch (_: IllegalArgumentException) {
                    add(field, "Invalid validation rule [${rule.raw}].")
                    continue
                }
                if (exclude) {
                    excluded.add(field)
                    return
                }
                continue
            }
            if (rule.name == "sometimes" || rule.name == "nullable" || rule.name == "bail") {
                continue
            }
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
            val implicit = rule.name in IMPLICIT
            if (!implicit && !validatable(present, value, "nullable" in names)) {
                continue
            }
            val message = check(context) ?: continue
            add(field, message)
            if (rule.name == "required" || "bail" in names) {
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

    private enum class Kind { NUMERIC, STRING, ARRAY, FILE }

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val NUMBER = Regex("^\\s*[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?\\s*$")
        private val INTEGER = Regex("^[+-]?(0|[1-9]\\d*)$")
        private val BOOLEANS: Set<Any> = setOf(true, false, 0, 1, "0", "1", "true", "false")
        private val ACCEPTED = setOf("yes", "on", "1", "true")
        private val DECLINED = setOf("no", "off", "0", "false")
        private val ALPHA = Regex("^[\\p{L}]+$")
        private val ALPHA_NUM = Regex("^[\\p{L}\\p{N}]+$")
        private val ALPHA_DASH = Regex("^[\\p{L}\\p{N}_-]+$")
        private val ASCII = Regex("^[\\x00-\\x7F]*$")
        private val DIGITS = Regex("^\\d+$")
        private val ULID = Regex("^[0-7][0-9A-HJKMNP-TV-Z]{25}$", RegexOption.IGNORE_CASE)
        private val MAC = Regex(
            "^([0-9A-Fa-f]{2}([-:])){5}([0-9A-Fa-f]{2})$|^([0-9A-Fa-f]{4}\\.){2}([0-9A-Fa-f]{4})$",
        )
        private val IPV4 = Regex(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$",
        )

        @Volatile
        private var currentPasswordChecker: CurrentPasswordChecker? = null

        /** Register a checker for the `current_password` rule (typically wired to the auth hasher). */
        @JvmStatic
        fun setCurrentPasswordChecker(checker: CurrentPasswordChecker?) {
            currentPasswordChecker = checker
        }
        private val REGEX_FLAGS: Map<Char, Set<RegexOption>> = mapOf(
            'i' to setOf(RegexOption.IGNORE_CASE),
            'm' to setOf(RegexOption.MULTILINE),
            's' to setOf(RegexOption.DOT_MATCHES_ALL),
            'x' to setOf(RegexOption.COMMENTS),
            'u' to emptySet(),
        )

        private val EXCLUDE_RULES = setOf(
            "exclude",
            "exclude_if",
            "exclude_unless",
            "exclude_with",
            "exclude_without",
        )

        private val IMPLICIT = setOf(
            "required",
            "filled",
            "present",
            "required_if",
            "required_unless",
            "required_with",
            "required_without",
            "required_with_all",
            "required_without_all",
            "prohibited",
            "prohibited_if",
            "prohibited_unless",
            "prohibits",
            "accepted_if",
            "declined_if",
            "current_password",
        )

        private val MIN = mapOf(
            Kind.NUMERIC to "must be at least :a.",
            Kind.STRING to "must be at least :a characters.",
            Kind.ARRAY to "must have at least :a items.",
            Kind.FILE to "must be at least :a kilobytes.",
        )
        private val MAX = mapOf(
            Kind.NUMERIC to "must not be greater than :a.",
            Kind.STRING to "must not be greater than :a characters.",
            Kind.ARRAY to "must not have more than :a items.",
            Kind.FILE to "must not be greater than :a kilobytes.",
        )
        private val SIZE = mapOf(
            Kind.NUMERIC to "must be :a.",
            Kind.STRING to "must be :a characters.",
            Kind.ARRAY to "must contain :a items.",
            Kind.FILE to "must be :a kilobytes.",
        )
        private val BETWEEN = mapOf(
            Kind.NUMERIC to "must be between :a and :b.",
            Kind.STRING to "must be between :a and :b characters.",
            Kind.ARRAY to "must have between :a and :b items.",
            Kind.FILE to "must be between :a and :b kilobytes.",
        )

        @Volatile
        private var existenceChecker: ExistenceChecker? = null

        /** Register a DB existence/unique checker (optional; typically wired by Cadence). */
        @JvmStatic
        fun setExistenceChecker(checker: ExistenceChecker?) {
            existenceChecker = checker
        }

        @JvmStatic
        fun existenceChecker(): ExistenceChecker? = existenceChecker

        /** Rule name → parameter parser (throws [IllegalArgumentException] when malformed) → check. */
        private val CHECKS: Map<String, (String?) -> (Context) -> String?> = mapOf(
            "required" to { _ -> { c -> fail(isEmptyValue(c.value), c, "is required.") } },
            "filled" to { _ ->
                { c ->
                    if (!c.data.containsKey(c.field)) {
                        null
                    } else {
                        fail(isEmptyValue(c.value), c, "must have a value.")
                    }
                }
            },
            "present" to { _ ->
                { c ->
                    if (c.data.containsKey(c.field)) {
                        null
                    } else {
                        "The ${c.label} field must be present."
                    }
                }
            },
            "nullable" to { _ -> { _ -> null } },
            "bail" to { _ -> { _ -> null } },
            "sometimes" to { _ -> { _ -> null } },
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
            "not_in" to { p ->
                val denied = required(p).split(',').toSet()
                check { c -> if (!inSet(c.value, denied)) null else "The selected ${c.label} is invalid." }
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
            "different" to { p ->
                val other = required(p)
                check { c -> fail(c.data[other] == c.value, c, "must be different from ${label(other)}.") }
            },
            "regex" to { p ->
                val pattern = pcre(required(p))
                check { c -> fail(!(c.value is String && pattern.containsMatchIn(c.value)), c, "format is invalid.") }
            },
            "not_regex" to { p ->
                val pattern = pcre(required(p))
                check { c -> fail(c.value is String && pattern.containsMatchIn(c.value), c, "format is invalid.") }
            },
            "file" to { _ ->
                { c ->
                    val uploaded = c.value as? UploadedFile
                    fail(uploaded == null || !uploaded.isValid(), c, "must be a file.")
                }
            },
            "image" to { _ ->
                { c ->
                    val uploaded = c.value
                    if (uploaded !is UploadedFile) {
                        fail(true, c, "must be an image.")
                    } else if (!uploaded.isValid()) {
                        fail(true, c, "must be an image.")
                    } else {
                        fail(!isImage(uploaded), c, "must be an image.")
                    }
                }
            },
            "mimes" to { p -> fileTypeRule(p, useMime = false) },
            "mimetypes" to { p -> fileTypeRule(p, useMime = true) },
            "accepted" to { _ ->
                { c ->
                    if (c.value == null) {
                        fail(true, c, "must be accepted.")
                    } else {
                        fail(c.value.toString().trim().lowercase() !in ACCEPTED, c, "must be accepted.")
                    }
                }
            },
            "declined" to { _ ->
                { c ->
                    if (c.value == null) {
                        fail(true, c, "must be declined.")
                    } else {
                        fail(c.value.toString().trim().lowercase() !in DECLINED, c, "must be declined.")
                    }
                }
            },
            "alpha" to { _ ->
                { c -> fail(!(c.value is String && ALPHA.matches(c.value)), c, "must only contain letters.") }
            },
            "alpha_num" to { _ ->
                { c ->
                    fail(
                        !(c.value is String && ALPHA_NUM.matches(c.value)),
                        c,
                        "must only contain letters and numbers.",
                    )
                }
            },
            "alpha_dash" to { _ ->
                { c ->
                    fail(
                        !(c.value is String && ALPHA_DASH.matches(c.value)),
                        c,
                        "must only contain letters, numbers, dashes, and underscores.",
                    )
                }
            },
            "ascii" to { _ ->
                { c -> fail(!(c.value is String && ASCII.matches(c.value)), c, "must only contain ASCII characters.") }
            },
            "uuid" to { _ ->
                { c ->
                    fail(!isUuid(c.value), c, "must be a valid UUID.")
                }
            },
            "json" to { _ ->
                { c -> fail(!isJson(c.value), c, "must be a valid JSON string.") }
            },
            "ip" to { _ -> { c -> fail(!isIp(c.value, v4 = true, v6 = true), c, "must be a valid IP address.") } },
            "ipv4" to { _ -> { c -> fail(!isIp(c.value, v4 = true, v6 = false), c, "must be a valid IPv4 address.") } },
            "ipv6" to { _ -> { c -> fail(!isIp(c.value, v4 = false, v6 = true), c, "must be a valid IPv6 address.") } },
            "timezone" to { _ ->
                { c ->
                    if (c.value == null) {
                        fail(true, c, "must be a valid timezone.")
                    } else {
                        fail(!ZoneId.getAvailableZoneIds().contains(c.value.toString()), c, "must be a valid timezone.")
                    }
                }
            },
            "date" to { _ ->
                { c -> fail(parseDate(c.value) == null, c, "must be a valid date.") }
            },
            "date_format" to { p ->
                val formatter = dateFormatter(required(p))
                check { c ->
                    val text = c.value as? String
                    fail(text == null || !matchesFormat(text, formatter), c, "must match the format ${required(p)}.")
                }
            },
            "after" to { p -> dateCompare(required(p), exclusive = true, after = true) },
            "after_or_equal" to { p -> dateCompare(required(p), exclusive = false, after = true) },
            "before" to { p -> dateCompare(required(p), exclusive = true, after = false) },
            "before_or_equal" to { p -> dateCompare(required(p), exclusive = false, after = false) },
            "starts_with" to { p ->
                val prefixes = required(p).split(',')
                check { c ->
                    if (c.value == null) {
                        fail(true, c, "must start with one of: ${prefixes.joinToString(", ")}.")
                    } else {
                        val text = c.value.toString()
                        fail(prefixes.none { text.startsWith(it) }, c, "must start with one of: ${prefixes.joinToString(", ")}.")
                    }
                }
            },
            "ends_with" to { p ->
                val suffixes = required(p).split(',')
                check { c ->
                    if (c.value == null) {
                        fail(true, c, "must end with one of: ${suffixes.joinToString(", ")}.")
                    } else {
                        val text = c.value.toString()
                        fail(suffixes.none { text.endsWith(it) }, c, "must end with one of: ${suffixes.joinToString(", ")}.")
                    }
                }
            },
            "digits" to { p ->
                val len = required(p).trim().toInt()
                check { c ->
                    if (c.value == null) {
                        fail(true, c, "must be $len digits.")
                    } else {
                        val text = c.value.toString().trim()
                        fail(!DIGITS.matches(text) || text.length != len, c, "must be $len digits.")
                    }
                }
            },
            "digits_between" to { p ->
                val parts = required(p).split(',')
                require(parts.size == 2)
                val low = parts[0].trim().toInt()
                val high = parts[1].trim().toInt()
                check { c ->
                    if (c.value == null) {
                        fail(true, c, "must be between $low and $high digits.")
                    } else {
                        val text = c.value.toString().trim()
                        fail(!DIGITS.matches(text) || text.length !in low..high, c, "must be between $low and $high digits.")
                    }
                }
            },
            "lowercase" to { _ ->
                { c ->
                    val text = c.value as? String
                    fail(text == null || text != text.lowercase(), c, "must be lowercase.")
                }
            },
            "uppercase" to { _ ->
                { c ->
                    val text = c.value as? String
                    fail(text == null || text != text.uppercase(), c, "must be uppercase.")
                }
            },
            "distinct" to { _ ->
                { c ->
                    val items = when (val v = c.value) {
                        is Collection<*> -> v
                        is Array<*> -> v.toList()
                        else -> null
                    }
                    if (items == null) {
                        fail(true, c, "must be an array.")
                    } else {
                        fail(items.size != items.toSet().size, c, "has duplicate values.")
                    }
                }
            },
            "required_if" to { p ->
                val parts = required(p).split(',', limit = 2)
                require(parts.size == 2)
                val other = parts[0]
                val expected = parts[1]
                check { c ->
                    if (!fieldEquals(c.data, other, expected)) {
                        null
                    } else if (isEmptyValue(c.value)) {
                        fail(true, c, "is required when $other is $expected.")
                    } else {
                        null
                    }
                }
            },
            "required_unless" to { p ->
                val parts = required(p).split(',', limit = 2)
                require(parts.size == 2)
                val other = parts[0]
                val expected = parts[1]
                check { c ->
                    if (fieldEquals(c.data, other, expected)) {
                        null
                    } else if (isEmptyValue(c.value)) {
                        fail(true, c, "is required unless $other is $expected.")
                    } else {
                        null
                    }
                }
            },
            "required_with" to { p ->
                val others = required(p).split(',')
                check { c ->
                    val trigger = others.any { !isEmptyValue(c.data[it]) }
                    if (trigger && isEmptyValue(c.value)) {
                        fail(true, c, "is required when ${others.joinToString(" / ")} is present.")
                    } else {
                        null
                    }
                }
            },
            "required_without" to { p ->
                val others = required(p).split(',')
                check { c ->
                    val missingOther = others.any { isEmptyValue(c.data[it]) }
                    if (!missingOther) {
                        null
                    } else if (isEmptyValue(c.value)) {
                        fail(true, c, "is required when ${others.joinToString(" / ")} is not present.")
                    } else {
                        null
                    }
                }
            },
            "required_with_all" to { p ->
                val others = required(p).split(',')
                check { c ->
                    val allPresent = others.all { !isEmptyValue(c.data[it]) }
                    if (allPresent && isEmptyValue(c.value)) {
                        fail(true, c, "is required when ${others.joinToString(" / ")} are present.")
                    } else {
                        null
                    }
                }
            },
            "required_without_all" to { p ->
                val others = required(p).split(',')
                check { c ->
                    val allMissing = others.all { isEmptyValue(c.data[it]) }
                    if (allMissing && isEmptyValue(c.value)) {
                        fail(true, c, "is required when none of ${others.joinToString(" / ")} are present.")
                    } else {
                        null
                    }
                }
            },
            "exists" to { p -> existenceRule(p, unique = false) },
            "unique" to { p -> existenceRule(p, unique = true) },
            "ulid" to { _ -> { c -> fail(!isUlid(c.value), c, "must be a valid ULID.") } },
            "mac_address" to { _ ->
                { c -> fail(!isMacAddress(c.value), c, "must be a valid MAC address.") }
            },
            "prohibited" to { _ ->
                { c ->
                    fail(!isEmptyValue(c.value), c, "is prohibited.")
                }
            },
            "prohibited_if" to { p ->
                val (other, expected) = otherAndExpected(p)
                check { c ->
                    if (!fieldEquals(c.data, other, expected)) {
                        null
                    } else {
                        fail(!isEmptyValue(c.value), c, "is prohibited when $other is $expected.")
                    }
                }
            },
            "prohibited_unless" to { p ->
                val (other, expected) = otherAndExpected(p)
                check { c ->
                    if (fieldEquals(c.data, other, expected)) {
                        null
                    } else {
                        fail(!isEmptyValue(c.value), c, "is prohibited unless $other is $expected.")
                    }
                }
            },
            "prohibits" to { p ->
                val others = required(p).split(',')
                check { c ->
                    if (isEmptyValue(c.value)) {
                        null
                    } else if (others.any { !isEmptyValue(c.data[it]) }) {
                        fail(true, c, "prohibits ${others.joinToString(" / ")} from being present.")
                    } else {
                        null
                    }
                }
            },
            "accepted_if" to { p ->
                val (other, expected) = otherAndExpected(p)
                check { c ->
                    if (!fieldEquals(c.data, other, expected)) {
                        null
                    } else if (c.value == null) {
                        fail(true, c, "must be accepted when $other is $expected.")
                    } else {
                        fail(
                            c.value.toString().trim().lowercase() !in ACCEPTED,
                            c,
                            "must be accepted when $other is $expected.",
                        )
                    }
                }
            },
            "declined_if" to { p ->
                val (other, expected) = otherAndExpected(p)
                check { c ->
                    if (!fieldEquals(c.data, other, expected)) {
                        null
                    } else if (c.value == null) {
                        fail(true, c, "must be declined when $other is $expected.")
                    } else {
                        fail(
                            c.value.toString().trim().lowercase() !in DECLINED,
                            c,
                            "must be declined when $other is $expected.",
                        )
                    }
                }
            },
            "current_password" to { _ ->
                check { c ->
                    val checker = currentPasswordChecker
                    if (checker == null) {
                        return@check "The ${c.label} field current_password rule requires a CurrentPasswordChecker."
                    }
                    if (c.value == null) {
                        return@check fail(true, c, "is incorrect.")
                    }
                    fail(!checker.check(c.value.toString()), c, "is incorrect.")
                }
            },
        )

        @JvmStatic
        fun make(data: Map<String, Any?>, rules: Map<String, List<String>>): Validator =
            Validator(data, rules)

        @JvmStatic
        fun make(request: Request, rules: Map<String, List<String>>): Validator =
            Validator(request.data(), rules)

        private fun check(body: (Context) -> String?): (Context) -> String? = body

        private fun shouldExclude(rule: ParsedRule, context: Context): Boolean {
            if (rule.name == "exclude") {
                return true
            }
            if (rule.name == "exclude_if") {
                val (other, expected) = otherAndExpected(rule.param)
                return fieldEquals(context.data, other, expected)
            }
            if (rule.name == "exclude_unless") {
                val (other, expected) = otherAndExpected(rule.param)
                return !fieldEquals(context.data, other, expected)
            }
            if (rule.name == "exclude_with") {
                val others = required(rule.param).split(',')
                return others.any { !isEmptyValue(context.data[it]) }
            }
            // exclude_without
            val others = required(rule.param).split(',')
            return others.any { isEmptyValue(context.data[it]) }
        }

        /** Parses `other,expected` rule parameters (comma required). */
        private fun otherAndExpected(param: String?): Pair<String, String> {
            val raw = required(param)
            val comma = raw.indexOf(',')
            if (comma <= 0 || comma >= raw.length - 1) {
                throw IllegalArgumentException("expected other,value")
            }
            return raw.substring(0, comma) to raw.substring(comma + 1)
        }

        private fun isUlid(value: Any?): Boolean =
            value is String && value.length == 26 && ULID.matches(value)

        private fun isMacAddress(value: Any?): Boolean =
            value is String && MAC.matches(value.trim())

        private fun split(rule: String): List<String> =
            if (rule.startsWith("regex:") || rule.startsWith("not_regex:")) {
                listOf(rule)
            } else {
                rule.split('|')
            }

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
                is UploadedFile -> (v.size / 1024.0) to Kind.FILE
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
            is UploadedFile -> value.isEmpty()
            is Collection<*> -> value.isEmpty()
            is Map<*, *> -> value.isEmpty()
            is Array<*> -> value.isEmpty()
            else -> false
        }

        private fun isImage(file: UploadedFile): Boolean {
            val mime = file.clientMimeType?.lowercase().orEmpty()
            if (mime.startsWith("image/")) {
                return true
            }
            return file.extension() in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")
        }

        private fun fileTypeRule(param: String?, useMime: Boolean): (Context) -> String? {
            val parts = required(param).split(',')
            val allowed = linkedSetOf<String>()
            for (part in parts) {
                val token = part.trim().lowercase()
                if (token.isNotEmpty()) {
                    allowed.add(token)
                }
            }
            val sep = ", "
            val types = allowed.joinToString(sep)
            return check { c ->
                val uploaded = c.value as? UploadedFile
                if (uploaded == null || !uploaded.isValid()) {
                    fail(true, c, "must be a file of type: $types.")
                } else {
                    val token = if (useMime) {
                        uploaded.clientMimeType?.lowercase()
                    } else {
                        uploaded.extension()
                    }
                    fail(token == null || !allowed.contains(token), c, "must be a file of type: $types.")
                }
            }
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

        private fun isUuid(value: Any?): Boolean {
            if (value !is String) {
                return false
            }
            return try {
                UUID.fromString(value)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }

        private fun isJson(value: Any?): Boolean = value is String && JsonSupport.isJson(value)

        private fun fieldEquals(data: Map<String, Any?>, field: String, expected: String): Boolean {
            if (!data.containsKey(field)) {
                return false
            }
            val value = data[field]
            if (value == null) {
                return expected == "null"
            }
            return value.toString() == expected
        }

        private fun isIp(value: Any?, v4: Boolean, v6: Boolean): Boolean {
            if (value !is String) {
                return false
            }
            if (value.contains(':')) {
                if (!v6) {
                    return false
                }
                return try {
                    InetAddress.getByName(value) is java.net.Inet6Address
                } catch (_: UnknownHostException) {
                    false
                }
            }
            if (!v4) {
                return false
            }
            return IPV4.matches(value)
        }

        private fun parseDate(value: Any?): LocalDate? {
            when (value) {
                is LocalDate -> return value
                is LocalDateTime -> return value.toLocalDate()
                null -> return null
            }
            val text = value.toString().trim()
            if (text.isEmpty()) {
                return null
            }
            return Request.tryParseDate(text)
        }

        private fun dateFormatter(phpOrJava: String): DateTimeFormatter {
            val pattern = phpDateToJava(phpOrJava)
            return DateTimeFormatter.ofPattern(pattern)
        }

        /**
         * Map common PHP `date()` tokens (Laravel `date_format`) to Java patterns.
         * Detect PHP style via `Y` (4-digit year) or `i` (minutes) — tokens Java patterns do not use the same way.
         */
        private fun phpDateToJava(format: String): String {
            if (!format.contains('Y') && !format.contains('i')) {
                return format
            }
            val sb = StringBuilder()
            for (ch in format) {
                when (ch) {
                    'Y' -> sb.append("yyyy")
                    'y' -> sb.append("yy")
                    'm' -> sb.append("MM")
                    'd' -> sb.append("dd")
                    'H' -> sb.append("HH")
                    'i' -> sb.append("mm")
                    's' -> sb.append("ss")
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        private fun matchesFormat(text: String, formatter: DateTimeFormatter): Boolean {
            return try {
                formatter.parse(text)
                true
            } catch (_: DateTimeParseException) {
                false
            }
        }

        private fun dateCompare(otherOrDate: String, exclusive: Boolean, after: Boolean): (Context) -> String? =
            check { c ->
                val left = parseDate(c.value) ?: return@check fail(true, c, "must be a valid date.")
                val right = parseDate(c.data[otherOrDate] ?: otherOrDate)
                    ?: return@check fail(true, c, "must be a valid date.")
                val cmp = left.compareTo(right)
                val ok = dateOrderOk(cmp, after, exclusive)
                val word = dateOrderWord(after, exclusive)
                fail(!ok, c, "must be $word $otherOrDate.")
            }

        private fun dateOrderOk(cmp: Int, after: Boolean, exclusive: Boolean): Boolean =
            if (after) {
                if (exclusive) cmp > 0 else cmp >= 0
            } else {
                if (exclusive) cmp < 0 else cmp <= 0
            }

        private fun dateOrderWord(after: Boolean, exclusive: Boolean): String =
            if (after) {
                if (exclusive) "after" else "on or after"
            } else {
                if (exclusive) "before" else "on or before"
            }

        private fun existenceRule(param: String?, unique: Boolean): (Context) -> String? {
            val parts = required(param).split(',')
            val table = parts[0].trim()
            val columnPart = if (parts.size > 1) parts[1].trim() else ""
            val column = columnPart.ifEmpty { null }
            val exceptPart = if (parts.size > 2) parts[2].trim() else ""
            val except = exceptPart.ifEmpty { null }
            return check { c ->
                val checker = existenceChecker
                if (checker == null) {
                    return@check "The ${c.label} field ${if (unique) "unique" else "exists"} rule requires an ExistenceChecker."
                }
                val col = column ?: c.field
                val found = checker.exists(table, col, c.value, except)
                if (unique) {
                    fail(found, c, "has already been taken.")
                } else {
                    fail(!found, c, "is invalid.")
                }
            }
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

/**
 * Optional DB probe for `exists` / `unique` validation rules.
 * Implement with Cadence (or any store) and register via [Validator.setExistenceChecker].
 */
fun interface ExistenceChecker {
    /**
     * @param table table or collection name from the rule
     * @param column column name (defaults to the field name when omitted in the rule)
     * @param value candidate value
     * @param exceptId optional id to ignore (unique:table,column,exceptId)
     * @return true when a matching row exists
     */
    fun exists(table: String, column: String, value: Any?, exceptId: String?): Boolean
}

/** Optional probe for the `current_password` rule (plain password → matches authenticated user). */
fun interface CurrentPasswordChecker {
    fun check(plainPassword: String): Boolean
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
