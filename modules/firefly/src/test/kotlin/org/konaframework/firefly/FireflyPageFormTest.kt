package org.konaframework.firefly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.core.Application
import org.konaframework.filesystem.FilesystemServiceProvider
import org.konaframework.firefly.forms.FireflyUploads
import org.konaframework.firefly.forms.Select
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.schemas.Schema
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Request
import org.konaframework.http.RequestSession
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.nio.file.Files
import java.util.function.Function

class FireflyPageFormTest {
    private fun form(): Schema = Schema.make().components(
        Select.make("status").options(mapOf("open" to "Open", "closed" to "Closed")).searchable(),
        TextInput.make("title"),
    )

    private fun send(router: Router, method: String, path: String, query: Map<String, String> = emptyMap(), body: String = "", session: RequestSession? = null, headers: Map<String, String> = emptyMap()): Response {
        val match = router.find(method, path) ?: throw AssertionError("missing $method $path")
        val request = Request(method, path, headers, query, body)
        if (session != null) request.setAttribute(Request.SESSION_ATTRIBUTE, session)
        return match.route.action.handle(request)
    }

    @Test
    fun pageFormOptionsSearchesTheMountedSelect() {
        val router = Router()
        Panel().path("admin").authenticate(false)
            .page(PanelPage("settings").mountForm(form()))
            .page(PanelPage("").mountForm(form()))
            .page(PanelPage("plain"))
            .mount(router)
        val found = send(router, "GET", "/admin/settings/form-options", mapOf("field" to "status", "q" to "op")).body
        assertTrue(found.contains("\"value\":\"open\""), found)
        assertTrue(found.contains("\"label\":\"Open\""))
        assertTrue(found.contains("\"enabled\":true"))
        assertFalse(found.contains("Closed"))
        val all = send(router, "GET", "/admin/form-options", mapOf("field" to "status")).body
        assertTrue(all.contains("Open") && all.contains("Closed"), all)
        assertEquals("[]", send(router, "GET", "/admin/settings/form-options", mapOf("field" to "title")).body)
        assertEquals("[]", send(router, "GET", "/admin/settings/form-options").body)
        assertEquals("[]", send(router, "GET", "/admin/plain/form-options", mapOf("field" to "status")).body)
    }

    @Test
    fun pageFormOptionsHonoursTheDomainGateAndSignIn() {
        val gated = Router()
        Panel().path("admin").domain("admin.test").authenticate(false).page(PanelPage("settings").mountForm(form())).mount(gated)
        assertEquals(404, send(gated, "GET", "/admin/settings/form-options").status)
        val guard = MemoryPanelGuard()
        guard.register("Ada", "ada@test", "secret")
        val locked = Router()
        Panel().path("admin").guard(guard).page(PanelPage("settings").mountForm(form())).mount(locked)
        val redirect = send(locked, "GET", "/admin/settings/form-options", mapOf("field" to "status"))
        assertEquals(302, redirect.status)
        assertEquals("/admin/login", redirect.headers["Location"])
        guard.attempt("ada@test", "secret")
        assertTrue(send(locked, "GET", "/admin/settings/form-options", mapOf("field" to "status")).body.contains("Open"))
    }

    @Test
    fun postingTheMountedFormRunsTheHandlerAndFlashesItsTitle() {
        val seen = ArrayList<Map<String, String>>()
        val router = Router()
        Panel().path("admin").authenticate(false)
            .page(PanelPage("settings").mountForm(form()).onFormSubmit(Function { input -> seen.add(input); "Settings saved" }))
            .page(PanelPage("defaults").mountForm(form()))
            .mount(router)
        val session = PageFormSession()
        val saved = send(router, "POST", "/admin/settings", mapOf("tab" to "1"), "title=Hi&status=open&_token=&_bulk_action=&tab=1", session)
        assertEquals(302, saved.status)
        assertEquals("/admin/settings", saved.headers["Location"])
        assertEquals(listOf(mapOf("title" to "Hi", "status" to "open")), seen)
        assertTrue(session.values.values.toString().contains("Settings saved"), session.values.toString())
        val fallback = PageFormSession()
        send(router, "POST", "/admin/defaults", body = "title=x", session = fallback)
        assertTrue(fallback.values.values.toString().contains("Form submitted"))
        assertEquals(302, send(router, "POST", "/admin/defaults", body = "title=x").status)
    }

    @Test
    fun uploadsAreAllowedOnlyForSignedInPanelUsersWithAValidToken() {
        val guard = MemoryPanelGuard()
        guard.register("Ada", "ada@test", "secret")
        val panel = Panel().path("admin").guard(guard)
        val gated = Panel().path("ops").domain("ops.test").authenticate(false)
        val request = Request("POST", FireflyUploads.PATH)
        assertFalse(Firefly.uploadAllowed(request, listOf(panel, gated)))
        guard.attempt("ada@test", "secret")
        assertTrue(Firefly.uploadAllowed(request, listOf(gated, panel)))
        val session = PageFormSession()
        session.put(FireflyCsrf.TOKEN_KEY, "expected")
        val forged = Request("POST", FireflyUploads.PATH, mapOf("X-CSRF-TOKEN" to "wrong"))
        forged.setAttribute(Request.SESSION_ATTRIBUTE, session)
        assertFalse(Firefly.uploadAllowed(forged, listOf(panel)))
    }

    @Test
    fun mountRegistersTheUploadRouteWhenFilesystemsAreBound() {
        val base = Files.createTempDirectory("firefly-uploads")
        val guard = MemoryPanelGuard()
        guard.register("Ada", "ada@test", "secret")
        guard.attempt("ada@test", "secret")
        Firefly.reset()
        Firefly.register(Panel().path("admin").guard(guard))
        val app = Application.builder(base)
            .withProvider(HttpServiceProvider())
            .withProvider(FilesystemServiceProvider())
            .build()
        Firefly.mount(app)
        val router = app.resolve(Router::class.java)
        val body = "--B\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\nContent-Type: text/plain\r\n\r\nhello\r\n--B--\r\n"
        val stored = send(router, "POST", FireflyUploads.PATH, body = body, headers = mapOf("Content-Type" to "multipart/form-data; boundary=B"))
        assertEquals(200, stored.status, stored.body)
        assertTrue(stored.body.contains("\"path\""))
        guard.logout()
        assertEquals(419, send(router, "POST", FireflyUploads.PATH, body = body, headers = mapOf("Content-Type" to "multipart/form-data; boundary=B")).status)
        Firefly.reset()
    }

    @Test
    fun richEditorAssetsAreServedFromTheAllowList() {
        val router = Router()
        FireflyAssets.register(router)
        fun asset(file: String, withParam: Boolean = true): Response {
            val uri = "/_firefly/rich-editor/$file"
            val match = router.resolve("GET", uri)
            val request = Request("GET", uri)
            if (withParam) request.attributes.putAll(match.parameters)
            return match.route.action.handle(request)
        }
        val script = asset("simple.js")
        assertEquals(200, script.status)
        assertEquals("application/javascript; charset=utf-8", script.headers["Content-Type"])
        assertTrue(String(script.bodyBytes()).contains("mountSimpleEditor"))
        assertEquals(200, asset("icons.js").status)
        assertEquals(404, asset("secret.js").status)
        assertEquals(404, asset("simple.js", withParam = false).status)
    }
}

private class PageFormSession : RequestSession {
    val values = LinkedHashMap<String, Any?>()
    override fun get(key: String): Any? = values[key]
    override fun put(key: String, value: Any?): RequestSession {
        values[key] = value
        return this
    }
    override fun flash(key: String, value: Any?): RequestSession = put(key, value)
    override fun flashInput(input: Map<String, String>): RequestSession = this
    override fun old(key: String, default: String?): String? = default
    override fun oldInput(): Map<String, String> = emptyMap()
    override fun previousUrl(): String? = null
    override fun setPreviousUrl(url: String): RequestSession = this
}
