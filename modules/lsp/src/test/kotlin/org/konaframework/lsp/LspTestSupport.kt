package org.konaframework.lsp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

internal object LspTestSupport {
    val mapper: ObjectMapper = ObjectMapper().registerModule(kotlinModule())

    fun frame(json: String): ByteArray {
        val body = json.toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
        return header + body
    }

    fun frame(node: JsonNode): ByteArray = frame(mapper.writeValueAsString(node))

    fun request(id: Int, method: String, params: JsonNode? = null): ByteArray {
        val node = mapper.createObjectNode()
        node.put("jsonrpc", "2.0")
        node.put("id", id)
        node.put("method", method)
        if (params != null) {
            node.set<JsonNode>("params", params)
        }
        return frame(node)
    }

    fun notification(method: String, params: JsonNode? = null): ByteArray {
        val node = mapper.createObjectNode()
        node.put("jsonrpc", "2.0")
        node.put("method", method)
        if (params != null) {
            node.set<JsonNode>("params", params)
        }
        return frame(node)
    }

    fun runSession(vararg chunks: ByteArray): Pair<Int, List<JsonNode>> {
        val input = ByteArrayInputStream(chunks.fold(ByteArray(0)) { acc, b -> acc + b })
        val output = ByteArrayOutputStream()
        val code = LspServer().serve(input, output)
        return code to parseMessages(output.toByteArray())
    }

    fun parseMessages(bytes: ByteArray): List<JsonNode> {
        val transport = JsonRpcTransport(ByteArrayInputStream(bytes), ByteArrayOutputStream())
        val out = mutableListOf<JsonNode>()
        while (true) {
            val msg = transport.readMessage() ?: break
            out.add(msg)
        }
        return out
    }

    fun initializeParams(): JsonNode {
        val params = mapper.createObjectNode()
        params.put("processId", 1)
        params.set<JsonNode>("capabilities", mapper.createObjectNode())
        params.putNull("rootUri")
        return params
    }
}
