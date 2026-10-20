package org.konaframework.lsp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LspServerTest {
    @Test
    fun initializeShutdownExitLifecycle() {
        val (code, messages) =
            LspTestSupport.runSession(
                LspTestSupport.request(1, "initialize", LspTestSupport.initializeParams()),
                LspTestSupport.notification("initialized"),
                LspTestSupport.request(2, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        val init = messages.first { it.has("id") && it.get("id").asInt() == 1 }
        assertEquals("kona-lsp", init.path("result").path("serverInfo").path("name").asText())
        assertTrue(init.path("result").path("capabilities").has("completionProvider"))
        assertTrue(init.path("result").path("capabilities").has("diagnosticProvider"))
        val triggers =
            init.path("result").path("capabilities").path("completionProvider").path("triggerCharacters")
        assertTrue(triggers.toString().contains("@"))
        assertTrue(triggers.toString().contains(":"))
        assertTrue(triggers.toString().contains("."))
        assertTrue(triggers.toString().contains("{"))
        val shutdown = messages.first { it.has("id") && it.get("id").asInt() == 2 }
        assertTrue(shutdown.has("result"))
        assertTrue(shutdown.get("result").isNull)
    }

    @Test
    fun exitWithoutShutdownReturnsOne() {
        val (code, _) =
            LspTestSupport.runSession(
                LspTestSupport.notification("exit"),
            )
        assertEquals(1, code)
    }

    @Test
    fun eofWithoutShutdownReturnsOne() {
        val (code, _) = LspTestSupport.runSession()
        assertEquals(1, code)
    }

    @Test
    fun eofAfterShutdownReturnsZero() {
        val (code, _) =
            LspTestSupport.runSession(
                LspTestSupport.request(1, "shutdown"),
            )
        assertEquals(0, code)
    }

    @Test
    fun didOpenPublishesDiagnosticsAndCompletionWorks() {
        val openParams = LspTestSupport.mapper.createObjectNode()
        val doc = LspTestSupport.mapper.createObjectNode()
        doc.put("uri", "file:///tmp/x.crema.html")
        doc.put("languageId", "crema")
        doc.put("version", 1)
        doc.put("text", "@extends('a')\n@notARealDirective\n<button wire:cli")
        openParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", doc)

        val completionParams = LspTestSupport.mapper.createObjectNode()
        val td = LspTestSupport.mapper.createObjectNode()
        td.put("uri", "file:///tmp/x.crema.html")
        completionParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", td)
        val pos = LspTestSupport.mapper.createObjectNode()
        pos.put("line", 0)
        pos.put("character", 1)
        completionParams.set<com.fasterxml.jackson.databind.JsonNode>("position", pos)

        val wireCompletionParams = LspTestSupport.mapper.createObjectNode()
        wireCompletionParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", td)
        val wirePos = LspTestSupport.mapper.createObjectNode()
        wirePos.put("line", 2)
        wirePos.put("character", 14) // end of "wire:cli"
        wireCompletionParams.set<com.fasterxml.jackson.databind.JsonNode>("position", wirePos)

        val diagParams = LspTestSupport.mapper.createObjectNode()
        diagParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", td)

        val (code, messages) =
            LspTestSupport.runSession(
                LspTestSupport.request(1, "initialize", LspTestSupport.initializeParams()),
                LspTestSupport.notification("textDocument/didOpen", openParams),
                LspTestSupport.request(2, "textDocument/completion", completionParams),
                LspTestSupport.request(3, "textDocument/diagnostic", diagParams),
                LspTestSupport.request(4, "textDocument/completion", wireCompletionParams),
                LspTestSupport.request(5, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        val published =
            messages.first {
                it.path("method").asText() == "textDocument/publishDiagnostics"
            }
        assertTrue(
            published.path("params").path("diagnostics").toString().contains("notARealDirective"),
        )
        val completion = messages.first { it.has("id") && it.get("id").asInt() == 2 }
        assertTrue(completion.path("result").path("items").size() > 0)
        val pull = messages.first { it.has("id") && it.get("id").asInt() == 3 }
        assertEquals("full", pull.path("result").path("kind").asText())
        assertTrue(pull.path("result").path("items").size() >= 1)
        val wireCompletion = messages.first { it.has("id") && it.get("id").asInt() == 4 }
        assertTrue(wireCompletion.path("result").path("items").toString().contains("wire:click"))
    }

    @Test
    fun didChangeAndDidClose() {
        val uri = "file:///tmp/y.crema.html"
        val openParams = LspTestSupport.mapper.createObjectNode()
        val doc = LspTestSupport.mapper.createObjectNode()
        doc.put("uri", uri)
        doc.put("languageId", "crema")
        doc.put("version", 1)
        doc.put("text", "@csrf")
        openParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", doc)

        val changeParams = LspTestSupport.mapper.createObjectNode()
        val changeDoc = LspTestSupport.mapper.createObjectNode()
        changeDoc.put("uri", uri)
        changeDoc.put("version", 2)
        changeParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", changeDoc)
        val changes = changeParams.putArray("contentChanges")
        val change = LspTestSupport.mapper.createObjectNode()
        change.put("text", "@bogus")
        changes.add(change)

        val closeParams = LspTestSupport.mapper.createObjectNode()
        val closeDoc = LspTestSupport.mapper.createObjectNode()
        closeDoc.put("uri", uri)
        closeParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", closeDoc)

        val (code, messages) =
            LspTestSupport.runSession(
                LspTestSupport.request(1, "initialize", LspTestSupport.initializeParams()),
                LspTestSupport.notification("textDocument/didOpen", openParams),
                LspTestSupport.notification("textDocument/didChange", changeParams),
                LspTestSupport.notification("textDocument/didClose", closeParams),
                LspTestSupport.request(2, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        val diags =
            messages.filter {
                it.path("method").asText() == "textDocument/publishDiagnostics"
            }
        assertTrue(diags.size >= 3)
        assertTrue(diags[1].path("params").path("diagnostics").toString().contains("bogus"))
        assertEquals(0, diags.last().path("params").path("diagnostics").size())
    }

    @Test
    fun unknownMethodAndClientResponseIgnored() {
        val clientResponse = LspTestSupport.mapper.createObjectNode()
        clientResponse.put("jsonrpc", "2.0")
        clientResponse.put("id", 99)
        clientResponse.putNull("result")

        val (code, messages) =
            LspTestSupport.runSession(
                LspTestSupport.request(1, "initialize", LspTestSupport.initializeParams()),
                LspTestSupport.frame(clientResponse),
                LspTestSupport.request(2, "workspace/unknownThing"),
                LspTestSupport.notification("telemetry/event"),
                LspTestSupport.request(3, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        val err = messages.first { it.has("id") && it.get("id").asInt() == 2 }
        assertEquals(LspServer.METHOD_NOT_FOUND, err.path("error").path("code").asInt())
    }

    @Test
    fun malformedFramesProduceParseErrors() {
        val badHeader = "Not-A-Header\r\n\r\n".toByteArray()
        val (code, messages) =
            LspTestSupport.runSession(
                badHeader,
                LspTestSupport.request(1, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        assertTrue(messages.any { it.path("error").path("code").asInt() == LspServer.PARSE_ERROR })
    }

    @Test
    fun negativeContentLengthAndTruncatedBody() {
        val negative = "Content-Length: -1\r\n\r\n".toByteArray()
        val (code1, messages1) =
            LspTestSupport.runSession(
                negative,
                LspTestSupport.request(1, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code1)
        assertTrue(messages1.any { it.path("error").path("code").asInt() == LspServer.PARSE_ERROR })

        val truncated = "Content-Length: 100\r\n\r\n{}".toByteArray()
        val (code2, messages2) =
            LspTestSupport.runSession(
                truncated,
            )
        assertEquals(1, code2)
        assertTrue(messages2.any { it.path("error").path("code").asInt() == LspServer.PARSE_ERROR })
    }

    @Test
    fun missingContentLengthHeader() {
        val headersOnly = "Content-Type: application/vscode-jsonrpc\r\n\r\n".toByteArray()
        val (code, messages) =
            LspTestSupport.runSession(
                headersOnly,
                LspTestSupport.request(1, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        assertTrue(messages.any { it.path("error").path("code").asInt() == LspServer.PARSE_ERROR })
    }

    @Test
    fun didChangeWithoutChangesIsIgnored() {
        val uri = "file:///tmp/z.crema.html"
        val openParams = LspTestSupport.mapper.createObjectNode()
        val doc = LspTestSupport.mapper.createObjectNode()
        doc.put("uri", uri)
        doc.put("languageId", "crema")
        doc.put("version", 1)
        doc.put("text", "@csrf")
        openParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", doc)

        val changeParams = LspTestSupport.mapper.createObjectNode()
        val changeDoc = LspTestSupport.mapper.createObjectNode()
        changeDoc.put("uri", uri)
        changeParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", changeDoc)
        changeParams.putArray("contentChanges")

        val (code, messages) =
            LspTestSupport.runSession(
                LspTestSupport.request(1, "initialize", LspTestSupport.initializeParams()),
                LspTestSupport.notification("textDocument/didOpen", openParams),
                LspTestSupport.notification("textDocument/didChange", changeParams),
                LspTestSupport.request(2, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        assertEquals(
            1,
            messages.count { it.path("method").asText() == "textDocument/publishDiagnostics" },
        )
    }

    @Test
    fun notificationsWithMissingParamsAreSafe() {
        val (code, _) =
            LspTestSupport.runSession(
                LspTestSupport.notification("textDocument/didOpen"),
                LspTestSupport.notification("textDocument/didChange"),
                LspTestSupport.notification("textDocument/didClose"),
                LspTestSupport.request(1, "textDocument/completion"),
                LspTestSupport.request(2, "textDocument/diagnostic"),
                LspTestSupport.request(3, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
    }

    @Test
    fun transportWriteHelpers() {
        val out = java.io.ByteArrayOutputStream()
        val transport = JsonRpcTransport(java.io.ByteArrayInputStream(ByteArray(0)), out)
        transport.writeResponse(null, null)
        transport.writeError(null, 1, "x")
        val nullId = LspTestSupport.mapper.nullNode()
        transport.writeResponse(nullId, null)
        transport.writeError(nullId, 3, "z")
        val id = LspTestSupport.mapper.nodeFactory.numberNode(7)
        transport.writeResponse(id, transport.objectNode().put("ok", true))
        transport.writeError(id, 2, "y")
        val messages = LspTestSupport.parseMessages(out.toByteArray())
        assertEquals(6, messages.size)
        assertTrue(messages[0].get("id").isNull)
        assertTrue(messages[4].path("result").path("ok").asBoolean())
        assertEquals(2, messages[5].path("error").path("code").asInt())
    }

    @Test
    fun lfOnlyHeadersAndNonNumericLength() {
        val lfOnly = "Content-Length: 2\n\n{}".toByteArray()
        val messages = LspTestSupport.parseMessages(lfOnly)
        assertEquals(1, messages.size)

        val (code, errs) =
            LspTestSupport.runSession(
                "Content-Length: nope\r\n\r\n".toByteArray(),
                LspTestSupport.request(1, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        assertTrue(errs.any { it.path("error").path("code").asInt() == LspServer.PARSE_ERROR })
    }

    @Test
    fun nullMethodAndNonArrayChanges() {
        val nullMethod = LspTestSupport.mapper.createObjectNode()
        nullMethod.put("jsonrpc", "2.0")
        nullMethod.put("id", 1)
        nullMethod.putNull("method")

        val changeParams = LspTestSupport.mapper.createObjectNode()
        changeParams.set<com.fasterxml.jackson.databind.JsonNode>(
            "textDocument",
            LspTestSupport.mapper.createObjectNode().put("uri", "file:///a.crema.html"),
        )
        changeParams.set<com.fasterxml.jackson.databind.JsonNode>(
            "contentChanges",
            LspTestSupport.mapper.createObjectNode(),
        )

        val openMissingDoc = LspTestSupport.mapper.createObjectNode()
        openMissingDoc.put("other", true)

        val (code, _) =
            LspTestSupport.runSession(
                LspTestSupport.frame(nullMethod),
                LspTestSupport.notification("textDocument/didOpen", openMissingDoc),
                LspTestSupport.notification("textDocument/didChange", changeParams),
                LspTestSupport.request(2, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
    }

    @Test
    fun changeWithoutOpenAndCompletionWithoutPosition() {
        val changeParams = LspTestSupport.mapper.createObjectNode()
        changeParams.set<com.fasterxml.jackson.databind.JsonNode>(
            "textDocument",
            LspTestSupport.mapper.createObjectNode().put("uri", "file:///new.crema.html"),
        )
        val changes = changeParams.putArray("contentChanges")
        // omit text so asText(fallback) uses empty previous
        changes.add(LspTestSupport.mapper.createObjectNode())

        val completionParams = LspTestSupport.mapper.createObjectNode()
        completionParams.set<com.fasterxml.jackson.databind.JsonNode>(
            "textDocument",
            LspTestSupport.mapper.createObjectNode().put("uri", "file:///new.crema.html"),
        )

        val (code, messages) =
            LspTestSupport.runSession(
                LspTestSupport.notification("textDocument/didChange", changeParams),
                LspTestSupport.request(1, "textDocument/completion", completionParams),
                LspTestSupport.request(2, "textDocument/diagnostic", completionParams),
                LspTestSupport.request(3, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        assertTrue(messages.any { it.has("id") && it.get("id").asInt() == 1 })
    }

    @Test
    fun exceptionWithNullMessageUsesFallback() {
        var thrown = false
        val boom =
            object : java.io.InputStream() {
                override fun read(): Int {
                    if (!thrown) {
                        thrown = true
                        throw RuntimeException(null as String?)
                    }
                    return -1
                }
            }
        val out = java.io.ByteArrayOutputStream()
        val code = LspServer().serve(boom, out)
        assertEquals(1, code)
        val messages = LspTestSupport.parseMessages(out.toByteArray())
        assertTrue(messages.any { it.path("error").path("message").asText() == "parse error" })
    }

    @Test
    fun requestWithJsonNullId() {
        val node = LspTestSupport.mapper.createObjectNode()
        node.put("jsonrpc", "2.0")
        node.putNull("id")
        node.put("method", "shutdown")
        val (code, _) =
            LspTestSupport.runSession(
                LspTestSupport.frame(node),
                LspTestSupport.request(1, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
    }

    @Test
    fun headerWithoutColonAndEofMidHeaders() {
        val bad = "Content-Length\r\n\r\n".toByteArray()
        val (code, messages) =
            LspTestSupport.runSession(
                bad,
                LspTestSupport.request(1, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        assertTrue(messages.any { it.path("error").path("code").asInt() == LspServer.PARSE_ERROR })

        // EOF after partial headers returns null message → exit code 1
        val partial = "Content-Length: 1".toByteArray()
        val (code2, _) = LspTestSupport.runSession(partial)
        assertEquals(1, code2)
    }

    @Test
    fun requestsWithoutIdAreIgnored() {
        val (code, messages) =
            LspTestSupport.runSession(
                LspTestSupport.notification("initialize", LspTestSupport.initializeParams()),
                LspTestSupport.notification("shutdown"),
                LspTestSupport.notification("textDocument/completion"),
                LspTestSupport.notification("textDocument/diagnostic"),
                LspTestSupport.request(1, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        assertFalse(messages.any { it.has("error") })
    }

    @Test
    fun invalidJsonBodyIsParseError() {
        val body = "not-json".toByteArray()
        val framed =
            "Content-Length: ${body.size}\r\n\r\n".toByteArray() + body
        val (code, messages) =
            LspTestSupport.runSession(
                framed,
                LspTestSupport.request(1, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        assertTrue(messages.any { it.path("error").path("code").asInt() == LspServer.PARSE_ERROR })
    }

    @Test
    fun emptyUriNotificationsAreIgnored() {
        val openParams = LspTestSupport.mapper.createObjectNode()
        val doc = LspTestSupport.mapper.createObjectNode()
        doc.put("uri", "")
        doc.put("text", "@csrf")
        openParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", doc)

        val changeParams = LspTestSupport.mapper.createObjectNode()
        val changeDoc = LspTestSupport.mapper.createObjectNode()
        changeDoc.put("uri", "")
        changeParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", changeDoc)
        changeParams.putArray("contentChanges").add(
            LspTestSupport.mapper.createObjectNode().put("text", "@csrf"),
        )

        val closeParams = LspTestSupport.mapper.createObjectNode()
        closeParams.set<com.fasterxml.jackson.databind.JsonNode>(
            "textDocument",
            LspTestSupport.mapper.createObjectNode().put("uri", ""),
        )

        val (code, messages) =
            LspTestSupport.runSession(
                LspTestSupport.notification("textDocument/didOpen", openParams),
                LspTestSupport.notification("textDocument/didChange", changeParams),
                LspTestSupport.notification("textDocument/didClose", closeParams),
                LspTestSupport.request(1, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        assertEquals(
            0,
            messages.count { it.path("method").asText() == "textDocument/publishDiagnostics" },
        )
    }

    @Test
    fun javaHostDocumentCompletionsAndDiagnostics() {
        val q = "\"\"\""
        val java =
            """
            public class Counter {
                public int count = 0;
                public String template() {
                    return $q
            <div>{{ count }}</div>
            @csrf @nope
            <button wire:cli
            $q;
                }
            }
            """.trimIndent()
        val uri = "file:///Counter.java"
        val openParams = LspTestSupport.mapper.createObjectNode()
        val doc = LspTestSupport.mapper.createObjectNode()
        doc.put("uri", uri)
        doc.put("languageId", "java")
        doc.put("version", 1)
        doc.put("text", java)
        openParams.set<com.fasterxml.jackson.databind.JsonNode>("textDocument", doc)

        val atCsrf = java.indexOf("@csrf") + 1
        val atPos = CremaAnalyzer.offsetToPosition(java, atCsrf)
        val completionParams = LspTestSupport.mapper.createObjectNode()
        completionParams.set<com.fasterxml.jackson.databind.JsonNode>(
            "textDocument",
            LspTestSupport.mapper.createObjectNode().put("uri", uri),
        )
        completionParams.set<com.fasterxml.jackson.databind.JsonNode>(
            "position",
            LspTestSupport.mapper.createObjectNode().put("line", atPos.line).put("character", atPos.character),
        )

        val outsideParams = LspTestSupport.mapper.createObjectNode()
        outsideParams.set<com.fasterxml.jackson.databind.JsonNode>(
            "textDocument",
            LspTestSupport.mapper.createObjectNode().put("uri", uri),
        )
        outsideParams.set<com.fasterxml.jackson.databind.JsonNode>(
            "position",
            LspTestSupport.mapper.createObjectNode().put("line", 0).put("character", 0),
        )

        val diagParams = LspTestSupport.mapper.createObjectNode()
        diagParams.set<com.fasterxml.jackson.databind.JsonNode>(
            "textDocument",
            LspTestSupport.mapper.createObjectNode().put("uri", uri),
        )

        val (code, messages) =
            LspTestSupport.runSession(
                LspTestSupport.request(1, "initialize", LspTestSupport.initializeParams()),
                LspTestSupport.notification("textDocument/didOpen", openParams),
                LspTestSupport.request(2, "textDocument/completion", completionParams),
                LspTestSupport.request(3, "textDocument/completion", outsideParams),
                LspTestSupport.request(4, "textDocument/diagnostic", diagParams),
                LspTestSupport.request(5, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        val published =
            messages.first { it.path("method").asText() == "textDocument/publishDiagnostics" }
        assertTrue(published.path("params").path("diagnostics").toString().contains("nope"))
        val completion = messages.first { it.has("id") && it.get("id").asInt() == 2 }
        assertTrue(completion.path("result").path("items").toString().contains("@csrf"))
        val outside = messages.first { it.has("id") && it.get("id").asInt() == 3 }
        assertEquals(0, outside.path("result").path("items").size())
        val pull = messages.first { it.has("id") && it.get("id").asInt() == 4 }
        assertTrue(pull.path("result").path("items").toString().contains("nope"))
    }

    @Test
    fun missingTextDocumentAndUnknownUriDiagnostic() {
        val noDoc = LspTestSupport.mapper.createObjectNode()
        noDoc.put("version", 1)

        val diagParams = LspTestSupport.mapper.createObjectNode()
        diagParams.set<com.fasterxml.jackson.databind.JsonNode>(
            "textDocument",
            LspTestSupport.mapper.createObjectNode().put("uri", "file:///never-opened.crema.html"),
        )

        val (code, messages) =
            LspTestSupport.runSession(
                LspTestSupport.notification("textDocument/didChange", noDoc),
                LspTestSupport.notification("textDocument/didClose", noDoc),
                LspTestSupport.request(1, "textDocument/diagnostic", diagParams),
                LspTestSupport.request(2, "shutdown"),
                LspTestSupport.notification("exit"),
            )
        assertEquals(0, code)
        val pull = messages.first { it.has("id") && it.get("id").asInt() == 1 }
        assertEquals(0, pull.path("result").path("items").size())
    }
}
