package org.konaframework.lsp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream

/**
 * Panel, resource, page, and widget names shipped with Firefly.
 * The JSON resource is generated from those classes. It is not a hand-written list.
 */
object FireflyCatalog {
    data class TypeRecord(
        val name: String,
        val packageName: String,
        val kind: String,
        val methods: List<String>,
    )

    private val mapper = ObjectMapper()

    @JvmStatic
    fun types(): List<TypeRecord> = read(FireflyCatalog::class.java.getResourceAsStream("firefly-catalog.json"))

    @JvmStatic
    fun read(stream: InputStream?): List<TypeRecord> {
        if (stream == null) {
            return emptyList()
        }
        val root = mapper.readTree(stream)
        stream.close()
        return parse(root)
    }

    @JvmStatic
    fun completions(
        text: String,
        languageId: String,
        line: Int,
        character: Int,
    ): List<CremaAnalyzer.CompletionItem> {
        if (!hostLanguage(languageId)) {
            return emptyList()
        }
        val head = head(text, line, character)
        val prefix = trailingIdent(head)
        val before = head.substring(0, head.length - prefix.length)
        val receiver = receiverName(before)
        if (receiver != null) {
            return methodItems(receiver, prefix)
        }
        if (endsWithDot(before)) {
            return emptyList()
        }
        if (prefix.isEmpty()) {
            return emptyList()
        }
        return typeItems(prefix)
    }

    @JvmStatic
    fun hover(
        text: String,
        languageId: String,
        line: Int,
        character: Int,
    ): String? {
        if (!hostLanguage(languageId)) {
            return null
        }
        val offset = CremaAnalyzer.positionToOffset(text, line, character).coerceIn(0, text.length)
        val token = identAt(text, offset)
        if (token == null) {
            return null
        }
        val before = text.substring(0, token.start)
        val receiver = receiverName(before)
        if (receiver != null) {
            val type = find(receiver)
            if (type == null) {
                return null
            }
            if (!type.methods.contains(token.text)) {
                return null
            }
            return type.packageName + "." + type.name + "." + token.text + "()"
        }
        val named = find(token.text)
        if (named == null) {
            return null
        }
        return named.packageName + "." + named.name
    }

    private fun parse(root: JsonNode): List<TypeRecord> {
        val array = root.get("types")
        if (array == null) {
            return emptyList()
        }
        if (!array.isArray) {
            return emptyList()
        }
        val out = ArrayList<TypeRecord>()
        for (node in array) {
            val name = node.path("name").asText("")
            if (name.isEmpty()) {
                continue
            }
            out.add(
                TypeRecord(
                    name = name,
                    packageName = node.path("package").asText(""),
                    kind = node.path("kind").asText(""),
                    methods = methodNames(node.get("methods")),
                ),
            )
        }
        return out
    }

    private fun methodNames(node: JsonNode?): List<String> {
        if (node == null) {
            return emptyList()
        }
        if (!node.isArray) {
            return emptyList()
        }
        val methods = ArrayList<String>()
        for (item in node) {
            val method = item.asText("")
            if (method.isEmpty()) {
                continue
            }
            methods.add(method)
        }
        return methods
    }

    private fun hostLanguage(languageId: String): Boolean {
        val lang = languageId.lowercase()
        if (lang == "kotlin") {
            return true
        }
        if (lang == "java") {
            return true
        }
        if (lang == "kotlinscript") {
            return true
        }
        return false
    }

    private fun head(text: String, line: Int, character: Int): String {
        val offset = CremaAnalyzer.positionToOffset(text, line, character).coerceIn(0, text.length)
        return text.substring(0, offset)
    }

    private fun typeItems(prefix: String): List<CremaAnalyzer.CompletionItem> {
        val needle = prefix.lowercase()
        val items = ArrayList<CremaAnalyzer.CompletionItem>()
        for (type in types()) {
            if (type.name.lowercase().startsWith(needle)) {
                items.add(
                    CremaAnalyzer.CompletionItem(
                        label = type.name,
                        kind = 7,
                        detail = type.packageName + " · " + type.kind,
                        insertText = type.name,
                    ),
                )
            }
        }
        return items
    }

    private fun methodItems(receiver: String, prefix: String): List<CremaAnalyzer.CompletionItem> {
        val type = find(receiver)
        if (type == null) {
            return emptyList()
        }
        val needle = prefix.lowercase()
        val items = ArrayList<CremaAnalyzer.CompletionItem>()
        for (method in type.methods) {
            val matches =
                if (needle.isEmpty()) {
                    true
                } else {
                    method.lowercase().startsWith(needle)
                }
            if (matches) {
                items.add(
                    CremaAnalyzer.CompletionItem(
                        label = method,
                        kind = 2,
                        detail = type.name + "." + method + "()",
                        insertText = method,
                    ),
                )
            }
        }
        return items
    }

    private fun find(name: String): TypeRecord? {
        for (type in types()) {
            if (type.name == name) {
                return type
            }
        }
        return null
    }

    private fun receiverName(before: String): String? {
        val trimmed = trimTail(before)
        if (trimmed.isEmpty()) {
            return null
        }
        if (trimmed[trimmed.length - 1] != '.') {
            return null
        }
        var left = trimTail(trimmed.substring(0, trimmed.length - 1))
        if (left.isNotEmpty() && left[left.length - 1] == ')') {
            val cut = skipCall(left)
            if (cut < 0) {
                return null
            }
            left = left.substring(0, cut)
        }
        val name = trailingIdent(left)
        if (name.isEmpty()) {
            return null
        }
        return name
    }

    private fun skipCall(value: String): Int {
        var depth = 0
        var index = value.length - 1
        while (index >= 0) {
            val ch = value[index]
            if (ch == ')') {
                depth = depth + 1
            } else if (ch == '(') {
                depth = depth - 1
                if (depth == 0) {
                    return index
                }
            }
            index = index - 1
        }
        return -1
    }

    private fun endsWithDot(before: String): Boolean {
        val trimmed = trimTail(before)
        if (trimmed.isEmpty()) {
            return false
        }
        return trimmed[trimmed.length - 1] == '.'
    }

    private fun identAt(text: String, offset: Int): Token? {
        if (text.isEmpty()) {
            return null
        }
        val index = offset.coerceIn(0, text.length - 1)
        if (!ident(text[index])) {
            return null
        }
        var start = index
        while (start > 0 && ident(text[start - 1])) {
            start = start - 1
        }
        var end = index + 1
        while (end < text.length && ident(text[end])) {
            end = end + 1
        }
        return Token(start, text.substring(start, end))
    }

    private fun trailingIdent(value: String): String {
        var index = value.length
        while (index > 0 && ident(value[index - 1])) {
            index = index - 1
        }
        return value.substring(index)
    }

    private fun trimTail(value: String): String {
        var end = value.length
        while (end > 0) {
            val ch = value[end - 1]
            if (ch != ' ' && ch != '\n' && ch != '\t') {
                break
            }
            end = end - 1
        }
        return value.substring(0, end)
    }

    private fun ident(ch: Char): Boolean {
        if (ch >= 'a' && ch <= 'z') {
            return true
        }
        if (ch >= 'A' && ch <= 'Z') {
            return true
        }
        if (ch >= '0' && ch <= '9') {
            return true
        }
        return ch == '_'
    }

    private data class Token(val start: Int, val text: String)
}
