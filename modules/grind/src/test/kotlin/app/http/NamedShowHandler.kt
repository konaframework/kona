package app.http

import org.konaframework.http.Handler
import org.konaframework.http.Request
import org.konaframework.http.Response

/** App-package handler so `route:list --except-vendor` can keep a non-framework action. */
class NamedShowHandler : Handler {
    override fun handle(request: Request): Response = Response.text("show")
}
