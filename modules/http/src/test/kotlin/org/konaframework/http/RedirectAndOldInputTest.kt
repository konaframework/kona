package org.konaframework.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RedirectAndOldInputTest {
    private class FakeSession : RequestSession {
        private val data = linkedMapOf<String, Any?>()
        var previous: String? = null

        override fun get(key: String): Any? = data[key]

        override fun put(key: String, value: Any?): RequestSession {
            data[key] = value
            return this
        }

        override fun flash(key: String, value: Any?): RequestSession = put(key, value)

        override fun flashInput(input: Map<String, String>): RequestSession {
            data["_old_input"] = input
            return this
        }

        override fun old(key: String, default: String?): String? {
            val bag = data["_old_input"] as? Map<*, *> ?: return default
            if (!bag.containsKey(key)) {
                return default
            }
            return bag[key]?.toString() ?: default
        }

        override fun oldInput(): Map<String, String> {
            val bag = data["_old_input"] as? Map<*, *> ?: return emptyMap()
            return bag.entries.associate { it.key.toString() to (it.value?.toString() ?: "") }
        }

        override fun previousUrl(): String? = previous

        override fun setPreviousUrl(url: String): RequestSession {
            previous = url
            return this
        }
    }

    @Test
    fun redirectFactoriesAndBackTargets() {
        assertEquals(303, Response.redirect("/gone", 303).status)
        assertEquals("/gone", Response.redirect("/gone", 303).headers["Location"])

        val withSession = Request.get("/now").setAttribute(Request.SESSION_ATTRIBUTE, FakeSession().also {
            it.setPreviousUrl("/from-session")
        })
        assertEquals("/from-session", Response.back(withSession).headers["Location"])

        // Session present but no previous URL → fall through to Referer.
        val sessionThenReferer = Request(
            "GET",
            "/",
            headers = mapOf("Referer" to "/from-referer"),
        ).setAttribute(Request.SESSION_ATTRIBUTE, FakeSession())
        assertEquals("/from-referer", Response.back(sessionThenReferer).headers["Location"])

        // Session present, no previous, no Referer → fallback.
        assertEquals(
            "/fallback",
            Response.back(
                Request.get("/").setAttribute(Request.SESSION_ATTRIBUTE, FakeSession()),
                "/fallback",
            ).headers["Location"],
        )

        assertEquals("/fallback", Response.back(Request.get("/"), "/fallback", 303).headers["Location"])
        assertEquals(303, Response.back(Request.get("/"), "/fallback", 303).status)
    }

    @Test
    fun requestSessionAndOldHelpers() {
        assertNull(Request.get("/").session())
        assertNull(Request.get("/").setAttribute(Request.SESSION_ATTRIBUTE, "not-a-session").session())
        assertNull(Request.get("/").old("email"))
        assertEquals("d", Request.get("/").old("email", "d"))

        val session = FakeSession().flashInput(mapOf("email" to "a@b.c"))
        val request = Request.get("/").setAttribute(Request.SESSION_ATTRIBUTE, session)
        assertEquals(session, request.session())
        assertEquals("a@b.c", request.old("email"))
        assertEquals("x", request.old("missing", "x"))
        assertNull(request.old("missing"))
        val asInterface: RequestSession = session
        assertEquals("a@b.c", asInterface.old("email"))
        assertEquals("a@b.c", asInterface.old("email", null))
    }

    @Test
    fun expectsJsonBranches() {
        assertFalse(Request.get("/").expectsJson())
        assertTrue(
            Request("GET", "/", headers = mapOf("Accept" to "application/json")).expectsJson(),
        )
        assertFalse(
            Request("GET", "/", headers = mapOf("Accept" to "text/html, application/json")).expectsJson(),
        )
        assertTrue(
            Request(
                "POST",
                "/",
                headers = mapOf("Content-Type" to "application/json"),
                body = "{}",
            ).expectsJson(),
        )
        assertFalse(
            Request(
                "POST",
                "/",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = "a=1",
            ).expectsJson(),
        )
    }

    @Test
    fun validationRedirectUsesRefererWhenNoSession() {
        val response = DefaultExceptionHandler().handle(
            Request(
                "POST",
                "/contact",
                headers = mapOf("Accept" to "text/html", "Referer" to "/contact"),
                body = "email=x",
            ),
            ValidationException(mapOf("email" to listOf("bad"))),
        )
        assertEquals(302, response.status)
        assertEquals("/contact", response.headers["Location"])
        assertEquals(mapOf("email" to "x"), response.sessionOldInput)
        assertEquals(mapOf("email" to listOf("bad")), response.sessionErrors)
    }
}
