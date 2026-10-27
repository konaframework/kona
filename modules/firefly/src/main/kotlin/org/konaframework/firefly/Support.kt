package org.konaframework.firefly

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.http.Request
import org.konaframework.http.Route
import java.security.SecureRandom

/** Session `_token` helpers for panel HTML (same key VerifyCsrfToken reads). */
internal object FireflyCsrf {
    const val TOKEN_KEY: String = "_token"

    private val random = SecureRandom()

    fun ensure(request: Request): String? {
        val session = request.session() ?: return null
        val existing = session.get(TOKEN_KEY) as? String
        if (!existing.isNullOrBlank()) return existing
        val generated = generate()
        session.put(TOKEN_KEY, generated)
        return generated
    }

    fun field(token: String?): String {
        if (token.isNullOrBlank()) return ""
        return """<input type="hidden" name="_token" value="${escapeHtml(token)}" />"""
    }

    /**
     * When the request has a session token, require a matching `_token` (or `csrf-token`) field.
     * Sessions without a token (tests / no StartSession) skip the check.
     */
    fun rejectIfInvalid(request: Request): org.konaframework.http.Response? {
        val session = request.session() ?: return null
        val expected = session.get(TOKEN_KEY) as? String ?: return null
        if (expected.isBlank()) return null
        val provided = request.input(TOKEN_KEY) ?: request.input("csrf-token") ?: request.header("X-CSRF-TOKEN")
        if (provided != null && provided == expected) return null
        return org.konaframework.http.Response.html("CSRF token mismatch", 419)
    }

    private fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }
}

internal fun escapeHtml(text: String): String {
    val out = StringBuilder()
    for (ch in text) {
        if (ch == '&') {
            out.append("&amp;")
        } else if (ch == '<') {
            out.append("&lt;")
        } else if (ch == '>') {
            out.append("&gt;")
        } else if (ch == '"') {
            out.append("&quot;")
        } else {
            out.append(ch)
        }
    }
    return out.toString()
}

internal fun normalizePrefix(raw: String): String = raw.trim().trim('/')

internal fun panelUri(prefix: String, slug: String): String {
    val parts = ArrayList<String>()
    if (prefix.isNotEmpty()) parts.add(prefix)
    if (slug.isNotEmpty()) parts.add(slug)
    if (parts.isEmpty()) return "/"
    return "/" + parts.joinToString("/")
}

internal fun reserved(uri: String, owner: String) {
    if (uri == "/_firefly" || uri.startsWith("/_firefly/") || uri == "/wire/update" || uri.startsWith("/wire/update/")) {
        throw FireflyException("Panel route [$uri] for [$owner] would catch a Firefly or Wire path")
    }
}

internal fun handlerLabel(route: Route): String {
    val named = route.name
    if (named != null && named.isNotEmpty()) return named
    return route.action.javaClass.name
}

internal class PanelAnchor : Component("panel")

internal object SchemaBridge {
    private val getFor = Schema::class.java.methods.first { it.name.startsWith("getFor$") }

    fun open(state: Map<String, Any?>): Get {
        val anchor = PanelAnchor()
        val schema = Schema().fill(state).components(anchor)
        return getFor.invoke(schema, anchor) as Get
    }
}
