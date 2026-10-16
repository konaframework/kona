package io.konaframework.http

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpDeepenCoverageTest {
    @AfterEach
    fun clear() {
        Validator.setExistenceChecker(null)
    }

    @Test
    fun requestEdgeBranches() {
        val blankJson = Request(
            "POST",
            "/",
            headers = mapOf("Content-Type" to "application/json"),
            body = "   ",
        )
        assertEquals(emptyMap<String, String>(), blankJson.form())

        val jsonForm = Request(
            "POST",
            "/",
            headers = mapOf("Content-Type" to "application/json"),
            body = """{"name":"Ada","n":null}""",
        )
        assertEquals("Ada", jsonForm.form()["name"])
        assertEquals("", jsonForm.form()["n"])

        val request = Request.post("/", "a=1")
        request.merge(mapOf("flag" to true, "emptyFile" to UploadedFile("e.bin", null, ByteArray(0))))
        assertTrue(request.boolean("flag"))
        assertFalse(request.filled("emptyFile"))
        assertFalse(request.hasFile("emptyFile"))
        request.merge(mapOf("not-a-file" to "x"))
        assertNull(request.file("not-a-file"))
        assertNull(request.file("absent-upload"))

        assertFalse(request.filled("missing-key"))
        assertEquals(emptyMap<String, Any?>(), request.only("nope"))
        request.merge(
            mapOf(
                "list" to emptyList<Any>(),
                "map" to emptyMap<String, Any>(),
                "arr" to emptyArray<Any>(),
                "n" to 1,
                "nil" to null,
                "spaces" to "  ",
            ),
        )
        assertFalse(request.filled("list"))
        assertFalse(request.filled("map"))
        assertFalse(request.filled("arr"))
        assertTrue(request.filled("n"))
        request.merge(mapOf("arr2" to arrayOf(1)))
        assertTrue(request.filled("arr2"))
        assertFalse(request.boolean("nil"))
        assertFalse(request.boolean("spaces"))
        assertTrue(request.boolean("n"))
        assertEquals("default", request.input("nil", "default"))

        request.setAttribute(TrustProxies.CLIENT_IP, "  ")
        request.setAttribute(TrustProxies.REMOTE_ADDR, "10.0.0.9")
        assertEquals("10.0.0.9", request.ip())
        request.setAttribute(TrustProxies.REMOTE_ADDR, "  ")
        assertNull(request.ip())

        var missingRan = false
        request.whenMissing("a") { missingRan = true }
        assertFalse(missingRan)

        // second merge uses existing bag
        request.merge(mapOf("extra" to "1"))
        assertEquals("1", request.input("extra"))
    }

    @Test
    fun jsonSupportIsJsonEdges() {
        assertFalse(JsonSupport.isJson(""))
        assertFalse(JsonSupport.isJson("   "))
        assertFalse(JsonSupport.isJson("]"))
        assertFalse(JsonSupport.isJson("1"))
        assertFalse(JsonSupport.isJson("\"str\""))
        assertTrue(JsonSupport.isJson("{}"))
        assertTrue(JsonSupport.isJson("[]"))
    }

    @Test
    fun validationDateFormatPhpTokensAndCompareGaps() {
        val ok = Validator.make(
            mapOf("f" to "24-05-01 13:45:09"),
            mapOf("f" to listOf("date_format:y-m-d H:i:s")),
        )
        assertTrue(ok.passes())

        // Java pattern passthrough (no PHP tokens)
        assertTrue(
            Validator.make(
                mapOf("f" to "2024-05-01"),
                mapOf("f" to listOf("date_format:yyyy-MM-dd")),
            ).passes(),
        )

        assertEquals(
            1,
            Validator.make(
                mapOf("f" to "2024-05-01", "other" to "nope"),
                mapOf("f" to listOf("before:other")),
            ).also { it.fails() }.errors()["f"]!!.size,
        )

        // accepted/declined null present
        assertEquals(1, Validator.make(mapOf("f" to null), mapOf("f" to listOf("accepted"))).also { it.fails() }.errors()["f"]!!.size)
        assertEquals(1, Validator.make(mapOf("f" to null), mapOf("f" to listOf("declined"))).also { it.fails() }.errors()["f"]!!.size)

        // non-string / invalid for string-ish rules
        for (rule in listOf(
            "alpha", "alpha_num", "alpha_dash", "ascii", "lowercase", "uppercase",
            "starts_with:a", "ends_with:a", "timezone", "date_format:Y-m-d",
        )) {
            assertEquals(
                1,
                Validator.make(mapOf("f" to 1), mapOf("f" to listOf(rule))).also { it.fails() }.errors()["f"]!!.size,
                rule,
            )
        }
        assertEquals(1, Validator.make(mapOf("f" to "x"), mapOf("f" to listOf("digits:1"))).also { it.fails() }.errors()["f"]!!.size)
        assertEquals(1, Validator.make(mapOf("f" to "x"), mapOf("f" to listOf("digits_between:1,2"))).also { it.fails() }.errors()["f"]!!.size)

        // not_regex non-string passes (not a string match)
        assertTrue(Validator.make(mapOf("f" to 5), mapOf("f" to listOf("not_regex:/5/"))).passes())

        // unique/exists with only table (column defaults to field)
        Validator.setExistenceChecker { _, column, value, _ ->
            column == "f" && value == "yes"
        }
        assertTrue(Validator.make(mapOf("f" to "yes"), mapOf("f" to listOf("exists:users"))).passes())
        assertTrue(Validator.make(mapOf("f" to "no"), mapOf("f" to listOf("unique:users"))).passes())

        // required_* success paths when value present
        assertTrue(
            Validator.make(
                mapOf("role" to "admin", "f" to "x"),
                mapOf("f" to listOf("required_if:role,admin")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("name" to "Ada", "f" to "x"),
                mapOf("f" to listOf("required_with:name")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("a" to "1", "b" to "2", "f" to "x"),
                mapOf("f" to listOf("required_with_all:a,b")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("f" to "x"),
                mapOf("f" to listOf("required_without_all:a,b")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("role" to "user", "f" to "x"),
                mapOf("f" to listOf("required_unless:role,admin")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                emptyMap(),
                mapOf("f" to listOf("required_without:name")),
            ).fails(),
        )
        assertTrue(
            Validator.make(
                mapOf("name" to "Ada"),
                mapOf("f" to listOf("required_without:name")),
            ).passes(),
        )

        // date accepts LocalDate / LocalDateTime; uuid/ip reject non-strings
        assertTrue(
            Validator.make(
                mapOf("f" to java.time.LocalDate.of(2024, 1, 1)),
                mapOf("f" to listOf("date")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("f" to java.time.LocalDateTime.of(2024, 1, 1, 12, 0)),
                mapOf("f" to listOf("date")),
            ).passes(),
        )
        assertTrue(Validator.make(mapOf("f" to null), mapOf("f" to listOf("date"))).fails())
        assertTrue(Validator.make(mapOf("f" to 12), mapOf("f" to listOf("uuid"))).fails())
        assertTrue(Validator.make(mapOf("f" to 12), mapOf("f" to listOf("ip"))).fails())
        assertTrue(Validator.make(mapOf("f" to "localhost"), mapOf("f" to listOf("ipv4"))).fails())
        assertTrue(Validator.make(mapOf("f" to "::1"), mapOf("f" to listOf("ipv4"))).fails())
        assertTrue(Validator.make(mapOf("f" to "127.0.0.1"), mapOf("f" to listOf("ipv6"))).fails())
        assertTrue(Validator.make(mapOf("f" to "not a host !!!"), mapOf("f" to listOf("ip"))).fails())

        // date compare failure branches + null-ish rule inputs
        assertTrue(
            Validator.make(
                mapOf("f" to "2024-04-01", "start" to "2024-05-01"),
                mapOf("f" to listOf("after_or_equal:start")),
            ).fails(),
        )
        assertTrue(
            Validator.make(
                mapOf("f" to "2024-06-01", "end" to "2024-05-01"),
                mapOf("f" to listOf("before_or_equal:end")),
            ).fails(),
        )
        assertTrue(Validator.make(mapOf("f" to null), mapOf("f" to listOf("timezone"))).fails())
        assertTrue(Validator.make(mapOf("f" to null), mapOf("f" to listOf("starts_with:a"))).fails())
        assertTrue(Validator.make(mapOf("f" to null), mapOf("f" to listOf("ends_with:a"))).fails())
        assertTrue(Validator.make(mapOf("f" to null), mapOf("f" to listOf("digits:2"))).fails())
        assertTrue(Validator.make(mapOf("f" to null), mapOf("f" to listOf("digits_between:1,3"))).fails())
        // blank strings skip non-implicit rules (including date)
        assertTrue(Validator.make(mapOf("f" to ""), mapOf("f" to listOf("date"))).passes())
        assertTrue(Validator.make(mapOf("f" to "not-a-date"), mapOf("f" to listOf("date"))).fails())

        // malformed required_if / digits_between params
        assertTrue(
            Validator.make(mapOf("f" to "x"), mapOf("f" to listOf("required_if:role"))).also { it.fails() }
                .errors()["f"]!!.first().contains("Invalid"),
        )
        assertTrue(
            Validator.make(mapOf("f" to "12"), mapOf("f" to listOf("digits_between:1"))).also { it.fails() }
                .errors()["f"]!!.first().contains("Invalid"),
        )

        Validator.setExistenceChecker { _, column, _, except ->
            column == "email" && except == null
        }
        assertTrue(Validator.make(mapOf("f" to "x"), mapOf("f" to listOf("unique:users,"))).passes())
        assertTrue(Validator.make(mapOf("email" to "x"), mapOf("email" to listOf("exists:users,email,"))).passes())

        // digits_between: digits ok but length out of range
        assertTrue(Validator.make(mapOf("f" to "12345"), mapOf("f" to listOf("digits_between:1,3"))).fails())
        assertTrue(Validator.make(mapOf("f" to "12"), mapOf("f" to listOf("digits_between:1,3"))).passes())
        assertTrue(Validator.make(mapOf("f" to "12"), mapOf("f" to listOf("digits:2"))).passes())

        // required_if / unless / without remaining boolean combos
        assertTrue(
            Validator.make(
                mapOf("role" to "admin", "f" to "ok"),
                mapOf("f" to listOf("required_if:role,admin")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("role" to "admin"),
                mapOf("f" to listOf("required_unless:role,user")),
            ).fails(),
        )
        assertTrue(
            Validator.make(
                mapOf("name" to "Ada", "f" to ""),
                mapOf("f" to listOf("required_without:name")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("f" to "x"),
                mapOf("f" to listOf("required_without:name")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("role" to "admin", "f" to "x"),
                mapOf("f" to listOf("required_unless:role,admin")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                emptyMap(),
                mapOf("f" to listOf("required_if:role,admin")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("role" to null),
                mapOf("f" to listOf("required_if:role,admin")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("f" to "x"),
                mapOf("f" to listOf("required_unless:role")),
            ).also { it.fails() }.errors()["f"]!!.first().contains("Invalid"),
        )
        assertTrue(
            Validator.make(
                mapOf("f" to "2024-05-01", "other" to ""),
                mapOf("f" to listOf("after:other")),
            ).fails(),
        )
        // after equal fails; before equal fails  
        assertTrue(
            Validator.make(
                mapOf("f" to "2024-05-01", "t" to "2024-05-01"),
                mapOf("f" to listOf("after:t")),
            ).fails(),
        )
        assertTrue(
            Validator.make(
                mapOf("f" to "2024-05-01", "t" to "2024-05-01"),
                mapOf("f" to listOf("before:t")),
            ).fails(),
        )
        assertTrue(
            Validator.make(
                mapOf("f" to "2024-05-01", "t" to "2024-05-01"),
                mapOf("f" to listOf("before_or_equal:t")),
            ).passes(),
        )

        Validator.setExistenceChecker(null)
        assertNull(Validator.existenceChecker())
    }

    @Test
    fun middlewarePriorityUnlistedStaysAfter() {
        val order = mutableListOf<String>()
        class A : Middleware {
            override fun handle(request: Request, next: Handler): Response {
                order += "A"
                return next.handle(request)
            }
        }
        class B : Middleware {
            override fun handle(request: Request, next: Handler): Response {
                order += "B"
                return next.handle(request)
            }
        }
        class C : Middleware {
            override fun handle(request: Request, next: Handler): Response {
                order += "C"
                return next.handle(request)
            }
        }
        val sorted = MiddlewarePipeline.sortByPriority(
            listOf(C(), B(), A()),
            listOf(A::class.java, B::class.java),
        )
        val router = Router().apply { get("/") { Response.text("ok") } }
        HttpKernel(router, sorted).handle(Request.get("/"))
        assertEquals(listOf("A", "B", "C"), order)
        assertEquals(emptyList<Middleware>(), MiddlewarePipeline.sortByPriority(emptyList(), listOf(A::class.java)))
    }
}
