package dev.kona.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GrindTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun helpAndUnknown() {
        val app = GrindApplication()
        assertEquals(0, app.run(emptyArray()))
        assertEquals(0, app.run(arrayOf("help")))
        assertEquals(0, app.run(arrayOf("--help")))
        assertEquals(0, app.run(arrayOf("-h")))
        assertEquals(1, app.run(arrayOf("nope")))
    }

    @Test
    fun newKotlinAndJava() {
        val kt = temp.resolve("appkt")
        assertEquals(0, NewCommand().run(listOf(kt.toString()))) // default lang kotlin
        assertTrue(Files.exists(kt.resolve("README.md")))
        assertTrue(Files.exists(kt.resolve(".env")))
        assertEquals(1, GrindApplication().run(arrayOf("new", kt.toString(), "--lang=kotlin")))
        assertEquals(1, GrindApplication().run(arrayOf("new", temp.resolve("x2").toString(), "--lang=ruby")))
        assertEquals(1, GrindApplication().run(arrayOf("new")))

        val j = temp.resolve("appjava")
        assertEquals(0, NewCommand().run(listOf(j.toString(), "--lang=java")))
        assertTrue(Files.exists(j.resolve("app/Http/Controllers/WelcomeController.java.txt")))
    }

    @Test
    fun makeController() {
        val base = temp.resolve("proj")
        Files.createDirectories(base.resolve("app/Http/Controllers"))
        assertEquals(0, MakeControllerCommand().run(listOf("User", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.exists(base.resolve("app/Http/Controllers/UserController.kt")))
        assertEquals(0, MakeControllerCommand().run(listOf("AccountController", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.exists(base.resolve("app/Http/Controllers/AccountController.kt")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller", "User", "--base=$base", "--lang=kotlin")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller")))
        assertEquals(0, MakeControllerCommand().run(listOf("Post", "--base=$base", "--lang=java")))
        Files.writeString(base.resolve("app/Http/Controllers/X.java"), "class X {}")
        // detectLang java via existing .java under app/
        val javaProj = temp.resolve("javap")
        Files.createDirectories(javaProj.resolve("app/Http/Controllers"))
        Files.writeString(javaProj.resolve("app/Http/Controllers/X.java"), "class X {}")
        assertEquals(0, MakeControllerCommand().run(listOf("Auto", "--base=$javaProj")))
        assertTrue(Files.exists(javaProj.resolve("app/Http/Controllers/AutoController.java")))
        // detectLang kotlin default when no java
        val ktProj = temp.resolve("ktp")
        Files.createDirectories(ktProj.resolve("app/Http/Controllers"))
        assertEquals(0, MakeControllerCommand().run(listOf("Auto", "--base=$ktProj")))
        // controllers dir missing -> kotlin
        val bare = temp.resolve("bare")
        Files.createDirectories(bare)
        assertEquals(0, MakeControllerCommand().run(listOf("Bare", "--base=$bare")))
    }

    @Test
    fun serveOnceAndRoutesProperty() {
        System.setProperty("kona.routes", TestRoutes::class.java.name)
        try {
            val base = temp.resolve("serve")
            Files.createDirectories(base)
            val port = java.net.ServerSocket(0).use { it.localPort }
            assertEquals(
                0,
                ServeCommand(waitForever = {}).run(
                    listOf("--base=$base", "--host=127.0.0.1", "--port=$port", "--once=true"),
                ),
            )
            System.setProperty("kona.routes", "")
            val port2 = java.net.ServerSocket(0).use { it.localPort }
            assertEquals(
                0,
                GrindApplication().run(
                    arrayOf("serve", "--base=$base", "--host=127.0.0.1", "--port=$port2", "--once=true"),
                ),
            )
            System.setProperty("kona.routes", EmptyRoutes::class.java.name)
            val portEmpty = java.net.ServerSocket(0).use { it.localPort }
            assertEquals(
                0,
                ServeCommand(waitForever = {}).run(
                    listOf("--base=$base", "--host=127.0.0.1", "--port=$portEmpty", "--once=true"),
                ),
            )
            System.clearProperty("kona.routes")
            val port3 = java.net.ServerSocket(0).use { it.localPort }
            assertEquals(
                0,
                ServeCommand(waitForever = {}).run(listOf("--base=$base", "--port=$port3", "--once=true")),
            )
        } finally {
            System.clearProperty("kona.routes")
        }
    }

    @Test
    fun parseOptionsAndServeWait() {
        val (pos, opt) = parseOptions(listOf("a", "--flag", "--k=v"))
        assertEquals(listOf("a"), pos)
        assertEquals("true", opt["flag"])
        assertEquals("v", opt["k"])
        var waited = false
        val base = temp.resolve("w")
        Files.createDirectories(base)
        val port = java.net.ServerSocket(0).use { it.localPort }
        ServeCommand(waitForever = { waited = true }).run(
            listOf("--base=$base", "--host=127.0.0.1", "--port=$port"),
        )
        assertTrue(waited)
    }

    @Test
    fun missingSkeletonFails() {
        assertThrows(GrindException::class.java) {
            NewCommand().run(listOf(temp.resolve("x").toString(), "--lang=ruby"))
        }
        val target = temp.resolve("broken")
        Files.createDirectories(target)
        assertThrows(GrindException::class.java) {
            NewCommand().copySkeleton("broken", target, "app")
        }
    }
}

class TestRoutes {
    fun register(router: dev.kona.http.Router) {
        router.get("/custom") { dev.kona.http.Response.text("custom") }
    }
}
