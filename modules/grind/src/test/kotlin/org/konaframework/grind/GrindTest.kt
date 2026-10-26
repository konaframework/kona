package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
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
        assertEquals(1, app.run(arrayOf("new", "x")))
    }

    @Test
    fun makeController() {
        val base = temp.resolve("proj")
        Files.createDirectories(base.resolve("src/main/kotlin/app/http/controllers"))
        assertEquals(0, MakeControllerCommand().run(listOf("User", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/controllers/UserController.kt")))
        assertEquals(0, MakeControllerCommand().run(listOf("AccountController", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/controllers/AccountController.kt")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller", "User", "--base=$base", "--lang=kotlin")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller", "../../Evil", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:controller", "Bad-Name", "--base=$base")))
        assertTrue(Files.notExists(temp.resolve("EvilController.kt")))
        assertEquals(0, MakeControllerCommand().run(listOf("Post", "--base=$base", "--lang=java")))
        val javaProj = temp.resolve("javap")
        Files.createDirectories(javaProj.resolve("src/main/java/app/http/controllers"))
        Files.writeString(javaProj.resolve("src/main/java/app/http/controllers/X.java"), "class X {}")
        assertEquals(0, MakeControllerCommand().run(listOf("Auto", "--base=$javaProj")))
        assertTrue(Files.exists(javaProj.resolve("src/main/java/app/http/controllers/AutoController.java")))
        val ktProj = temp.resolve("ktp")
        Files.createDirectories(ktProj.resolve("src/main/kotlin/app/http/controllers"))
        assertEquals(0, MakeControllerCommand().run(listOf("Auto", "--base=$ktProj")))
        val bare = temp.resolve("bare")
        Files.createDirectories(bare)
        assertEquals(0, MakeControllerCommand().run(listOf("Bare", "--base=$bare")))
        val emptyJavaRoot = temp.resolve("empty-java")
        Files.createDirectories(emptyJavaRoot.resolve("src/main/java/app"))
        assertEquals(0, MakeControllerCommand().run(listOf("EmptyJava", "--base=$emptyJavaRoot")))
        assertTrue(Files.exists(emptyJavaRoot.resolve("src/main/kotlin/app/http/controllers/EmptyJavaController.kt")))
        assertEquals(0, MakeControllerCommand().run(listOf("Order", "--base=$base", "--resource", "--lang=kotlin")))
        val resourceKt = Files.readString(base.resolve("src/main/kotlin/app/http/controllers/OrderController.kt"))
        assertTrue(resourceKt.contains("class OrderController : BaseResourceController()"))
        assertTrue(resourceKt.contains("override fun store("))
        assertTrue(resourceKt.contains("override fun destroy("))
        assertEquals(0, MakeControllerCommand().run(listOf("Invoice", "--base=$base", "--resource", "--lang=java")))
        val resourceJava = Files.readString(base.resolve("src/main/java/app/http/controllers/InvoiceController.java"))
        assertTrue(resourceJava.contains("extends BaseResourceController"))
        assertTrue(resourceJava.contains("public Response store(Request"))
        assertTrue(resourceJava.contains("public Response destroy(Request"))
        assertEquals(0, MakeControllerCommand().run(listOf("ApiThing", "--base=$base", "--api", "--lang=kotlin")))
        val apiCtrl = Files.readString(base.resolve("src/main/kotlin/app/http/controllers/ApiThingController.kt"))
        assertTrue(apiCtrl.contains("class ApiThingController : BaseResourceController()"))
        assertTrue(apiCtrl.contains("fun store"))
        assertFalse(apiCtrl.contains("fun create"))
        assertEquals(
            0,
            MakeControllerCommand().run(
                listOf("Post", "--base=$base", "--resource", "--requests", "--model=Post", "--lang=kotlin"),
            ),
        )
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/requests/StorePostRequest.kt")))
        val postCtrl = Files.readString(base.resolve("src/main/kotlin/app/http/controllers/PostController.kt"))
        assertTrue(postCtrl.contains("class PostController : BaseResourceController()"))
        assertTrue(postCtrl.contains("validate(request, StorePostRequest())"))
        assertTrue(postCtrl.contains("validate(request, UpdatePostRequest())"))
        assertEquals(
            0,
            MakeControllerCommand().run(
                listOf("Sku", "--base=$base", "--api", "--requests", "--model=Sku", "--lang=java"),
            ),
        )
        val sku = Files.readString(base.resolve("src/main/java/app/http/controllers/SkuController.java"))
        assertTrue(sku.contains("extends BaseResourceController"))
        assertTrue(sku.contains("validate(request, new StoreSkuRequest())"))
        assertTrue(sku.contains("validate(request, new UpdateSkuRequest())"))
        assertFalse(sku.contains("create(Request"))
        assertTrue(Files.exists(base.resolve("src/main/java/app/http/requests/UpdateSkuRequest.java")))
        assertEquals(
            0,
            MakeControllerCommand().run(
                listOf("Book", "--base=$base", "--resource", "--requests", "--model=Book", "--lang=java"),
            ),
        )
        val book = Files.readString(base.resolve("src/main/java/app/http/controllers/BookController.java"))
        assertTrue(book.contains("extends BaseResourceController"))
        assertTrue(book.contains("create(Request"))
        assertTrue(book.contains("validate(request, new StoreBookRequest())"))
        assertTrue(book.contains("validate(request, new UpdateBookRequest())"))
    }

    @Test
    fun makeResource() {
        val base = temp.resolve("res-proj")
        Files.createDirectories(base.resolve("src/main/kotlin/app"))
        assertEquals(0, MakeResourceCommand().run(listOf("User", "--base=$base", "--lang=kotlin")))
        val kt = Files.readString(base.resolve("src/main/kotlin/app/http/resources/UserResource.kt"))
        assertTrue(kt.contains("class UserResource"))
        assertTrue(kt.contains("JsonResource"))
        assertEquals(0, MakeResourceCommand().run(listOf("PostResource", "--base=$base", "--collection", "--lang=kotlin")))
        val collectionKt = Files.readString(base.resolve("src/main/kotlin/app/http/resources/PostResource.kt"))
        assertTrue(collectionKt.contains("fun collection("))
        assertEquals(1, GrindApplication().run(arrayOf("make:resource", "User", "--base=$base", "--lang=kotlin")))
        assertEquals(1, GrindApplication().run(arrayOf("make:resource")))
        assertEquals(1, GrindApplication().run(arrayOf("make:resource", "Bad-Name", "--base=$base")))
        assertEquals(0, MakeResourceCommand().run(listOf("Order", "--base=$base", "--lang=java")))
        assertTrue(Files.exists(base.resolve("src/main/java/app/http/resources/OrderResource.java")))
        assertEquals(0, MakeResourceCommand().run(listOf("Invoice", "--base=$base", "--collection", "--lang=java")))
        val javaCollection = Files.readString(base.resolve("src/main/java/app/http/resources/InvoiceResource.java"))
        assertTrue(javaCollection.contains("static ResourceCollection collection"))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:resource" })
    }

    @Test
    fun makeMiddlewareAndRequest() {
        val base = temp.resolve("mw-req")
        Files.createDirectories(base.resolve("src/main/kotlin/app"))
        assertEquals(0, MakeMiddlewareCommand().run(listOf("Auth", "--base=$base", "--lang=kotlin")))
        assertEquals(0, MakePolicyCommand().run(listOf("Post", "--base=$base", "--lang=kotlin", "--model=Post")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/policies/PostPolicy.kt")))
        assertTrue(Files.readString(base.resolve("src/main/kotlin/app/policies/PostPolicy.kt")).contains("import app.models.Post"))
        assertEquals(0, MakePolicyCommand().run(listOf("Article", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/policies/ArticlePolicy.kt")))
        assertEquals(0, MakePolicyCommand().run(listOf("CommentPolicy", "--base=$base", "--lang=java")))
        assertTrue(Files.exists(base.resolve("src/main/java/app/policies/CommentPolicy.java")))
        assertEquals(0, MakePolicyCommand().run(listOf("Tag", "--base=$base", "--lang=java", "--model=Tag")))
        assertTrue(Files.readString(base.resolve("src/main/java/app/policies/TagPolicy.java")).contains("import app.models.Tag;"))
        assertEquals(1, GrindApplication().run(arrayOf("make:policy")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/http/middleware/AuthMiddleware.kt")))
        assertTrue(Files.readString(base.resolve("src/main/kotlin/app/http/middleware/AuthMiddleware.kt")).contains("Middleware"))
        assertEquals(0, MakeMiddlewareCommand().run(listOf("LogMiddleware", "--base=$base", "--lang=java")))
        assertTrue(Files.exists(base.resolve("src/main/java/app/http/middleware/LogMiddleware.java")))
        assertEquals(1, GrindApplication().run(arrayOf("make:middleware", "Auth", "--base=$base", "--lang=kotlin")))
        assertEquals(1, GrindApplication().run(arrayOf("make:middleware")))
        assertEquals(1, GrindApplication().run(arrayOf("make:middleware", "Bad-Name", "--base=$base")))

        assertEquals(0, MakeRequestCommand().run(listOf("StoreUser", "--base=$base", "--lang=kotlin")))
        val ktReq = Files.readString(base.resolve("src/main/kotlin/app/http/requests/StoreUserRequest.kt"))
        assertTrue(ktReq.contains("FormRequest"))
        assertTrue(ktReq.contains("fun rules("))
        assertEquals(0, MakeRequestCommand().run(listOf("UpdatePostRequest", "--base=$base", "--lang=java")))
        assertTrue(Files.readString(base.resolve("src/main/java/app/http/requests/UpdatePostRequest.java")).contains("extends FormRequest"))
        assertEquals(1, GrindApplication().run(arrayOf("make:request", "StoreUser", "--base=$base", "--lang=kotlin")))
        assertEquals(1, GrindApplication().run(arrayOf("make:request")))
        assertEquals(1, GrindApplication().run(arrayOf("make:request", "Bad-Name", "--base=$base")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:middleware" } && ListCommand.COMMANDS.any { it.first == "make:policy" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:request" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "storage:link" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "deploy:systemd" })
    }

    @Test
    fun storageLink() {
        val base = temp.resolve("storage-proj")
        Files.createDirectories(base.resolve("public"))
        assertEquals(0, StorageLinkCommand().run(listOf("--base=$base")))
        val link = base.resolve("public/storage")
        assertTrue(Files.isSymbolicLink(link))
        assertEquals(
            base.resolve("storage/app/public").normalize(),
            Files.readSymbolicLink(link).normalize(),
        )
        assertEquals(0, StorageLinkCommand().run(listOf("--base=$base")))
        val conflict = temp.resolve("conflict-proj")
        Files.createDirectories(conflict.resolve("public"))
        Files.writeString(conflict.resolve("public/storage"), "file")
        assertThrows(GrindException::class.java) {
            StorageLinkCommand().run(listOf("--base=$conflict"))
        }
        val wrongLink = temp.resolve("wrong-link")
        Files.createDirectories(wrongLink.resolve("public"))
        Files.createDirectories(wrongLink.resolve("storage/app/other"))
        Files.createSymbolicLink(
            wrongLink.resolve("public/storage"),
            wrongLink.resolve("storage/app/other"),
        )
        assertThrows(GrindException::class.java) {
            StorageLinkCommand().run(listOf("--base=$wrongLink"))
        }
        val plain = temp.resolve("plain-public")
        Files.createDirectories(plain)
        assertEquals(0, StorageLinkCommand().run(listOf("--base=$plain")))
        assertTrue(Files.isSymbolicLink(plain.resolve("public/storage")))
        assertEquals(0, GrindApplication().run(arrayOf("storage:link", "--base=$base")))
        val cwd = temp.resolve("cwd-link").toAbsolutePath()
        Files.createDirectories(cwd)
        assertEquals(0, StorageLinkCommand(cwd).run(emptyList()))
        assertTrue(Files.isSymbolicLink(cwd.resolve("public/storage")))
        val legacy = temp.resolve("legacy-public")
        Files.createDirectories(legacy.resolve("src/main/resources/public"))
        assertEquals(0, StorageLinkCommand().run(listOf("--base=$legacy")))
        assertTrue(Files.isSymbolicLink(legacy.resolve("src/main/resources/public/storage")))
        val both = temp.resolve("both-public")
        Files.createDirectories(both.resolve("public"))
        Files.createDirectories(both.resolve("src/main/resources/public"))
        assertEquals(0, StorageLinkCommand().run(listOf("--base=$both")))
        assertTrue(Files.isSymbolicLink(both.resolve("public/storage")))
        assertFalse(Files.exists(both.resolve("src/main/resources/public/storage")))
        assertTrue(StorageLinkCommand().javaClass.name.contains("StorageLinkCommand"))
    }

    @Test
    fun downAndUp() {
        val base = temp.resolve("maint")
        assertEquals(0, DownCommand().run(listOf("--base=$base")))
        assertTrue(Files.isRegularFile(base.resolve("storage/framework/down")))
        assertEquals(0, UpCommand().run(listOf("--base=$base")))
        assertFalse(Files.exists(base.resolve("storage/framework/down")))
        assertEquals(0, UpCommand().run(listOf("--base=$base")))
        val cwd = temp.resolve("maint-cwd").toAbsolutePath()
        Files.createDirectories(cwd)
        assertEquals(0, DownCommand(cwd).run(emptyList()))
        assertTrue(Files.isRegularFile(cwd.resolve("storage/framework/down")))
        assertEquals(0, UpCommand(cwd).run(emptyList()))
        assertEquals(0, GrindApplication().run(arrayOf("down", "--base=$base")))
        assertEquals(0, GrindApplication().run(arrayOf("up", "--base=$base")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "down" && it.second.contains("maintenance") })
        assertTrue(ListCommand.COMMANDS.any { it.first == "up" })
        DownCommand::class.java.getDeclaredConstructor().newInstance()
        UpCommand::class.java.getDeclaredConstructor().newInstance()
    }

    @Test
    fun makeView() {
        val base = temp.resolve("views-proj")
        Files.createDirectories(base)
        assertEquals(0, MakeViewCommand().run(listOf("home", "--base=$base")))
        assertTrue(Files.exists(base.resolve("src/main/resources/views/home.crema.html")))
        assertEquals(0, MakeViewCommand().run(listOf("posts.show", "--base=$base")))
        assertTrue(Files.exists(base.resolve("src/main/resources/views/posts/show.crema.html")))
        assertEquals(0, MakeViewCommand().run(listOf("brew/todos", "--base=$base")))
        assertTrue(Files.exists(base.resolve("src/main/resources/views/brew/todos.crema.html")))
        assertEquals(1, GrindApplication().run(arrayOf("make:view", "home", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:view")))
        assertEquals(1, GrindApplication().run(arrayOf("make:view", "Bad-Name", "--base=$base")))
        assertEquals("CreateUsersTable", Names.studly("create_users_table"))
        assertEquals("AB", Names.studly("a__b"))
        assertEquals("create_users_table", Names.snake("create_users_table"))
        assertEquals("create_users_table", Names.snake("CreateUsersTable"))
        assertEquals("http_response", Names.snake("HTTPResponse"))
        val parsed = Names.parseMigrationInput("CreateFlightsTable")
        assertEquals("CreateFlightsTable", parsed.descriptive)
        assertEquals("create_flights_table", parsed.snake)
        assertEquals(null, parsed.timestamp)
        val stamped = Names.parseMigrationInput("M2026_10_20_141530_create_gates_table")
        assertEquals("2026_10_20_141530", stamped.timestamp)
        assertEquals("CreateGatesTable", stamped.descriptive)
        assertEquals("create_gates_table", stamped.snake)
        val pascal = Names.parseMigrationInput("AddStatusToUsersTable")
        assertEquals("AddStatusToUsersTable", pascal.descriptive)
        assertEquals("add_status_to_users_table", pascal.snake)
        assertEquals(0, MakeMigrationCommand().run(listOf("create_widgets_table", "--base=$base", "--lang=kotlin")))
        assertEquals(0, MakeMigrationCommand().run(listOf("add_name_to_widgets_table", "--base=$base", "--lang=kotlin")))
        val migrations = base.resolve("src/main/kotlin/database/migrations")
        assertTrue(
            Files.list(migrations).use { stream ->
                stream.anyMatch { it.fileName.toString().endsWith("_create_widgets_table.kt") }
            },
        )
        Files.createDirectories(temp.resolve("empty-mig/src/main/kotlin/database/migrations"))
        assertEquals(
            0,
            MakeMigrationCommand().run(
                listOf("CreateEmptyDirTable", "--base=${temp.resolve("empty-mig")}", "--lang=kotlin"),
            ),
        )
    }

    @Test
    fun serveRequiresProject() {
        val empty = temp.resolve("empty")
        Files.createDirectories(empty)
        assertEquals(1, GrindApplication().run(arrayOf("serve", "--base=$empty")))
        assertNull(ServeCommand.findProjectRoot(empty))
        val nested = fixtureProject(temp.resolve("root")).resolve("src/main/kotlin/app/http")
        Files.createDirectories(nested)
        assertEquals(temp.resolve("root"), ServeCommand.findProjectRoot(nested))
        listOf("settings.gradle", "build.gradle.kts", "build.gradle").forEach { marker ->
            val dir = temp.resolve("m-$marker")
            Files.createDirectories(dir)
            Files.writeString(dir.resolve(marker), "")
            assertEquals(dir, ServeCommand.findProjectRoot(dir))
        }
    }

    @Test
    fun serveSpawnsGradleRunInProjectRoot() {
        val calls = mutableListOf<Triple<List<String>, Path, Map<String, String>>>()
        val runner = ProcessRunner { command, dir, env ->
            calls += Triple(command, dir, env)
            7
        }
        val app = stubGradleApp(temp.resolve("spawned"))
        Files.createDirectories(app.resolve("routes/nested"))

        val out = java.io.ByteArrayOutputStream()
        val serve = ServeCommand(runner = runner, windows = false, io = GrindIO(java.io.PrintStream(out), color = false))
        assertEquals(7, serve.run(listOf("--base=${app.resolve("routes/nested")}", "--no-watch", "--host=0.0.0.0", "--port=9000")))
        assertEquals(
            listOf(
                app.resolve("gradlew").toString(),
                "--quiet",
                "--console=plain",
                "-Dorg.gradle.console=plain",
                "run",
            ),
            calls[0].first,
        )
        assertEquals(app, calls[0].second)
        assertEquals(mapOf("HOST" to "0.0.0.0", "PORT" to "9000"), calls[0].third)
        val banner = out.toString()
        assertTrue(banner.contains("grind serve"), banner)
        assertTrue(banner.contains("http://localhost:9000"), banner)
        assertTrue(banner.contains("Bind     0.0.0.0:9000"), banner)

        // Preferred defaults come from ServePorts (do not require port 4000 to be free on the host).
        val (defaultHost, defaultPort) = ServePorts.preferredHostPort(app, emptyMap())
        assertEquals("127.0.0.1", defaultHost)
        assertEquals(DEFAULT_SERVE_PORT, defaultPort)
        val freePort = (29100..29200).first { ServePorts.isAvailable("127.0.0.1", it) }
        assertEquals(7, serve.run(listOf("--base=$app", "--no-watch", "--port=$freePort")))
        assertEquals("127.0.0.1", calls[1].third["HOST"])
        assertEquals(freePort.toString(), calls[1].third["PORT"])
        assertTrue(out.toString().contains("http://localhost:$freePort"), out.toString())

        assertEquals(
            listOf(
                app.resolve("gradlew.bat").toString(),
                "--quiet",
                "--console=plain",
                "-Dorg.gradle.console=plain",
                "run",
            ),
            ServeCommand(windows = true).gradleCommand(app),
        )
        val noWrapper = fixtureProject(temp.resolve("nowrapper"))
        assertEquals(
            listOf("gradle", "--quiet", "--console=plain", "-Dorg.gradle.console=plain", "run"),
            ServeCommand(windows = false).gradleCommand(noWrapper),
        )
    }

    @Test
    fun serveAnnounceReadyVsStarting() {
        val out = java.io.ByteArrayOutputStream()
        val serve = ServeCommand(io = GrindIO(java.io.PrintStream(out), color = false))
        serve.announce("127.0.0.1", 4000, ready = false, lanIp = null, wsl = false)
        val starting = out.toString()
        assertTrue(starting.contains("Building and starting"), starting)
        assertTrue(starting.contains("http://localhost:4000"), starting)
        assertFalse(starting.contains("WSL tip"), starting)
        assertFalse(starting.contains("Network"), starting)
        out.reset()
        serve.announce("127.0.0.1", 4000, ready = true, lanIp = null, wsl = true)
        val ready = out.toString()
        assertTrue(ready.contains("Server ready at http://localhost:4000"), ready)
        assertTrue(ready.contains("WSL tip"), ready)
        out.reset()
        // Wildcard + LAN: Network + Bind lines
        serve.announce("0.0.0.0", 9000, ready = false, lanIp = "10.0.0.5", wsl = false)
        val network = out.toString()
        assertTrue(network.contains("Network  http://10.0.0.5:9000"), network)
        assertTrue(network.contains("Bind     0.0.0.0:9000"), network)
        assertFalse(network.contains("WSL tip"), network)
        out.reset()
        // Wildcard without LAN: Bind only (no Network)
        serve.announce("0.0.0.0", 9000, ready = true, lanIp = null, wsl = true)
        val noLan = out.toString()
        assertTrue(noLan.contains("Bind     0.0.0.0:9000"), noLan)
        assertFalse(noLan.contains("Network"), noLan)
        assertFalse(noLan.contains("WSL tip"), noLan) // wildcard bind → no WSL tip
        out.reset()
        // LAN equals local URL → do not duplicate Network line
        serve.announce("0.0.0.0", 4000, ready = false, lanIp = "localhost", wsl = false)
        val same = out.toString()
        assertFalse(same.contains("Network"), same)
        out.reset()
        serve.announce("127.0.0.1", 4000, ready = true, lanIp = null, wsl = false, watching = true)
        val watching = out.toString()
        assertTrue(watching.contains("Reload   save .kt"), watching)
    }

    @Test
    fun systemProcessRunner() {
        assertEquals(3, ProcessRunner.SYSTEM.run(listOf("sh", "-c", "exit 3"), temp, emptyMap()))
        assertEquals(
            0,
            ProcessRunner.SYSTEM.run(listOf("sh", "-c", "test \"\$KONA_T\" = yes && test -n \"\$PWD\""), temp, mapOf("KONA_T" to "yes")),
        )
        assertThrows(GrindException::class.java) {
            ProcessRunner.SYSTEM.run(listOf("kona-definitely-missing-binary"), temp, emptyMap())
        }
    }

    @Test
    fun managedProcessRunnerDestroysTreeOnInterrupt() {
        val pidFile = temp.resolve("child.pid")
        val started = temp.resolve("child.started")
        val thread = Thread {
            ProcessRunner.SYSTEM.run(
                listOf(
                    "sh",
                    "-c",
                    "echo $$ > '${pidFile.toAbsolutePath()}'; touch '${started.toAbsolutePath()}'; sleep 120",
                ),
                temp,
                emptyMap(),
            )
        }
        thread.start()
        val deadline = System.currentTimeMillis() + 10_000
        while (!Files.exists(started) && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        assertTrue(Files.exists(started), "child should have started")
        val childPid = Files.readString(pidFile).trim().toLong()
        thread.interrupt()
        thread.join(10_000)
        assertTrue(!thread.isAlive, "serve waiter should finish after interrupt")
        assertTrue(!ProcessHandle.of(childPid).map { it.isAlive }.orElse(false), "child tree must die")
    }

    @Test
    fun managedProcessRunnerShutdownHookAndEdgeBranches() {
        val destroyed = mutableListOf<Process>()
        val hooks = mutableListOf<Thread>()
        val runner = ManagedProcessRunner(
            addShutdownHook = { hooks += it },
            removeShutdownHook = { hooks.remove(it) },
            destroyTree = {
                destroyed += it
                ProcessTrees.destroy(it)
            },
        )
        assertEquals(0, runner.run(listOf("sh", "-c", "exit 0"), temp, emptyMap()))
        assertTrue(hooks.isEmpty(), "hook removed after clean exit")
        assertEquals(1, destroyed.size, "finally always destroys the process tree")

        // Hook install fails while child already exited → return exitValue (0).
        val hookFailDead = ManagedProcessRunner(
            addShutdownHook = { throw IllegalStateException("shutting down") },
            destroyTree = ProcessTrees::destroy,
        )
        assertEquals(0, hookFailDead.run(listOf("true"), temp, emptyMap()))

        // Hook install fails while destroy leaves child alive → 143.
        val hookFailAlive = ManagedProcessRunner(
            addShutdownHook = { throw IllegalStateException("shutting down") },
            destroyTree = { },
        )
        assertEquals(143, hookFailAlive.run(listOf("sh", "-c", "sleep 60"), temp, emptyMap()))

        val removeFails = ManagedProcessRunner(
            addShutdownHook = { },
            removeShutdownHook = { throw IllegalStateException("already shutting down") },
            destroyTree = ProcessTrees::destroy,
        )
        assertEquals(0, removeFails.run(listOf("true"), temp, emptyMap()))

        // Interrupt while destroy leaves the child alive → wait times out → 143.
        val stubborn = ManagedProcessRunner(
            addShutdownHook = { },
            removeShutdownHook = { },
            destroyTree = { },
        )
        val code = intArrayOf(-1)
        val waiter = Thread {
            code[0] = stubborn.run(listOf("sh", "-c", "sleep 60"), temp, emptyMap())
        }
        waiter.start()
        val startedAt = System.currentTimeMillis()
        while (waiter.state == Thread.State.NEW && System.currentTimeMillis() - startedAt < 2_000) {
            Thread.sleep(10)
        }
        Thread.sleep(150)
        waiter.interrupt()
        waiter.join(10_000)
        assertTrue(!waiter.isAlive)
        assertEquals(143, code[0])

        val nested = ProcessBuilder("sh", "-c", "sleep 60").directory(temp.toFile()).start()
        ProcessTrees.destroy(nested)
        assertTrue(nested.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(!nested.isAlive)

        ProcessHandle.current().descendants()
            .filter { handle ->
                handle.info().commandLine().orElse("").contains("sleep 60")
            }
            .forEach { it.destroyForcibly() }
    }

    @Test
    fun serveInProcessRunsBootstrapAndReadsDotEnv() {
        val port = freePort()
        val base = fixtureProject(temp.resolve("inproc"), "APP_NAME=Fixture\nHOST=127.0.0.1\nPORT=$port\n")
        var body = ""
        val out = java.io.ByteArrayOutputStream()
        val serve = ServeCommand(
            awaitShutdown = { server ->
                assertTrue(server.isRunning())
                body = httpGet("http://127.0.0.1:$port/fixture").body()
            },
            io = GrindIO(java.io.PrintStream(out), color = false),
        )
        assertEquals(0, serve.run(listOf("--base=$base", "--in-process", "--bootstrap=${FixtureApp::class.java.name}")))
        assertEquals("fixture:Fixture", body)
        assertTrue(out.toString().contains("Server ready at http://localhost:$port"), out.toString())

        val port2 = freePort()
        assertEquals(
            0,
            GrindApplication().run(
                arrayOf("serve", "--base=$base", "--in-process", "--bootstrap=${FixtureApp::class.java.name}", "--port=$port2", "--host=127.0.0.1", "--once"),
            ),
        )
    }

    @Test
    fun serveInProcessPortBusyOffersNextOrAborts() {
        occupyPortWithFreeSuccessor().use { occupied ->
            val port = occupied.localPort
            val next = port + 1
            val base = fixtureProject(temp.resolve("occupied"), "HOST=127.0.0.1\nPORT=$port\n")
            val out = java.io.ByteArrayOutputStream()
            assertThrows(GrindException::class.java) {
                ServeCommand(
                    prompts = Prompts(java.io.ByteArrayInputStream("n\n".toByteArray()), java.io.PrintStream(out), color = false),
                    io = GrindIO(java.io.PrintStream(out), color = false),
                ).run(listOf("--base=$base", "--in-process", "--once", "--bootstrap=${FixtureApp::class.java.name}"))
            }
            var body = ""
            assertEquals(
                0,
                ServeCommand(
                    awaitShutdown = { server ->
                        assertEquals(next, server.port())
                        body = httpGet("http://127.0.0.1:$next/fixture").body()
                    },
                    prompts = Prompts(java.io.ByteArrayInputStream("y\n".toByteArray()), java.io.PrintStream(out), color = false),
                    io = GrindIO(java.io.PrintStream(out), color = false),
                ).run(listOf("--base=$base", "--in-process", "--bootstrap=${FixtureApp::class.java.name}")),
            )
            assertEquals("fixture:Kona", body)
        }
    }

    @Test
    fun serveInProcessDefaultWaitIsInterruptible() {
        val port = freePort()
        val base = fixtureProject(temp.resolve("blocking"), "PORT=$port\n")
        var failure: Throwable? = null
        val thread = Thread {
            try {
                ServeCommand().run(listOf("--base=$base", "--in-process", "--bootstrap=${FixtureApp::class.java.name}"))
            } catch (t: Throwable) {
                failure = t
            }
        }
        thread.start()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val up = runCatching { httpGet("http://127.0.0.1:$port/fixture").statusCode() == 200 }.getOrDefault(false)
            if (up) break
            Thread.sleep(50)
        }
        thread.interrupt()
        thread.join(10_000)
        assertInstanceOf(InterruptedException::class.java, failure)
        assertThrows(java.net.ConnectException::class.java) { httpGet("http://127.0.0.1:$port/fixture") }
    }

    @Test
    fun appLoaderErrors() {
        val noClasses = temp.resolve("noclasses")
        Files.createDirectories(noClasses)
        Files.writeString(noClasses.resolve("build.gradle.kts"), "")
        assertTrue(AppLoader(noClasses).classpath().isEmpty())
        assertThrows(GrindException::class.java) { AppLoader(noClasses).create(emptyArray()) }
        assertEquals(1, GrindApplication().run(arrayOf("serve", "--base=$noClasses", "--in-process")))

        val base = fixtureProject(temp.resolve("loader"))
        Files.createDirectories(base.resolve("build/classes/java/main"))
        Files.createDirectories(base.resolve("build/resources/main"))
        assertEquals(3, AppLoader(base).classpath().size)
        val jar = temp.resolve("extra.jar")
        Files.writeString(jar, "x")
        val withRuntime = AppLoader(base, FixtureApp::class.java.name) { listOf(jar) }
        assertEquals(4, withRuntime.classpath().size)
        val args = arrayOf("--base=$base")
        assertThrows(GrindException::class.java) { AppLoader(base).create(args) }
        assertThrows(GrindException::class.java) { AppLoader(base, NoCreateApp::class.java.name).create(args) }
        assertThrows(GrindException::class.java) { AppLoader(base, InstanceCreateApp::class.java.name).create(args) }
        assertThrows(GrindException::class.java) { AppLoader(base, WrongReturnApp::class.java.name).create(args) }
        val boom = assertThrows(IllegalStateException::class.java) {
            AppLoader(base, ThrowingApp::class.java.name).create(args)
        }
        assertEquals("boom 1", boom.message)
    }

    @Test
    fun appLoaderParsesRuntimeClasspath() {
        assertTrue(AppLoader.parseClasspathLine("").isEmpty())
        assertTrue(AppLoader.parseClasspathLine("noise\nmore").isEmpty())
        val a = temp.resolve("a.jar")
        val b = temp.resolve("b.jar")
        Files.writeString(a, "a")
        Files.writeString(b, "b")
        val sep = java.io.File.pathSeparator
        val parsed = AppLoader.parseClasspathLine("ignore\n${a.toAbsolutePath()}$sep${b.toAbsolutePath()}\n")
        assertEquals(listOf(a.toAbsolutePath(), b.toAbsolutePath()), parsed.map { it.toAbsolutePath() })
        val missing = temp.resolve("missing.jar")
        assertEquals(
            1,
            AppLoader.parseClasspathLine("${a.toAbsolutePath()}$sep${missing.toAbsolutePath()}").size,
        )
        val cache = temp.resolve("cp.txt")
        Files.writeString(cache, "${a.toAbsolutePath()}$sep${b.toAbsolutePath()}")
        assertEquals(2, AppLoader.parseClasspathFile(cache).size)
        // Missing wrapper → empty runtime list
        val bare = fixtureProject(temp.resolve("nort"))
        assertTrue(AppLoader.gradleRuntimeClasspath(bare).isEmpty())

        // Cached classpath file short-circuits Gradle
        val cachedProj = fixtureProject(temp.resolve("cached"))
        val cachedFile = cachedProj.resolve("build/kona-runtime.classpath")
        Files.createDirectories(cachedFile.parent)
        Files.writeString(cachedFile, a.toAbsolutePath().toString())
        assertEquals(listOf(a.toAbsolutePath()), AppLoader.gradleRuntimeClasspath(cachedProj).map { it.toAbsolutePath() })

        assertNull(AppLoader.resolveGradleWrapper(bare))
        val batOnly = fixtureProject(temp.resolve("bat"))
        val bat = batOnly.resolve("gradlew.bat")
        Files.writeString(bat, "@echo off\n")
        assertEquals(bat.toAbsolutePath(), AppLoader.resolveGradleWrapper(batOnly)?.toAbsolutePath())

        // gradlew present but konaClasspath fails → empty
        val failProj = fixtureProject(temp.resolve("failcp"))
        val failWrap = failProj.resolve("gradlew")
        Files.writeString(failWrap, "#!/bin/sh\nexit 1\n")
        failWrap.toFile().setExecutable(true)
        assertTrue(AppLoader.gradleRuntimeClasspath(failProj).isEmpty())

        // exit 0 but no classpath line → empty, no cache write
        val emptyProj = fixtureProject(temp.resolve("emptycp"))
        val emptyWrap = emptyProj.resolve("gradlew")
        Files.writeString(emptyWrap, "#!/bin/sh\necho nope\n")
        emptyWrap.toFile().setExecutable(true)
        assertTrue(AppLoader.gradleRuntimeClasspath(emptyProj).isEmpty())
        AppLoader.writeClasspathCache(emptyProj.resolve("build/kona-runtime.classpath"), emptyList())

        // ProcessBuilder start failure (empty command / non-directory working dir)
        assertNull(AppLoader.startClasspathProcess(Path.of(""), temp.resolve("nort")))
        val fileRoot = temp.resolve("not-a-dir")
        Files.writeString(fileRoot, "x")
        val anyWrap = temp.resolve("wrap.sh")
        Files.writeString(anyWrap, "#!/bin/sh\n")
        anyWrap.toFile().setExecutable(true)
        assertNull(AppLoader.startClasspathProcess(anyWrap, fileRoot))

        // starter returns null → empty classpath
        val nullStarterProj = fixtureProject(temp.resolve("nullstarter"))
        Files.writeString(nullStarterProj.resolve("gradlew"), "#!/bin/sh\n")
        nullStarterProj.resolve("gradlew").toFile().setExecutable(true)
        assertTrue(AppLoader.gradleRuntimeClasspath(nullStarterProj) { _, _ -> null }.isEmpty())

        // cache write failure: build exists as a file
        val badCache = temp.resolve("badcache-file")
        Files.writeString(badCache, "x")
        AppLoader.writeClasspathCache(badCache.resolve("child"), listOf(a))

        // gradlew prints a classpath line → parsed + cached
        val okProj = fixtureProject(temp.resolve("okcp"))
        val okWrap = okProj.resolve("gradlew")
        Files.writeString(
            okWrap,
            "#!/bin/sh\necho '${a.toAbsolutePath()}$sep${b.toAbsolutePath()}'\n",
        )
        okWrap.toFile().setExecutable(true)
        val resolved = AppLoader.gradleRuntimeClasspath(okProj)
        assertEquals(2, resolved.size)
        assertTrue(Files.isRegularFile(okProj.resolve("build/kona-runtime.classpath")))

        // empty path segments filtered
        assertEquals(
            1,
            AppLoader.parseClasspathLine("${a.toAbsolutePath()}$sep$sep${missing.toAbsolutePath()}").size,
        )
    }

    @Test
    fun parseOptionsShape() {
        val (pos, opt) = parseOptions(listOf("a", "--flag", "--k=v"))
        assertEquals(listOf("a"), pos)
        assertEquals("true", opt["flag"])
        assertEquals("v", opt["k"])

        val (_, short) = parseOptions(listOf("Post", "-mcr", "--seed"))
        assertEquals("true", short["migration"])
        assertEquals("true", short["controller"])
        assertEquals("true", short["resource"])
        assertEquals("true", short["seeder"])

        val (_, all) = parseOptions(listOf("-a", "-fsp"))
        assertEquals("true", all["all"])
        assertEquals("true", all["factory"])
        assertEquals("true", all["seeder"])
        assertEquals("true", all["policy"])

        val (_, requests) = parseOptions(listOf("Post", "-mcrR", "--api"))
        assertEquals("true", requests["migration"])
        assertEquals("true", requests["controller"])
        assertEquals("true", requests["resource"])
        assertEquals("true", requests["requests"])
        assertEquals("true", requests["api"])

        val (_, watch) = parseOptions(listOf("serve", "-w"))
        assertEquals("true", watch["watch"])
    }

    @Test
    fun makeFireflyLayout() {
        val base = temp.resolve("firefly-layout")
        Files.createDirectories(base)
        assertEquals(0, MakeFireflyLayoutCommand().run(listOf("Banner", "--base=$base", "--lang=kotlin")))
        val source = Files.readString(base.resolve("src/main/kotlin/app/firefly/layouts/Banner.kt"))
        val view = Files.readString(base.resolve("src/main/resources/views/firefly/layouts/banner.crema.html"))
        assertTrue(source.contains("class Banner"))
        assertTrue(view.contains("x-data=\"ffField\""))
        assertTrue(view.contains("@entangle"))
        assertEquals(0, MakeFireflyLayoutCommand().run(listOf("Notice", "--base=$base", "--lang=java")))
        assertTrue(Files.exists(base.resolve("src/main/java/app/firefly/layouts/Notice.java")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-layout", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-layout", "Bad-Name", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-layout", "Banner", "--base=$base", "--lang=kotlin")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:firefly-layout" })
    }

    @Test
    fun makeFireflyWidget() {
        val base = temp.resolve("firefly-widget")
        Files.createDirectories(base)
        assertEquals(0, MakeFireflyWidgetCommand().run(listOf("Revenue", "--base=$base", "--lang=kotlin")))
        val source = Files.readString(base.resolve("src/main/kotlin/app/panels/admin/widgets/Revenue.kt"))
        val view = Files.readString(base.resolve("src/main/resources/views/firefly/widgets/revenue.crema.html"))
        assertTrue(source.contains("class Revenue"))
        assertTrue(source.contains(": Widget("))
        assertTrue(view.contains("x-data=\"ffWidget\""))
        assertEquals(0, MakeFireflyWidgetCommand().run(listOf("Orders", "--base=$base", "--lang=java")))
        assertTrue(Files.readString(base.resolve("src/main/java/app/panels/admin/widgets/Orders.java")).contains("extends Widget"))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-widget", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-widget", "Bad-Name", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-widget", "Revenue", "--base=$base", "--lang=kotlin")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:firefly-widget" })
    }

    @Test
    fun makeFireflyPanel() {
        val base = temp.resolve("firefly-panel")
        Files.createDirectories(base)
        assertEquals(0, MakeFireflyPanelCommand().run(listOf("--base=$base", "--lang=kotlin", "--id=desk", "--path=")))
        val source = Files.readString(base.resolve("src/main/kotlin/app/panels/desk/Panel.kt"))
        assertTrue(source.contains("class Panel"))
        assertTrue(source.contains("id(\"desk\")"))
        assertTrue(source.contains("path(\"\")"))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/panels/desk/pages/.gitkeep")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/panels/desk/widgets/.gitkeep")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/panels/desk/resources/.gitkeep")))
        assertEquals(0, MakeFireflyPanelCommand().run(listOf("Panel", "--base=${temp.resolve("firefly-panel-named")}", "--lang=java")))
        val javaPanel = Files.readString(temp.resolve("firefly-panel-named/src/main/java/app/panels/admin/Panel.java"))
        assertTrue(javaPanel.contains("extends org.konaframework.firefly.Panel"))
        assertFalse(javaPanel.contains("path("))
        assertEquals(0, MakeFireflyPanelCommand().run(listOf("--base=${temp.resolve("firefly-panel-java-path")}", "--lang=java", "--id=desk", "--path=staff")))
        assertTrue(Files.readString(temp.resolve("firefly-panel-java-path/src/main/java/app/panels/desk/Panel.java")).contains("path(\"staff\");"))
        assertThrows(GrindException::class.java) { MakeFireflyPanelCommand().run(listOf("Desk", "--base=$base")) }
        assertThrows(GrindException::class.java) { MakeFireflyPanelCommand().run(listOf("Bad-Name", "--base=$base")) }
        assertThrows(GrindException::class.java) { MakeFireflyPanelCommand().run(listOf("--base=${temp.resolve("blank-id")}", "--id=")) }
        assertThrows(GrindException::class.java) { MakeFireflyPanelCommand().run(listOf("--base=${temp.resolve("bad-id")}", "--id=bad-name")) }
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-panel", "--base=$base", "--lang=kotlin", "--id=desk")))
        assertEquals(0, FireflyInstallCommand().run(listOf("--base=${temp.resolve("firefly-install")}", "--lang=kotlin", "--id=desk", "--path=staff")))
        assertTrue(Files.readString(temp.resolve("firefly-install/src/main/kotlin/app/panels/desk/Panel.kt")).contains("path(\"staff\")"))
        assertEquals(0, FireflyInstallCommand().run(listOf("--base=${temp.resolve("firefly-install-default")}", "--lang=java")))
        assertFalse(Files.readString(temp.resolve("firefly-install-default/src/main/java/app/panels/admin/Panel.java")).contains("path("))
        assertThrows(GrindException::class.java) { FireflyInstallCommand().run(listOf("extra", "--base=$base")) }
        assertEquals(0, MakeFireflyPageCommand().run(listOf("Desk", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.readString(base.resolve("src/main/kotlin/app/panels/admin/pages/Desk.kt")).contains("CustomPage"))
        assertEquals(0, MakeFireflyPageCommand().run(listOf("Note", "--base=$base", "--lang=java")))
        assertTrue(Files.readString(base.resolve("src/main/java/app/panels/admin/pages/Note.java")).contains("extends CustomPage"))
        assertEquals(0, MakeFireflyPageCommand().run(listOf("Staff", "--base=$base", "--lang=kotlin", "--panel=staff")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/panels/staff/pages/Staff.kt")))
        assertEquals(0, MakeFireflyPageCommand().run(listOf("Blank", "--base=$base", "--panel=", "--lang=kotlin")))
        assertTrue(Files.exists(base.resolve("src/main/kotlin/app/panels/admin/pages/Blank.kt")))
        assertThrows(GrindException::class.java) { MakeFireflyPageCommand().run(listOf("Nope", "--base=$base", "--panel=bad-name")) }
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-page", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-page", "Bad-Name", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-page", "Desk", "--base=$base", "--lang=kotlin")))
        assertEquals(0, MakeFireflyThemeCommand().run(listOf("Harbor", "--base=$base", "--lang=kotlin")))
        assertTrue(Files.readString(base.resolve("src/main/kotlin/app/firefly/themes/Harbor.kt")).contains("FireflyTheme"))
        assertEquals(0, MakeFireflyThemeCommand().run(listOf("Slate", "--base=$base", "--lang=java")))
        assertTrue(Files.readString(base.resolve("src/main/java/app/firefly/themes/Slate.java")).contains("implements FireflyTheme"))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-theme", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-theme", "Bad-Name", "--base=$base")))
        assertEquals(0, FireflyPublishCommand().run(listOf("--base=$base")))
        assertTrue(Files.readString(base.resolve("src/main/resources/views/firefly/sidebar.crema.html")).contains("ff-hook:sidebar.start"))
        assertEquals(1, GrindApplication().run(arrayOf("firefly:publish", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("firefly:publish", "extra", "--base=$base")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:firefly-panel" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "firefly:install" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "firefly:publish" })
    }

    @Test
    fun makeFireflyResource() {
        val base = temp.resolve("firefly-resource")
        Files.createDirectories(base)
        assertEquals(0, MakeFireflyResourceCommand().run(listOf("Category", "--base=$base", "--lang=kotlin", "--simple")))
        val simple = Files.readString(base.resolve("src/main/kotlin/app/panels/admin/resources/Category.kt"))
        assertTrue(simple.contains("simple()"))
        assertTrue(simple.contains("ManagePage"))
        assertTrue(simple.contains("model(app.models.Category::class.java)"))
        assertFalse(simple.contains("RelationManager"))
        assertEquals(0, MakeFireflyResourceCommand().run(listOf("Order", "--base=$base", "--lang=kotlin")))
        val full = Files.readString(base.resolve("src/main/kotlin/app/panels/admin/resources/Order.kt"))
        assertTrue(full.contains("ListPage"))
        assertTrue(full.contains("RelationManager"))
        assertFalse(full.contains("simple()"))
        assertEquals(0, MakeFireflyResourceCommand().run(listOf("Note", "--base=$base", "--lang=java", "--simple")))
        val javaSimple = Files.readString(base.resolve("src/main/java/app/panels/admin/resources/Note.java"))
        assertTrue(javaSimple.contains("simple();"))
        assertTrue(javaSimple.contains("new ManagePage"))
        assertEquals(0, MakeFireflyResourceCommand().run(listOf("Invoice", "--base=$base", "--lang=java")))
        val javaFull = Files.readString(base.resolve("src/main/java/app/panels/admin/resources/Invoice.java"))
        assertTrue(javaFull.contains("new ViewPage"))
        assertTrue(javaFull.contains("new RelationManager"))
        assertThrows(GrindException::class.java) { MakeFireflyResourceCommand().run(listOf("--base=$base")) }
        assertThrows(GrindException::class.java) { MakeFireflyResourceCommand().run(listOf("Bad-Name", "--base=$base")) }
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-resource", "Category", "--base=$base", "--lang=kotlin", "--simple")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:firefly-resource" })
    }

    @Test
    fun makeFireflyField() {
        val base = temp.resolve("firefly-field")
        Files.createDirectories(base)
        assertEquals(0, MakeFireflyFieldCommand().run(listOf("Price", "--base=$base", "--lang=kotlin")))
        val source = Files.readString(base.resolve("src/main/kotlin/app/firefly/fields/Price.kt"))
        val view = Files.readString(base.resolve("src/main/resources/views/firefly/fields/price.crema.html"))
        assertTrue(source.contains("class Price"))
        assertTrue(source.contains("Field<Price>"))
        assertTrue(view.contains("x-data=\"ffField\""))
        assertTrue(view.contains("@entangle"))
        assertEquals(0, MakeFireflyFieldCommand().run(listOf("Note", "--base=$base", "--lang=java")))
        assertTrue(Files.readString(base.resolve("src/main/java/app/firefly/fields/Note.java")).contains("extends Field<Note>"))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-field", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-field", "Bad-Name", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-field", "Price", "--base=$base", "--lang=kotlin")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:firefly-field" })
    }

    @Test
    fun makeFireflyAction() {
        val base = temp.resolve("firefly-action")
        Files.createDirectories(base)
        assertEquals(0, MakeFireflyActionCommand().run(listOf("SaveRecord", "--base=$base", "--lang=kotlin")))
        val source = Files.readString(base.resolve("src/main/kotlin/app/firefly/actions/SaveRecord.kt"))
        val view = Files.readString(base.resolve("src/main/resources/views/firefly/actions/saverecord.crema.html"))
        assertTrue(source.contains("open class SaveRecord"))
        assertTrue(source.contains("Action(\"saveRecord\")"))
        assertTrue(view.contains("x-data=\"ffAction('saveRecord')\""))
        assertTrue(view.contains("wire:click=\"saveRecord\""))
        assertTrue(view.contains("x-data=\"{ open: false }\""))
        assertEquals(0, MakeFireflyActionCommand().run(listOf("DeleteRecord", "--base=$base", "--lang=java")))
        val java = Files.readString(base.resolve("src/main/java/app/firefly/actions/DeleteRecord.java"))
        assertTrue(java.contains("extends Action"))
        assertTrue(java.contains("super(\"deleteRecord\")"))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-action", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-action", "Bad-Name", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-action", "SaveRecord", "--base=$base", "--lang=kotlin")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:firefly-action" })
    }

    @Test
    fun makeFireflyColumnAndFilter() {
        val base = temp.resolve("firefly-table")
        Files.createDirectories(base)
        assertEquals(0, MakeFireflyColumnCommand().run(listOf("Title", "--base=$base", "--lang=kotlin")))
        val column = Files.readString(base.resolve("src/main/kotlin/app/firefly/columns/Title.kt"))
        val columnView = Files.readString(base.resolve("src/main/resources/views/firefly/columns/title.crema.html"))
        assertTrue(column.contains("class Title"))
        assertTrue(columnView.contains("x-data=\"ffColumn"))
        assertTrue(columnView.contains("@entangle"))
        assertTrue(columnView.contains("\$wire.\$call"))
        assertEquals(0, MakeFireflyColumnCommand().run(listOf("Status", "--base=$base", "--lang=java")))
        assertTrue(Files.exists(base.resolve("src/main/java/app/firefly/columns/Status.java")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-column", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-column", "Bad-Name", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-column", "Title", "--base=$base", "--lang=kotlin")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:firefly-column" })

        assertEquals(0, MakeFireflyFilterCommand().run(listOf("Active", "--base=$base", "--lang=kotlin")))
        val filter = Files.readString(base.resolve("src/main/kotlin/app/firefly/filters/Active.kt"))
        val filterView = Files.readString(base.resolve("src/main/resources/views/firefly/filters/active.crema.html"))
        assertTrue(filter.contains("class Active"))
        assertTrue(filterView.contains("x-data=\"ffColumn"))
        assertTrue(filterView.contains("@entangle"))
        assertTrue(filterView.contains("\$wire.\$call"))
        assertEquals(0, MakeFireflyFilterCommand().run(listOf("Trashed", "--base=$base", "--lang=java")))
        assertTrue(Files.exists(base.resolve("src/main/java/app/firefly/filters/Trashed.java")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-filter", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-filter", "Bad-Name", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-filter", "Active", "--base=$base", "--lang=kotlin")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:firefly-filter" })
    }

    @Test
    fun makeFireflyEntry() {
        val base = temp.resolve("firefly-entry")
        Files.createDirectories(base)
        assertEquals(0, MakeFireflyEntryCommand().run(listOf("StatusEntry", "--base=$base", "--lang=kotlin")))
        val source = Files.readString(base.resolve("src/main/kotlin/app/firefly/entries/StatusEntry.kt"))
        val view = Files.readString(base.resolve("src/main/resources/views/firefly/entries/statusentry.crema.html"))
        assertTrue(source.contains("class StatusEntry"))
        assertTrue(source.contains("ff-view"))
        assertTrue(view.contains("x-data=\"ffEntry\""))
        assertTrue(view.contains("@entangle"))
        assertEquals(0, MakeFireflyEntryCommand().run(listOf("NoteEntry", "--base=$base", "--lang=java")))
        val java = Files.readString(base.resolve("src/main/java/app/firefly/entries/NoteEntry.java"))
        assertTrue(java.contains("extends Entry<NoteEntry>"))
        val fresh = temp.resolve("firefly-entry-default")
        Files.createDirectories(fresh)
        assertEquals(0, MakeFireflyEntryCommand().run(listOf("PlainEntry", "--base=$fresh")))
        assertTrue(Files.exists(fresh.resolve("src/main/kotlin/app/firefly/entries/PlainEntry.kt")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-entry", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-entry", "Bad-Name", "--base=$base")))
        assertEquals(1, GrindApplication().run(arrayOf("make:firefly-entry", "StatusEntry", "--base=$base", "--lang=kotlin")))
        assertTrue(ListCommand.COMMANDS.any { it.first == "make:firefly-entry" })
    }

    private fun stubGradleApp(dir: Path): Path {
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("settings.gradle.kts"), "rootProject.name = \"stub\"\n")
        Files.writeString(dir.resolve("gradlew"), "#!/bin/sh\n")
        Files.writeString(dir.resolve("gradlew.bat"), "@echo off\n")
        dir.resolve("gradlew").toFile().setExecutable(true)
        return dir
    }
}
