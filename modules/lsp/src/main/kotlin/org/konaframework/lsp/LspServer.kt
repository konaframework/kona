package org.konaframework.lsp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal Crema language server over LSP JSON-RPC (stdio).
 *
 * Supports initialize / initialized / shutdown / exit, document sync,
 * completion for Crema directives and Brew `wire:*` attributes, and
 * publishDiagnostics for unknown `@name`s / `wire:*` attributes.
 */
class LspServer {
    private data class OpenDocument(val text: String, val languageId: String)

    private val documents = linkedMapOf<String, OpenDocument>()
    private var shutdownRequested = false

    /**
     * Serve until `exit`. Returns 0 after a prior `shutdown`, otherwise 1.
     */
    fun serve(input: InputStream, output: OutputStream): Int {
        val transport = JsonRpcTransport(input, output)
        while (true) {
            val message =
                try {
                    transport.readMessage()
                } catch (ex: LspException) {
                    transport.writeError(null, PARSE_ERROR, messageOf(ex))
                    continue
                } catch (ex: Exception) {
                    transport.writeError(null, PARSE_ERROR, messageOf(ex))
                    continue
                }
            if (message == null) {
                return if (shutdownRequested) 0 else 1
            }
            val methodNode = message.get("method")
            if (methodNode == null) {
                continue
            }
            if (methodNode.isNull) {
                continue
            }
            val method = methodNode.asText()
            val id = message.get("id")
            if (method == "exit") {
                return if (shutdownRequested) 0 else 1
            }
            handle(transport, method, id, message.get("params"))
        }
    }

    private fun messageOf(ex: Exception): String {
        val msg = ex.message
        if (msg == null) {
            return "parse error"
        }
        return msg
    }

    private fun handle(
        transport: JsonRpcTransport,
        method: String,
        id: JsonNode?,
        params: JsonNode?,
    ) {
        when (method) {
            "initialize" -> {
                if (!hasRequestId(id)) {
                    return
                }
                transport.writeResponse(id, initializeResult(transport))
            }
            "initialized" -> {
                // notification — no reply
            }
            "shutdown" -> {
                if (!hasRequestId(id)) {
                    return
                }
                shutdownRequested = true
                transport.writeResponse(id, null)
            }
            "textDocument/didOpen" -> didOpen(transport, params)
            "textDocument/didChange" -> didChange(transport, params)
            "textDocument/didClose" -> didClose(transport, params)
            "textDocument/completion" -> {
                if (!hasRequestId(id)) {
                    return
                }
                transport.writeResponse(id, completionResult(transport, params))
            }
            "textDocument/diagnostic" -> {
                if (!hasRequestId(id)) {
                    return
                }
                transport.writeResponse(id, diagnosticReport(transport, params))
            }
            else -> {
                if (hasRequestId(id)) {
                    transport.writeError(id, METHOD_NOT_FOUND, "Method not found: $method")
                }
            }
        }
    }

    private fun didOpen(transport: JsonRpcTransport, params: JsonNode?) {
        if (params == null) {
            return
        }
        val doc = params.get("textDocument") ?: return
        val uri = doc.path("uri").asText("")
        if (uri.isEmpty()) {
            return
        }
        val text = doc.path("text").asText("")
        val languageId = doc.path("languageId").asText("")
        documents[uri] = OpenDocument(text, languageId)
        publishDiagnostics(transport, uri, text, languageId)
    }

    private fun didChange(transport: JsonRpcTransport, params: JsonNode?) {
        if (params == null) {
            return
        }
        val uri = textDocumentUri(params)
        if (uri.isEmpty()) {
            return
        }
        val changes = params.get("contentChanges")
        if (changes !is ArrayNode) {
            return
        }
        if (changes.size() == 0) {
            return
        }
        val last = changes[changes.size() - 1]
        val previous = documents[uri]
        val text: String
        val languageId: String
        if (previous == null) {
            text = last.path("text").asText("")
            languageId = ""
        } else {
            text = last.path("text").asText(previous.text)
            languageId = previous.languageId
        }
        documents[uri] = OpenDocument(text, languageId)
        publishDiagnostics(transport, uri, text, languageId)
    }

    private fun didClose(transport: JsonRpcTransport, params: JsonNode?) {
        if (params == null) {
            return
        }
        val uri = textDocumentUri(params)
        if (uri.isEmpty()) {
            return
        }
        documents.remove(uri)
        publishDiagnostics(transport, uri, "", "")
    }

    private fun textDocumentUri(params: JsonNode): String {
        val doc = params.get("textDocument") ?: return ""
        return doc.path("uri").asText("")
    }

    private fun completionResult(transport: JsonRpcTransport, params: JsonNode?): ObjectNode {
        val uri: String
        val line: Int
        val character: Int
        if (params == null) {
            uri = ""
            line = 0
            character = 0
        } else {
            uri = textDocumentUri(params)
            val position = params.get("position")
            if (position == null) {
                line = 0
                character = 0
            } else {
                line = position.path("line").asInt(0)
                character = position.path("character").asInt(0)
            }
        }
        val stored = documents[uri]
        val text: String
        val languageId: String
        if (stored == null) {
            text = ""
            languageId = ""
        } else {
            text = stored.text
            languageId = stored.languageId
        }
        return completionItemsNode(
            transport,
            HostDocument.completions(text, languageId, uri, line, character),
        )
    }

    private fun hasRequestId(id: JsonNode?): Boolean {
        if (id == null) {
            return false
        }
        if (id.isNull) {
            return false
        }
        return true
    }

    private fun initializeResult(transport: JsonRpcTransport): ObjectNode {
        val result = transport.objectNode()
        val caps = transport.objectNode()
        caps.put("textDocumentSync", 1) // Full
        caps.set<ObjectNode>(
            "completionProvider",
            transport.objectNode().apply {
                putArray("triggerCharacters").add("@").add(":").add(".").add("{")
                put("resolveProvider", false)
            },
        )
        caps.set<ObjectNode>(
            "diagnosticProvider",
            transport.objectNode().apply {
                put("interFileDependencies", false)
                put("workspaceDiagnostics", false)
            },
        )
        result.set<ObjectNode>("capabilities", caps)
        result.set<ObjectNode>(
            "serverInfo",
            transport.objectNode().apply {
                put("name", "kona-lsp")
                put("version", "0.1.0")
            },
        )
        return result
    }

    private fun completionItemsNode(
        transport: JsonRpcTransport,
        items: List<CremaAnalyzer.CompletionItem>,
    ): ObjectNode {
        val result = transport.objectNode()
        val arr = result.putArray("items")
        items.forEach { item ->
            val node = transport.objectNode()
            node.put("label", item.label)
            node.put("kind", item.kind)
            node.put("detail", item.detail)
            node.put("insertText", item.insertText)
            arr.add(node)
        }
        result.put("isIncomplete", false)
        return result
    }

    private fun diagnosticReport(transport: JsonRpcTransport, params: JsonNode?): ObjectNode {
        val uri: String
        if (params == null) {
            uri = ""
        } else {
            uri = textDocumentUri(params)
        }
        val stored = documents[uri]
        val text: String
        val languageId: String
        if (stored == null) {
            text = ""
            languageId = ""
        } else {
            text = stored.text
            languageId = stored.languageId
        }
        val result = transport.objectNode()
        result.put("kind", "full")
        val arr = result.putArray("items")
        HostDocument.diagnostics(text, languageId, uri).forEach { d ->
            arr.add(diagnosticNode(transport, d))
        }
        return result
    }

    private fun publishDiagnostics(
        transport: JsonRpcTransport,
        uri: String,
        text: String,
        languageId: String,
    ) {
        val params = transport.objectNode()
        params.put("uri", uri)
        val arr = params.putArray("diagnostics")
        HostDocument.diagnostics(text, languageId, uri).forEach { d ->
            arr.add(diagnosticNode(transport, d))
        }
        transport.writeNotification("textDocument/publishDiagnostics", params)
    }

    private fun diagnosticNode(
        transport: JsonRpcTransport,
        d: CremaAnalyzer.Diagnostic,
    ): ObjectNode {
        val node = transport.objectNode()
        val range = transport.objectNode()
        val start = transport.objectNode()
        start.put("line", d.range.start.line)
        start.put("character", d.range.start.character)
        val end = transport.objectNode()
        end.put("line", d.range.end.line)
        end.put("character", d.range.end.character)
        range.set<ObjectNode>("start", start)
        range.set<ObjectNode>("end", end)
        node.set<ObjectNode>("range", range)
        node.put("severity", d.severity)
        node.put("source", d.source)
        node.put("message", d.message)
        return node
    }

    companion object {
        const val PARSE_ERROR = -32700
        const val METHOD_NOT_FOUND = -32601
    }
}
