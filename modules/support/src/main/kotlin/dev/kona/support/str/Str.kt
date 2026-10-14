package dev.kona.support.str

import java.util.Locale
import java.util.UUID

/**
 * Fluent / static string helpers (Laravel `Str` shape).
 */
object Str {
    @JvmStatic
    fun of(value: String?): Stringable = Stringable(value ?: "")

    @JvmStatic
    fun lower(value: String): String = value.lowercase(Locale.ROOT)

    @JvmStatic
    fun upper(value: String): String = value.uppercase(Locale.ROOT)

    @JvmStatic
    fun title(value: String): String =
        value.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { titleCase(it) }

    @JvmStatic
    fun camel(value: String): String {
        val studly = studly(value)
        if (studly.isEmpty()) {
            return studly
        }
        return studly[0].lowercaseChar() + studly.substring(1)
    }

    @JvmStatic
    fun studly(value: String): String =
        value.split(Regex("[\\s_\\-]+"))
            .filter { it.isNotEmpty() }
            .joinToString("") { titleCase(it) }

    private fun titleCase(part: String): String =
        part[0].titlecase(Locale.ROOT) + part.substring(1).lowercase(Locale.ROOT)

    @JvmStatic
    @JvmOverloads
    fun snake(value: String, delimiter: String = "_"): String {
        val spaced = value.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        return spaced.split(Regex("[\\s_\\-]+"))
            .filter { it.isNotEmpty() }
            .joinToString(delimiter) { it.lowercase(Locale.ROOT) }
    }

    @JvmStatic
    fun kebab(value: String): String = snake(value, "-")

    @JvmStatic
    @JvmOverloads
    fun slug(value: String, separator: String = "-"): String {
        val sep = if (separator.isEmpty()) "-" else separator
        val cleaned = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), sep)
        return cleaned.trim(sep.first())
    }

    @JvmStatic
    fun limit(value: String, limit: Int, end: String = "..."): String {
        if (value.length <= limit) {
            return value
        }
        if (limit <= 0) {
            return end
        }
        return value.take(limit) + end
    }

    @JvmStatic
    fun contains(haystack: String, needle: String, ignoreCase: Boolean = false): Boolean =
        haystack.contains(needle, ignoreCase)

    @JvmStatic
    fun startsWith(value: String, prefix: String): Boolean = value.startsWith(prefix)

    @JvmStatic
    fun endsWith(value: String, suffix: String): Boolean = value.endsWith(suffix)

    @JvmStatic
    fun before(value: String, search: String): String {
        val index = value.indexOf(search)
        return if (index < 0) value else value.substring(0, index)
    }

    @JvmStatic
    fun after(value: String, search: String): String {
        val index = value.indexOf(search)
        return if (index < 0) value else value.substring(index + search.length)
    }

    @JvmStatic
    fun replace(search: String, replace: String, subject: String): String =
        subject.replace(search, replace)

    @JvmStatic
    fun random(length: Int = 16): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = java.security.SecureRandom()
        return buildString(length) {
            repeat(length.coerceAtLeast(0)) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }

    @JvmStatic
    fun uuid(): String = UUID.randomUUID().toString()

    @JvmStatic
    fun isBlank(value: String?): Boolean = value == null || value.isBlank()

    @JvmStatic
    fun isNotBlank(value: String?): Boolean = !isBlank(value)
}

/** Chainable string value returned by [Str.of]. */
class Stringable(
    private val value: String,
) {
    fun lower(): Stringable = Stringable(Str.lower(value))

    fun upper(): Stringable = Stringable(Str.upper(value))

    fun title(): Stringable = Stringable(Str.title(value))

    fun camel(): Stringable = Stringable(Str.camel(value))

    fun studly(): Stringable = Stringable(Str.studly(value))

    fun snake(delimiter: String = "_"): Stringable = Stringable(Str.snake(value, delimiter))

    fun kebab(): Stringable = Stringable(Str.kebab(value))

    fun slug(separator: String = "-"): Stringable = Stringable(Str.slug(value, separator))

    fun limit(limit: Int, end: String = "..."): Stringable = Stringable(Str.limit(value, limit, end))

    fun append(suffix: String): Stringable = Stringable(value + suffix)

    fun prepend(prefix: String): Stringable = Stringable(prefix + value)

    fun finish(cap: String): Stringable =
        if (value.endsWith(cap)) this else Stringable(value + cap)

    fun start(prefix: String): Stringable =
        if (value.startsWith(prefix)) this else Stringable(prefix + value)

    fun toStringValue(): String = value

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = other is Stringable && other.value == value

    override fun hashCode(): Int = value.hashCode()
}
