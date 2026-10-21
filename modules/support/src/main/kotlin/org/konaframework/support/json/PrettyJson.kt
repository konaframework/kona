package org.konaframework.support.json

/**
 * 2-space JSON with `": "` (npm / Prettier shape). Used for scaffolded
 * `package.json` / `tsconfig.json` so nested maps cannot inherit a broken
 * `trimIndent()` indent from the surrounding Kotlin string.
 */
object PrettyJson {
    @JvmStatic
    fun stringify(value: Any?): String {
        val out = StringBuilder()
        write(out, value, 0)
        out.append('\n')
        return out.toString()
    }

    private fun write(out: StringBuilder, value: Any?, indent: Int) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(value)
            is Number -> out.append(value)
            is String -> out.append(quote(value))
            is Map<*, *> -> writeMap(out, value, indent)
            is Iterable<*> -> writeList(out, value.toList(), indent)
            is Array<*> -> writeList(out, value.toList(), indent)
            else -> out.append(quote(value.toString()))
        }
    }

    private fun writeMap(out: StringBuilder, value: Map<*, *>, indent: Int) {
        if (value.isEmpty()) {
            out.append("{}")
            return
        }
        out.append("{\n")
        val entries = value.entries.toList()
        entries.forEachIndexed { index, (key, nested) ->
            pad(out, indent + 1)
            out.append(quote(key.toString()))
            out.append(": ")
            write(out, nested, indent + 1)
            if (index < entries.size - 1) {
                out.append(',')
            }
            out.append('\n')
        }
        pad(out, indent)
        out.append('}')
    }

    private fun writeList(out: StringBuilder, value: List<*>, indent: Int) {
        if (value.isEmpty()) {
            out.append("[]")
            return
        }
        out.append("[\n")
        value.forEachIndexed { index, nested ->
            pad(out, indent + 1)
            write(out, nested, indent + 1)
            if (index < value.size - 1) {
                out.append(',')
            }
            out.append('\n')
        }
        pad(out, indent)
        out.append(']')
    }

    private fun pad(out: StringBuilder, indent: Int) {
        repeat(indent) { out.append("  ") }
    }

    private fun quote(raw: String): String {
        val out = StringBuilder(raw.length + 2)
        out.append('"')
        raw.forEach { ch ->
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else ->
                    if (ch < ' ') {
                        out.append("\\u")
                        out.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(ch)
                    }
            }
        }
        out.append('"')
        return out.toString()
    }
}
