package org.konaframework.lsp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * LSP message framing: `Content-Length` header + JSON body (UTF-8).
 */
class JsonRpcTransport(
    input: InputStream,
    output: OutputStream,
    private val mapper: ObjectMapper = defaultMapper(),
) {
    private val input = BufferedInputStream(input)
    private val output = BufferedOutputStream(output)

    fun readMessage(): JsonNode? {
        val headers = readHeaders() ?: return null
        val lengthHeader = headers["Content-Length"]
        if (lengthHeader == null) {
            throw LspException("Missing or invalid Content-Length")
        }
        val length = lengthHeader.toIntOrNull()
        if (length == null) {
            throw LspException("Missing or invalid Content-Length")
        }
        if (length < 0) {
            throw LspException("Negative Content-Length")
        }
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) {
                throw LspException("Unexpected EOF reading message body")
            }
            read += n
        }
        return mapper.readTree(body)
    }

    fun writeMessage(node: JsonNode) {
        val body = mapper.writeValueAsBytes(node)
        val header =
            "Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
        synchronized(output) {
            output.write(header)
            output.write(body)
            output.flush()
        }
    }

    fun writeResponse(id: JsonNode?, result: JsonNode?) {
        val node = mapper.createObjectNode()
        node.put("jsonrpc", "2.0")
        setId(node, id)
        if (result == null) {
            node.putNull("result")
        } else {
            node.set<JsonNode>("result", result)
        }
        writeMessage(node)
    }

    fun writeError(id: JsonNode?, code: Int, message: String) {
        val node = mapper.createObjectNode()
        node.put("jsonrpc", "2.0")
        setId(node, id)
        val error = mapper.createObjectNode()
        error.put("code", code)
        error.put("message", message)
        node.set<ObjectNode>("error", error)
        writeMessage(node)
    }

    fun writeNotification(method: String, params: JsonNode) {
        val node = mapper.createObjectNode()
        node.put("jsonrpc", "2.0")
        node.put("method", method)
        node.set<JsonNode>("params", params)
        writeMessage(node)
    }

    fun objectNode(): ObjectNode = mapper.createObjectNode()

    private fun setId(node: ObjectNode, id: JsonNode?) {
        if (id == null) {
            node.putNull("id")
            return
        }
        if (id.isNull) {
            node.putNull("id")
            return
        }
        node.set<JsonNode>("id", id)
    }

    private fun readHeaders(): Map<String, String>? {
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readLineBytes() ?: return if (headers.isEmpty()) null else headers
            if (line.isEmpty()) {
                return headers
            }
            val sep = line.indexOf(':')
            if (sep <= 0) {
                throw LspException("Malformed header: $line")
            }
            val name = line.substring(0, sep).trim()
            val value = line.substring(sep + 1).trim()
            headers[name] = value
        }
    }

    private fun readLineBytes(): String? {
        val buffer = ArrayList<Byte>(64)
        while (true) {
            val b = input.read()
            if (b < 0) {
                if (buffer.isEmpty()) {
                    return null
                }
                return String(buffer.toByteArray(), StandardCharsets.UTF_8)
            }
            if (b == '\n'.code) {
                stripTrailingCr(buffer)
                return String(buffer.toByteArray(), StandardCharsets.UTF_8)
            }
            buffer.add(b.toByte())
        }
    }

    private fun stripTrailingCr(buffer: ArrayList<Byte>) {
        if (buffer.isEmpty()) {
            return
        }
        if (buffer.last() == '\r'.code.toByte()) {
            buffer.removeAt(buffer.lastIndex)
        }
    }

    companion object {
        fun defaultMapper(): ObjectMapper =
            ObjectMapper().registerModule(kotlinModule())
    }
}

class LspException(message: String) : RuntimeException(message)
