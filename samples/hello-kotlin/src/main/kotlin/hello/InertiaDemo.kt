package hello

import dev.kona.core.Kona
import dev.kona.http.HttpKernel
import dev.kona.http.HttpServiceProvider
import dev.kona.http.Request
import dev.kona.http.Router
import dev.kona.inertia.HandleInertiaRequests
import dev.kona.inertia.Inertia
import dev.kona.inertia.InertiaServiceProvider
import dev.kona.inertia.assets.AssetManifest
import dev.kona.inertia.assets.Vite
import java.nio.file.Files
import java.nio.file.Path

object InertiaDemo {
    fun run(args: Array<String>, publicDir: Path): List<String> {
        Files.createDirectories(publicDir.resolve("build"))
        Files.writeString(
            publicDir.resolve("build/manifest.json"),
            """{"resources/js/app.js":{"file":"assets/app.js","isEntry":true}}""",
        )
        return try {
            val app = Kona.create(args)
                .configure {
                    it.set("inertia.version", "demo")
                    it.set("inertia.public_path", publicDir.toString())
                }
                .providers(HttpServiceProvider(), InertiaServiceProvider())
                .boot()
            app.resolve(HttpKernel::class.java).pushMiddleware(
                object : HandleInertiaRequests() {
                    override fun share(request: Request) = mapOf("shared" to true)
                },
            )
            app.resolve(Router::class.java).get("/") {
                Inertia.render("Pages/Demo", mapOf("hello" to "kona"))
            }
            val kernel = app.resolve(HttpKernel::class.java)
            val html = kernel.handle(Request.get("/"))
            val json = kernel.handle(
                Request("GET", "/", headers = mapOf("X-Inertia" to "true", "X-Inertia-Version" to "demo")),
            )
            val conflict = kernel.handle(
                Request("GET", "/", headers = mapOf("X-Inertia" to "true", "X-Inertia-Version" to "old")),
            )
            val tags = Vite.tags("resources/js/app.js")
            listOf(
                "html=${html.body.contains("data-page=")}",
                "json=${json.headers["X-Inertia"] == "true"}",
                "conflict=${conflict.status}",
                "vite=${tags.contains("app.js")}",
                "manifest=${app.resolve(AssetManifest::class.java).entry("resources/js/app.js")["file"]}",
            )
        } finally {
            Inertia.reset()
            Vite.reset()
        }
    }
}
