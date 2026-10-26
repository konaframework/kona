package org.konaframework.crema

/**
 * Property, attribute, and call access inside Crema expressions (`user.email`, `user.getEmail()`,
 * `user?.getEmail()`, `user !== null`).
 */
internal object CremaAccess {
    internal object NotComparison

    fun compare(expr: String, eval: (String) -> Any?): Any? {
        for (op in OPERATORS) {
            val idx = expr.indexOf(op)
            if (idx > 0) {
                val left = eval(expr.substring(0, idx))
                val right = eval(expr.substring(idx + op.length))
                return when (op) {
                    "===" -> same(left, right)
                    "!==" -> !same(left, right)
                    "==" -> "$left" == "$right"
                    "!=" -> "$left" != "$right"
                    "<" -> compareValues(left, right) < 0
                    ">" -> compareValues(left, right) > 0
                    "<=" -> compareValues(left, right) <= 0
                    else -> compareValues(left, right) >= 0
                }
            }
        }
        return NotComparison
    }

    fun read(
        expr: String,
        lookup: (String) -> Any? = { null },
        member: (Any, String) -> Any? = ::member,
        eval: (String) -> Any? = { it },
    ): Any? {
        val parts = segments(expr.trim())
        if (parts.isEmpty()) return null
        val head = parts[0]
        val headArgs = head.args
        var current = lookup(head.name)
        if (headArgs != null) {
            if (current == null) return null
            current = call(current, head.name, headArgs, eval)
        }
        for (part in parts.drop(1)) {
            if (current == null) {
                if (part.safe) return null
                return null
            }
            val args = part.args
            current = if (args != null) call(current, part.name, args, eval) else member(current, part.name)
        }
        return current
    }

    fun member(target: Any, part: String): Any? {
        if (target is Map<*, *>) return target[part]
        val cap = part.replaceFirstChar { c -> c.uppercaseChar() }
        val methods = target.javaClass.methods
        val getter = methods.firstOrNull { it.parameterCount == 0 && it.name.equals("get$cap", ignoreCase = true) }
        if (getter != null) return getter.invoke(target)
        val flag = methods.firstOrNull { it.parameterCount == 0 && it.name == "is$cap" }
        if (flag != null) return flag.invoke(target)
        val named = methods.firstOrNull { it.parameterCount == 0 && it.name == part }
        if (named != null) return named.invoke(target)
        val field = target.javaClass.fields.firstOrNull { it.name == part }
        if (field != null) return field.get(target)
        val attribute = methods.firstOrNull {
            it.name == "getAttribute" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
        }
        if (attribute != null) return attribute.invoke(target, part)
        return null
    }

    private fun call(target: Any, name: String, args: String, eval: (String) -> Any?): Any? {
        val values = CremaArguments.split(args).map(eval)
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == values.size }
            ?: return null
        return method.invoke(target, *values.toTypedArray())
    }

    private fun same(left: Any?, right: Any?): Boolean {
        if (left is Number && right is Number) return left.toDouble() == right.toDouble()
        return left == right
    }

    private fun compareValues(left: Any?, right: Any?): Int {
        if (left is Number && right is Number) return left.toDouble().compareTo(right.toDouble())
        return "$left".compareTo("$right")
    }

    private fun segments(expr: String): List<Segment> {
        if (expr.isEmpty()) return emptyList()
        val out = mutableListOf<Segment>()
        var i = 0
        var safe = false
        while (i < expr.length) {
            val start = i
            while (i < expr.length && (expr[i].isLetterOrDigit() || expr[i] == '_')) i++
            if (i == start) return emptyList()
            val name = expr.substring(start, i)
            var args: String? = null
            if (i < expr.length && expr[i] == '(') {
                val close = matchParen(expr, i)
                if (close < 0) return emptyList()
                args = expr.substring(i + 1, close)
                i = close + 1
            }
            out += Segment(name, safe, args)
            safe = false
            while (i < expr.length && expr[i].isWhitespace()) i++
            if (i >= expr.length) break
            if (expr.startsWith("?.", i)) {
                safe = true
                i += 2
            } else if (expr[i] == '.') {
                i += 1
            } else {
                return emptyList()
            }
            while (i < expr.length && expr[i].isWhitespace()) i++
        }
        return out
    }

    private fun matchParen(text: String, open: Int): Int {
        var depth = 0
        var quote: Char? = null
        var i = open
        while (i < text.length) {
            val ch = text[i]
            val q = quote
            when {
                q != null -> if (ch == q) quote = null
                ch == '\'' || ch == '"' -> quote = ch
                ch == '(' -> depth++
                ch == ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    private data class Segment(val name: String, val safe: Boolean, val args: String?)

    private val OPERATORS = listOf("===", "!==", "<=", ">=", "==", "!=", "<", ">")
}
