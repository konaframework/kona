package app.http.middleware

import org.konaframework.http.Request
import org.konaframework.inertia.HandleInertiaRequests

class HandleInertia : HandleInertiaRequests() {
    override fun share(request: Request): Map<String, Any?> =
        mapOf("appName" to "Inertia sample (Kotlin)")

    override fun version(request: Request): String = "1"
}
