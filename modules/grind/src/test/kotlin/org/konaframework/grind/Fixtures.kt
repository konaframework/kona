package org.konaframework.grind

import org.konaframework.core.Application
import org.konaframework.core.Kona
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Response
import org.konaframework.http.Router
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object FixtureApp {
    @JvmStatic
    fun create(args: Array<String>): Application {
        val app = Kona.create(args).providers(HttpServiceProvider()).boot()
        app.resolve(Router::class.java).get("/fixture") {
            Response.text("fixture:" + app.config.get<String>("app.name"))
        }
        return app
    }
}

class InstanceCreateApp {
    fun create(args: Array<String>): Application = Kona.start(args)
}

object WrongReturnApp {
    @JvmStatic
    fun create(args: Array<String>): String = args.joinToString()
}

class NoCreateApp

object ThrowingApp {
    @JvmStatic
    fun create(args: Array<String>): Application = throw IllegalStateException("boom ${args.size}")
}

internal fun httpGet(url: String): HttpResponse<String> =
    HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

internal fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }

/** Minimal Gradle project with an (empty) compiled-classes dir so [AppLoader] falls back to the test classpath. */
internal fun fixtureProject(dir: java.nio.file.Path, env: String = ""): java.nio.file.Path {
    java.nio.file.Files.createDirectories(dir.resolve("build/classes/kotlin/main"))
    java.nio.file.Files.writeString(dir.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"\n")
    java.nio.file.Files.writeString(dir.resolve(".env"), env)
    return dir
}
