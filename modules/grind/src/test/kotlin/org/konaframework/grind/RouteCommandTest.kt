package org.konaframework.grind

import org.konaframework.core.Kona
import org.konaframework.http.Handler
import org.konaframework.http.HttpServiceProvider
import org.konaframework.http.Middleware
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.Router
import org.konaframework.support.console.ConsoleIO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

class RouteCommandTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun reset() {
        Kona.reset()
    }

    private fun capture(block: () -> Int): Pair<Int, String> {
        val originalOut = System.out
        val originalErr = System.err
        val buffer = ByteArrayOutputStream()
        val stream = PrintStream(buffer, true)
        System.setOut(stream)
        System.setErr(stream)
        try {
            return block() to buffer.toString()
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
    }

    private fun grind(vararg args: String): Pair<Int, String> =
        capture { GrindApplication().run(arrayOf(*args)) }

    private fun project(): Path = fixtureProject(temp.resolve("routes"))

    private fun boot(command: String, vararg extra: String): Pair<Int, String> {
        val args = mutableListOf(
            command,
            "--base=${project()}",
            "--bootstrap=${RouteListApp::class.java.name}",
        )
        args.addAll(extra)
        return grind(*args.toTypedArray())
    }

    @Test
    fun listTableJsonFiltersAndCache() {
        val listed = boot("route:list")
        assertEquals(0, listed.first)
        assertTrue(listed.second.contains("Routes"))
        assertTrue(listed.second.contains("/home"))
        assertTrue(listed.second.contains("users.show"))
        assertTrue(listed.second.contains("app.http.NamedShowHandler"))
        assertTrue(listed.second.contains("Closure"))
        assertTrue(listed.second.contains("NamedGate"))
        assertTrue(listed.second.contains("Showing"))
        assertTrue(ListCommand.COMMANDS.any { it.first == "route:list" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "route:cache" })
        assertTrue(ListCommand.COMMANDS.any { it.first == "route:clear" })

        val json = boot("route:list", "--json")
        assertEquals(0, json.first)
        assertTrue(json.second.contains("\"uri\":\"/home\""))
        assertTrue(json.second.contains("\"vendor\":true"))
        assertTrue(json.second.contains("NamedShowHandler"))

        val byMethod = boot("route:list", "--method=POST")
        assertTrue(byMethod.second.contains("/users"))
        assertFalse(byMethod.second.contains("/home"))

        val head = boot("route:list", "--method=HEAD", "--columns=method,uri")
        assertTrue(head.second.contains("/home"))
        assertTrue(head.second.contains("/anon"))

        val named = boot("route:list", "--name=users")
        assertTrue(named.second.contains("users.store"))
        assertTrue(named.second.contains("users.show"))
        assertFalse(named.second.contains("/home"))

        val path = boot("route:list", "--path=/admin")
        assertTrue(path.second.contains("/admin"))
        assertFalse(path.second.contains("/home"))

        val exceptPath = boot("route:list", "--except-path=/users,/admin")
        assertTrue(exceptPath.second.contains("/home"))
        assertFalse(exceptPath.second.contains("/users"))
        assertFalse(exceptPath.second.contains("/admin"))

        val appOnly = boot("route:list", "--except-vendor")
        assertTrue(appOnly.second.contains("users.show"))
        assertFalse(appOnly.second.contains("/home"))

        val vendorOnly = boot("route:list", "--only-vendor")
        assertTrue(vendorOnly.second.contains("/home"))
        assertFalse(vendorOnly.second.contains("NamedShowHandler"))

        val both = boot("route:list", "--except-vendor", "--only-vendor")
        assertTrue(both.second.contains("doesn't have any routes matching the given criteria"))

        val compact = boot("route:list", "--compact")
        assertTrue(compact.second.contains("Method"))
        assertTrue(compact.second.contains("URI"))
        assertFalse(compact.second.contains("Name  "))

        val sorted = boot("route:list", "--sort=name", "--reverse", "--columns=name,uri")
        assertEquals(0, sorted.first)
        val nameIdx = sorted.second.indexOf("users.update")
        val homeIdx = sorted.second.indexOf("home")
        assertTrue(nameIdx >= 0 && homeIdx >= 0)
        assertTrue(nameIdx < homeIdx)

        assertEquals(1, boot("route:list", "--sort=bogus").first)
        assertEquals(1, boot("route:list", "--columns=nope").first)

        val jsonFalse = boot("route:list", "--json=false")
        assertTrue(jsonFalse.second.contains("Routes"))

        val methods = boot("route:list", "--method=GET,OPTIONS", "--columns=method,uri")
        assertTrue(methods.second.contains("/ping"))
        assertTrue(methods.second.contains("/home"))

        val emptyCols = boot("route:list", "--columns=")
        assertTrue(emptyCols.second.contains("Action"))

        val eachSort = listOf("method", "uri", "action", "middleware")
        eachSort.forEach { key ->
            assertEquals(0, boot("route:list", "--sort=$key", "--quiet").first)
        }

        val cached = boot("route:cache")
        assertEquals(0, cached.first)
        assertTrue(cached.second.contains("Routes cached successfully."))
        val cacheFile = project().resolve(RouteCommand.DEFAULT_CACHE_PATH)
        assertTrue(Files.isRegularFile(cacheFile))
        val snapshot = Files.readString(cacheFile)
        assertTrue(snapshot.contains("/home"))
        assertTrue(snapshot.contains("NamedShowHandler"))

        assertEquals(0, boot("route:cache").first)

        val cleared = boot("route:clear")
        assertEquals(0, cleared.first)
        assertTrue(cleared.second.contains("Route cache cleared successfully."))
        assertFalse(Files.exists(cacheFile))
        assertEquals(0, boot("route:clear").first)
    }

    @Test
    fun emptyMissingRouterAndMissingApp() {
        val emptyProject = fixtureProject(temp.resolve("empty-routes"))
        val emptyBoot = "--bootstrap=${EmptyRouterApp::class.java.name}"
        val empty = grind("route:list", "--base=$emptyProject", emptyBoot)
        assertEquals(0, empty.first)
        assertTrue(empty.second.contains("Your application doesn't have any routes."))

        val emptyJson = grind("route:list", "--base=$emptyProject", emptyBoot, "--json")
        assertTrue(emptyJson.second.contains("[]"))

        listOf(
            arrayOf("--method=POST"),
            arrayOf("--name=missing"),
            arrayOf("--path=/missing"),
            arrayOf("--except-path=/x"),
            arrayOf("--except-vendor"),
            arrayOf("--only-vendor"),
        ).forEach { flags ->
            val filteredEmpty = grind("route:list", "--base=$emptyProject", emptyBoot, *flags)
            assertTrue(filteredEmpty.second.contains("matching the given criteria"), flags.contentToString())
        }

        val noRouter = grind(
            "route:list",
            "--base=${project()}",
            "--bootstrap=${NoQueueApp::class.java.name}",
        )
        assertEquals(1, noRouter.first)
        assertTrue(noRouter.second.contains("No Router bound"))

        val missing = grind("route:list", "--base=${temp.resolve("nowhere")}")
        assertEquals(1, missing.first)
        assertTrue(missing.second.contains("No Kona application found"))

        assertEquals(1, grind("route:cache", "--base=${temp.resolve("nowhere")}").first)
        assertEquals(1, grind("route:clear", "--base=${temp.resolve("nowhere")}").first)

        val flagSort = grind(
            "route:list",
            "--base=${project()}",
            "--bootstrap=${RouteListApp::class.java.name}",
            "--sort",
        )
        assertEquals(1, flagSort.first)
    }

    @Test
    fun listingHelpersCoverRowsAndColors() {
        val app = Kona.create(arrayOf("--base=${project()}")).providers(HttpServiceProvider()).boot()
        val router = app.resolve(Router::class.java)
        router.get("/x") { Response.text("x") }.name("x")
        router.post("/y", NamedHandler())
        router.get("/z") { Response.text("z") }.middleware(NamedGate(), Middleware { req, nxt -> nxt.handle(req) })
        val rows = RouteListing.rows(router)
        assertEquals(3, rows.size)
        assertEquals("Closure", rows[0].action)
        assertTrue(rows[0].vendor)
        assertEquals(NamedHandler::class.java.name, rows[1].action)
        assertTrue(rows[1].vendor)
        assertTrue(rows[2].middleware.contains("NamedGate"))
        assertTrue(rows[2].middleware.contains("Closure"))
        assertEquals("", rows[0].column("nope"))
        assertEquals("URI", RouteListing.header("uri"))
        assertEquals("Method", RouteListing.header("method"))
        assertEquals("", RouteListing.header(""))
        assertEquals(ConsoleIO.GREEN, RouteListing.methodColor("GET"))
        assertEquals(ConsoleIO.GREEN, RouteListing.methodColor("HEAD"))
        assertEquals(ConsoleIO.YELLOW, RouteListing.methodColor("POST"))
        assertEquals(ConsoleIO.CYAN, RouteListing.methodColor("PUT"))
        assertEquals(ConsoleIO.CYAN, RouteListing.methodColor("PATCH"))
        assertEquals(ConsoleIO.RED, RouteListing.methodColor("DELETE"))
        assertEquals(ConsoleIO.DIM, RouteListing.methodColor("OPTIONS"))
        val painted = RouteListing.paintMethods(GrindIO(color = false), "GET|POST|OPTIONS")
        assertEquals("GET|POST|OPTIONS", painted)
        val row = rows[0]
        assertEquals("GET", row.method)
        assertEquals("/x", row.column("uri"))
        assertEquals("x", row.column("name"))
        assertEquals("Closure", row.column("action"))
        assertEquals("", row.column("middleware"))
        assertEquals("GET", row.column("method"))
        assertTrue(RouteListing.matchesMethod(row, "head"))
        assertFalse(RouteListing.matchesMethod(row, "POST"))
        assertEquals(emptyList<String>(), RouteListing.csv(emptyMap(), "path"))
        assertEquals(listOf("a", "b"), RouteListing.csv(mapOf("path" to "a, b"), "path"))
        assertEquals(emptyList<String>(), RouteListing.csv(mapOf("path" to ","), "path"))
        assertEquals(emptyList<String>(), RouteListing.csv(mapOf("path" to "  "), "path"))
        val filtered = RouteListing.filter(rows, mapOf("name" to "missing"))
        assertTrue(filtered.isEmpty())
        assertEquals(1, RouteListing.filter(rows, mapOf("name" to "X")).size)
        assertEquals(1, RouteListing.filter(rows, mapOf("path" to "/x")).size)
        assertEquals(2, RouteListing.filter(rows, mapOf("except-path" to "/x")).size)
        val none = RouteListing.filter(rows, mapOf("method" to ""))
        assertEquals(3, none.size)
        val json = RouteListing.toJson(emptyList())
        assertEquals("[]", json)
        assertEquals("Closure", RouteListing.actionLabel(Handler { Response.text("c") }))
        assertEquals(NamedHandler::class.java.name, RouteListing.actionLabel(NamedHandler()))
        assertTrue(RouteListing.looksLikeClosure(Handler { Response.text("c") }.javaClass))
        assertFalse(RouteListing.looksLikeClosure(NamedHandler::class.java))
        val anonymous = object : Handler {
            override fun handle(request: Request): Response = Response.text("anon")
        }
        assertTrue(RouteListing.looksLikeClosure(anonymous.javaClass))
        assertTrue(RouteListing.looksLikeClosure(Class.forName("org.konaframework.grind.Fake\$\$LambdaSample")))
        assertTrue(RouteListing.looksLikeClosure(Class.forName("org.konaframework.grind.Closure\$1")))
        assertEquals("Closure", RouteListing.actionLabel(Class.forName("org.konaframework.grind.Fake\$\$LambdaSample").getDeclaredConstructor().newInstance() as Handler))
        assertEquals("NamedGate", RouteListing.middlewareLabel(NamedGate()))
        assertEquals("Closure", RouteListing.middlewareLabel(Middleware { req, nxt -> nxt.handle(req) }))
        assertTrue(RouteListing.isVendor(NamedHandler()))
        assertEquals(RouteListing.DEFAULT_COLUMNS, RouteListing.columns(emptyMap()))
        assertEquals(RouteListing.COMPACT_COLUMNS, RouteListing.columns(mapOf("compact" to "true")))
        assertEquals(listOf("uri", "name"), RouteListing.columns(mapOf("columns" to "uri, name, uri")))
        val reversed = RouteListing.sort(rows, mapOf("reverse" to "true", "sort" to "uri"))
        assertEquals("/z", reversed.first().uri)
        val forward = RouteListing.sort(rows, mapOf("reverse" to "false"))
        assertEquals("/x", forward.first().uri)
        val trimmedSort = RouteListing.sort(rows, mapOf("sort" to "  "))
        assertEquals("/x", trimmedSort.first().uri)
        val copied = row.copy(name = "copied")
        assertEquals("copied", copied.name)
        assertEquals(row, row.copy())
        assertFalse(row == copied)
        assertFalse(row.equals(null))
        assertFalse(row.equals("nope"))
        assertEquals(row.hashCode(), row.copy().hashCode())
        assertTrue(row.toString().contains("/x"))
        assertEquals("GET", row.component1().first())
        assertEquals(0, boot("route:list", "--sort=").first)
        Kona.reset()
    }

    class NamedHandler : Handler {
        override fun handle(request: Request): Response = Response.text("named")
    }
}
