package org.konaframework.crema

import java.util.concurrent.ConcurrentHashMap

/**
 * Inline directive handler for `@name` or `@name(arguments)` (Blade `Blade::directive` shape).
 */
fun interface CremaDirective {
    fun render(call: CremaDirectiveCall): String
}

/**
 * Paired directive handler for `@name(arguments) … @endname`. The body is passed **unexpanded** so the
 * handler decides whether (and with which context) to render it via [CremaDirectiveCall.expand].
 */
fun interface CremaBlockDirective {
    fun render(call: CremaDirectiveCall): String
}

/**
 * Handler for prefixed tags such as `<brew:counter :count="$n" />` or `<brew:card>…</brew:card>`.
 */
fun interface CremaTagHandler {
    fun render(tag: CremaTag): String
}

/**
 * Deferred view value: resolved only when a template reads it (e.g. memoized computed properties).
 */
fun interface CremaLazy {
    fun get(): Any?
}

internal interface CremaEvaluator {
    fun evaluate(expression: String, context: CremaContext): Any?

    fun expandSource(source: String, context: CremaContext): String
}

/**
 * One directive invocation. [expression] is the raw text between the parentheses (empty when absent);
 * [body] is the raw text between a block directive's opening and `@end…` tag (empty for inline directives).
 */
class CremaDirectiveCall internal constructor(
    val name: String,
    val expression: String,
    val body: String,
    val context: CremaContext,
    private val evaluator: CremaEvaluator,
) {
    /** Evaluate a Crema expression (e.g. `$count`, `'literal'`, `$user.name`) in this call's context. */
    fun evaluate(expression: String): Any? = evaluator.evaluate(expression, context)

    /** Top-level comma-separated arguments of [expression], each evaluated. */
    fun arguments(): List<Any?> = CremaArguments.split(expression).map { evaluate(it) }

    /** Render [body] in this call's context. */
    fun expand(): String = evaluator.expandSource(body, context)

    /** Render arbitrary Crema [source] in [context]. */
    @JvmOverloads
    fun expand(source: String, context: CremaContext = this.context): String =
        evaluator.expandSource(source, context)
}

/**
 * A parsed prefixed tag. [attributes] keeps declaration order: `:name="expr"` and `:$name` are evaluated,
 * plain `name="text"` values have echoes expanded, bare `name` is `true`, and `::name` stays a literal
 * `:name` attribute. [slot] and [slots] are rendered in the **parent** context.
 */
class CremaTag internal constructor(
    val prefix: String,
    val name: String,
    val attributes: Map<String, Any?>,
    val slot: String,
    val slots: Map<String, String>,
    val selfClosing: Boolean,
    val context: CremaContext,
    private val evaluator: CremaEvaluator,
) {
    fun evaluate(expression: String): Any? = evaluator.evaluate(expression, context)

    @JvmOverloads
    fun expand(source: String, context: CremaContext = this.context): String =
        evaluator.expandSource(source, context)
}

/**
 * Registry of custom directives and tag prefixes attached to a [CremaEngine].
 */
class CremaExtensions {
    private val directives = ConcurrentHashMap<String, CremaDirective>()
    private val blocks = ConcurrentHashMap<String, CremaBlockDirective>()
    private val tags = ConcurrentHashMap<String, CremaTagHandler>()

    fun directive(name: String, handler: CremaDirective): CremaExtensions {
        directives[validName(name)] = handler
        return this
    }

    fun blockDirective(name: String, handler: CremaBlockDirective): CremaExtensions {
        blocks[validName(name)] = handler
        return this
    }

    fun tag(prefix: String, handler: CremaTagHandler): CremaExtensions {
        tags[validName(prefix)] = handler
        return this
    }

    fun hasDirective(name: String): Boolean = directives.containsKey(name)

    fun hasBlockDirective(name: String): Boolean = blocks.containsKey(name)

    fun hasTag(prefix: String): Boolean = tags.containsKey(prefix)

    internal fun directiveFor(name: String): CremaDirective? = directives[name]

    internal fun blockFor(name: String): CremaBlockDirective? = blocks[name]

    internal fun tagFor(prefix: String): CremaTagHandler? = tags[prefix]

    internal fun directiveNames(): Set<String> = directives.keys

    internal fun blockNames(): Set<String> = blocks.keys

    internal fun tagPrefixes(): Set<String> = tags.keys

    private fun validName(name: String): String {
        require(EXTENSION_NAME.matches(name)) { "Invalid Crema extension name: '$name'" }
        return name
    }
}

private val EXTENSION_NAME = Regex("[A-Za-z][A-Za-z0-9_]*")

internal object CremaArguments {
    /** Split on commas that are not nested in quotes, parentheses, brackets, or braces. */
    fun split(expression: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var quote: Char? = null
        for (ch in expression) {
            val q = quote
            when {
                q != null -> {
                    current.append(ch)
                    if (ch == q) quote = null
                }
                ch == '\'' || ch == '"' -> {
                    quote = ch
                    current.append(ch)
                }
                ch == '(' || ch == '[' || ch == '{' -> {
                    depth++
                    current.append(ch)
                }
                ch == ')' || ch == ']' || ch == '}' -> {
                    depth--
                    current.append(ch)
                }
                ch == ',' && depth == 0 -> {
                    parts += current.toString().trim()
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        val last = current.toString().trim()
        if (last.isNotEmpty() || parts.isNotEmpty()) parts += last
        return parts.filter { it.isNotEmpty() }
    }
}
