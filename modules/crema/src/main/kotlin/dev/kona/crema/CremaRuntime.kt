package dev.kona.crema

/**
 * Crema template runtime — directives and echoes for `*.crema.html`.
 * Kept in one file so the behavioral suite can drive 100% line/branch coverage.
 */
internal class CremaRuntime(private val finder: ViewFinder) {
    fun render(name: String, data: Map<String, Any?>): String =
        renderSource(finder.get(name), CremaContext().merge(data))

    fun render(name: String, context: CremaContext): String =
        renderSource(finder.get(name), context)

    fun renderSource(source: String, context: CremaContext): String {
        val cleaned = COMMENT.replace(source, "")
        val parent = EXTENDS.find(cleaned)
        val body = stripAndCollect(cleaned, context)
        return if (parent != null) {
            render(parent.groupValues[1], context)
        } else {
            expand(body, context)
        }
    }

    private fun stripAndCollect(source: String, context: CremaContext): String {
        var rest = EXTENDS.replace(source, "")
        rest = INLINE_SECTION.replace(rest) { m ->
            putSection(context, m.groupValues[1], m.groupValues[2])
            ""
        }
        while (true) {
            val m = OPEN_SECTION.find(rest) ?: break
            val name = m.groupValues[1]
            val start = m.range.last + 1
                val endSection = rest.indexOf("@endsection", start)
                val endShow = rest.indexOf("@show", start)
                val endIdx = when {
                    endSection >= 0 && (endShow < 0 || endSection < endShow) -> endSection
                    endShow >= 0 -> endShow
                    else -> -1
                }
            require(endIdx >= 0) { "Missing @endsection for $name" }
            val closer = if (rest.startsWith("@show", endIdx)) "@show" else "@endsection"
            val raw = rest.substring(start, endIdx)
            val content = if (closer == "@show") expand(raw, context) else raw
            putSection(context, name, content)
            rest = rest.removeRange(m.range.first, endIdx + closer.length)
        }
        return rest
    }

    private fun putSection(context: CremaContext, name: String, content: String) {
        val section = context.section(name)
        if (section.isEmpty()) {
            section.append(content)
        } else {
            section.setParent(content)
        }
    }

    private fun expand(source: String, context: CremaContext): String {
        var text = source
        text = expandBlocks(text, context)
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("{!!", i) -> {
                    val end = text.indexOf("!!}", i + 3)
                    require(end >= 0) { "Unclosed raw echo" }
                    out.append(value(text.substring(i + 3, end), context))
                    i = end + 3
                }
                text.startsWith("{{", i) -> {
                    val end = text.indexOf("}}", i + 2)
                    require(end >= 0) { "Unclosed echo" }
                    out.append(escape(value(text.substring(i + 2, end), context)))
                    i = end + 2
                }
                text.startsWith("@@", i) -> {
                    out.append('@')
                    i += 2
                }
                else -> {
                    out.append(text[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun expandBlocks(source: String, context: CremaContext): String {
        var text = source
        text = replaceDirective(text, "@csrf") {
            val token = context.get("_token")?.toString() ?: ""
            "<input type=\"hidden\" name=\"_token\" value=\"${escape(token)}\">"
        }
        text = replacePush(text, context)
        text = replaceYield(text, context)
        text = replaceInclude(text, context)
        text = replaceIf(text, context)
        text = replaceUnless(text, context)
        text = replaceForeach(text, context)
        text = replaceForelse(text, context)
        text = replaceComponent(text, context)
        text = replaceStack(text, context)
        return text
    }

    private fun replaceYield(source: String, context: CremaContext): String =
        YIELD.replace(source) { m ->
            val name = m.groupValues[1]
            val default = m.groupValues[2].ifEmpty { null }
            val section = context.section(name)
            when {
                !section.isEmpty() -> expand(section.render(), context)
                default != null -> default
                else -> ""
            }
        }

    private fun replaceStack(source: String, context: CremaContext): String =
        STACK.replace(source) { m -> context.stack(m.groupValues[1]) }

    private fun replaceInclude(source: String, context: CremaContext): String {
        var text = INCLUDE_IF.replace(source) { m ->
            runCatching { render(m.groupValues[1], context.child()) }.getOrDefault("")
        }
        text = INCLUDE_WHEN.replace(text) { m ->
            if (truthy(eval(m.groupValues[1], context))) {
                render(m.groupValues[2], context.child())
            } else {
                ""
            }
        }
        return INCLUDE.replace(text) { m ->
            render(m.groupValues[1], context.child())
        }
    }

    private fun replaceIf(source: String, context: CremaContext): String {
        var text = source
        while (true) {
            val m = IF.find(text) ?: break
            val cond = m.groupValues[1]
            val body = m.groupValues[2]
            val elseBody = m.groupValues[3]
            val replacement = if (truthy(eval(cond, context))) {
                expand(body, context)
            } else if (elseBody.isNotEmpty()) {
                expand(elseBody.removePrefix("@else"), context)
            } else {
                ""
            }
            text = text.replaceRange(m.range, replacement)
        }
        return text
    }

    private fun replaceUnless(source: String, context: CremaContext): String =
        UNLESS.replace(source) { m ->
            if (!truthy(eval(m.groupValues[1], context))) expand(m.groupValues[2], context) else ""
        }

    private fun replaceForeach(source: String, context: CremaContext): String {
        var text = source
        for (pattern in listOf(FOREACH, EACH, FOR)) {
            text = pattern.replace(text) { m ->
                val items = iterate(eval(m.groupValues[1], context))
                val itemName = m.groupValues[2]
                val body = m.groupValues[3]
                buildString {
                    items.forEachIndexed { idx, item ->
                        val child = context.child(
                            mapOf(itemName to item, "loop" to mapOf(
                                "index" to idx,
                                "iteration" to idx + 1,
                                "first" to (idx == 0),
                                "last" to (idx == items.lastIndex),
                                "count" to items.size,
                                "remaining" to (items.size - idx - 1),
                            )),
                        )
                        append(expand(body, child))
                    }
                }
            }
        }
        return text
    }

    private fun replaceForelse(source: String, context: CremaContext): String =
        FORELSE.replace(source) { m ->
            val items = iterate(eval(m.groupValues[1], context))
            val itemName = m.groupValues[2]
            val body = m.groupValues[3]
            val empty = m.groupValues[4]
            if (items.isEmpty()) {
                expand(empty, context)
            } else {
                buildString {
                    items.forEachIndexed { idx, item ->
                        append(expand(body, context.child(mapOf(itemName to item, "loop" to mapOf("iteration" to idx + 1)))))
                    }
                }
            }
        }

    private fun replacePush(source: String, context: CremaContext): String {
        var text = PUSH.replace(source) { m ->
            context.push(m.groupValues[1], expand(m.groupValues[2], context))
            ""
        }
        text = PREPEND.replace(text) { m ->
            context.prepend(m.groupValues[1], expand(m.groupValues[2], context))
            ""
        }
        return text
    }

    private fun replaceComponent(source: String, context: CremaContext): String =
        COMPONENT.replace(source) { m ->
            val name = m.groupValues[1]
            val body = m.groupValues[2]
            var slotRest = body
            val slots = linkedMapOf<String, String>()
            while (true) {
                val slot = SLOT.find(slotRest) ?: break
                slots[slot.groupValues[1]] = expand(slot.groupValues[2], context)
                slotRest = slotRest.removeRange(slot.range)
            }
            val child = context.child(mapOf("slot" to expand(slotRest, context)) + slots)
            render(name, child)
        }

    private fun replaceDirective(source: String, tag: String, block: () -> String): String =
        if (source.contains(tag)) source.replace(tag, block()) else source

    private fun value(expr: String, context: CremaContext): String =
        eval(expr, context)?.toString() ?: ""

    private fun eval(raw: String, context: CremaContext): Any? {
        val expr = raw.trim().removePrefix("$")
        if (expr.isEmpty()) return null
        when {
            expr == "null" || expr == "nil" -> return null
            expr == "true" -> return true
            expr == "false" -> return false
            expr.toIntOrNull() != null -> return expr.toInt()
            expr.toDoubleOrNull() != null -> return expr.toDouble()
            expr.length >= 2 && expr.first() == '\'' && expr.last() == '\'' ->
                return expr.substring(1, expr.lastIndex)
            expr.length >= 2 && expr.first() == '"' && expr.last() == '"' ->
                return expr.substring(1, expr.lastIndex)
        }
        if (" and " in expr) return expr.split(" and ").all { truthy(eval(it, context)) }
        if (" or " in expr) return expr.split(" or ").any { truthy(eval(it, context)) }
        if (expr.startsWith("not ")) return !truthy(eval(expr.removePrefix("not "), context))
        if (expr.startsWith("!")) return !truthy(eval(expr.removePrefix("!"), context))
        if (expr.startsWith("empty(") && expr.endsWith(")")) {
            return !truthy(eval(expr.removePrefix("empty(").removeSuffix(")"), context))
        }
        if (expr.startsWith("isset(") && expr.endsWith(")")) {
            return resolve(expr.removePrefix("isset(").removeSuffix(")"), context) != null
        }
        for (op in listOf("==", "!=", "<=", ">=", "<", ">")) {
            val idx = expr.indexOf(op)
            if (idx > 0) {
                val left = eval(expr.substring(0, idx), context)
                val right = eval(expr.substring(idx + op.length), context)
                return when (op) {
                    "==" -> "$left" == "$right"
                    "!=" -> "$left" != "$right"
                    "<" -> "$left" < "$right"
                    ">" -> "$left" > "$right"
                    "<=" -> "$left" <= "$right"
                    else -> "$left" >= "$right"
                }
            }
        }
        return resolve(expr, context)
    }

    private fun resolve(path: String, context: CremaContext): Any? {
        val parts = path.trim().split('.')
        var current: Any? = context.get(parts[0])
        for (part in parts.drop(1)) {
            current = when (current) {
                null -> return null
                is Map<*, *> -> current[part]
                else -> {
                    val getter = current.javaClass.methods.firstOrNull {
                        it.parameterCount == 0 && it.name.equals("get${part.replaceFirstChar { c -> c.uppercaseChar() }}", true)
                    }
                    getter?.invoke(current)
                }
            }
        }
        return current
    }

    private fun iterate(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is Collection<*> -> value.toList()
        is Array<*> -> value.toList()
        is IntRange -> value.toList()
        is CharSequence -> value.toList()
        else -> listOf(value)
    }

    private fun truthy(value: Any?): Boolean = when (value) {
        null, false -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is CharSequence -> value.isNotEmpty()
        is Collection<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        is Array<*> -> value.isNotEmpty()
        else -> true
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")

    private companion object {
        val COMMENT = Regex("""\{\{--[\s\S]*?--\}\}""")
        val EXTENDS = Regex("""@extends\(\s*'([^']+)'\s*\)""")
        val INLINE_SECTION = Regex("""@section\(\s*'([^']+)'\s*,\s*'([^']+)'\s*\)""")
        val OPEN_SECTION = Regex("""@section\(\s*'([^']+)'\s*\)""")
        val YIELD = Regex("""@yield\(\s*'([^']+)'\s*(?:,\s*'([^']*)'\s*)?\)""")
        val STACK = Regex("""@stack\(\s*'([^']+)'\s*\)""")
        val INCLUDE = Regex("""@include\(\s*'([^']+)'\s*\)""")
        val INCLUDE_IF = Regex("""@includeIf\(\s*'([^']+)'\s*\)""")
        val INCLUDE_WHEN = Regex("""@includeWhen\(\s*([^,]+)\s*,\s*'([^']+)'\s*\)""")
        val IF = Regex("""@if\(([^)]+)\)([\s\S]*?)(?:@else([\s\S]*?))?@endif""")
        val UNLESS = Regex("""@unless\(([^)]+)\)([\s\S]*?)@endunless""")
        val FOREACH = Regex("""@foreach\((.+?)\s+as\s+(\w+)\)([\s\S]*?)@endforeach""")
        val EACH = Regex("""@each\((.+?)\s+as\s+(\w+)\)([\s\S]*?)@endeach""")
        val FOR = Regex("""@for\((.+?)\s+as\s+(\w+)\)([\s\S]*?)@endfor""")
        val FORELSE = Regex("""@forelse\((.+?)\s+as\s+(\w+)\)([\s\S]*?)@empty([\s\S]*?)@endforelse""")
        val PUSH = Regex("""@push\(\s*'([^']+)'\s*\)([\s\S]*?)@endpush""")
        val PREPEND = Regex("""@prepend\(\s*'([^']+)'\s*\)([\s\S]*?)@endprepend""")
        val COMPONENT = Regex("""@component\(\s*'([^']+)'\s*\)([\s\S]*?)@endcomponent""")
        val SLOT = Regex("""@slot\(\s*'([^']+)'\s*\)([\s\S]*?)@endslot""")
    }
}
