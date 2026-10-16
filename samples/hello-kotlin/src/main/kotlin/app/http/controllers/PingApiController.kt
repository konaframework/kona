package app.http.controllers

import app.http.resources.PingResource
import io.konaframework.http.BaseResourceController
import io.konaframework.http.JsonResource
import io.konaframework.http.Request
import io.konaframework.http.Response

/** Demo API resource controller for `router.apiResource("pings", …)`. */
class PingApiController : BaseResourceController() {
    override fun index(request: Request): Response =
        JsonResource.collection(
            listOf(PingResource(1, "alpha"), PingResource(2, "beta")),
        ).toResponse(request)

    override fun show(request: Request): Response {
        val id = (request.getAttribute("id") as? String)?.toIntOrNull() ?: 0
        return PingResource(id, "ping-$id").toResponse(request)
    }
}
