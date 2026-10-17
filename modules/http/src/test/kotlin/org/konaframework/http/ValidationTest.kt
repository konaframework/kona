package org.konaframework.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ValidationTest {
    private fun errors(data: Map<String, Any?>, vararg rules: String, field: String = "f"): List<String> =
        Validator.make(data, mapOf(field to rules.toList())).also { it.fails() }.errors()[field].orEmpty()

    private fun ok(value: Any?, vararg rules: String) = assertEquals(emptyList<String>(), errors(mapOf("f" to value), *rules))

    private fun bad(value: Any?, vararg rules: String) =
        assertEquals(1, errors(mapOf("f" to value), *rules).size, "expected failure for $value ${rules.toList()}")

    @Test
    fun requiredIsImplicitAndStopsTheField() {
        assertEquals(listOf("The f field is required."), errors(emptyMap(), "required", "email", "min:3"))
        for (empty in listOf(null, "  ", emptyList<Any>(), emptyMap<String, Any>(), arrayOf<Any>())) {
            bad(empty, "required")
        }
        for (filled in listOf("x", listOf(1), mapOf("a" to 1), arrayOf(1), 0, false)) {
            ok(filled, "required")
        }
    }

    @Test
    fun nonImplicitRulesSkipMissingBlankAndNullableNulls() {
        ok(null, "nullable", "string", "min:3")
        ok("", "email", "integer")
        assertEquals(emptyList<String>(), errors(emptyMap(), "email"))
        // present null without nullable still runs rules
        assertEquals(listOf("The f field must be a string."), errors(mapOf("f" to null), "string"))
        assertEquals(listOf("The f field must be at least 1 characters."), errors(mapOf("f" to null), "min:1"))
        assertEquals(listOf("The f field is required."), errors(mapOf("f" to null), "nullable", "required"))
        ok("x", "nullable", "bail", "string")
    }

    @Test
    fun bailStopsAfterFirstFailureOtherwiseAllFailuresCollect() {
        assertEquals(2, errors(mapOf("f" to "x"), "integer", "email").size)
        assertEquals(listOf("The f field must be an integer."), errors(mapOf("f" to "x"), "bail", "integer", "email"))
    }

    @Test
    fun typeRules() {
        ok("s", "string"); bad(1, "string")
        ok(5, "integer"); ok("-12", "integer"); ok(" +7 ", "integer"); ok(5L, "integer")
        bad("007", "integer"); bad("99999999999999999999", "integer"); bad(3.5, "integer"); bad(true, "integer")
        ok(2.5, "numeric"); ok("1e3", "numeric"); ok(".5", "numeric"); ok(" -4.0 ", "numeric")
        bad("NaN", "numeric"); bad("0x1A", "numeric"); bad(true, "numeric")
        for (b in listOf(true, false, 0, 1, "0", "1", "true", "false")) ok(b, "boolean")
        bad("yes", "boolean"); bad(2, "boolean")
        ok("ada@example.com", "email"); bad("bad", "email"); bad(5, "email")
        ok("https://kona.dev/docs?x=1", "url")
        bad(5, "url"); bad("not a url", "url"); bad("relative/path", "url"); bad("mailto:a@b.co", "url")
        ok(listOf(1), "array"); ok(mapOf("a" to 1), "array"); ok(arrayOf(1), "array"); bad("[1]", "array")
        ok(listOf(1), "list"); ok(arrayOf(1), "list"); bad(mapOf("a" to 1), "list")
    }

    @Test
    fun sizeRulesAreTypeAware() {
        assertEquals(listOf("The f field must be at least 3 characters."), errors(mapOf("f" to "15"), "min:3"))
        assertEquals(listOf("The f field must be at least 30."), errors(mapOf("f" to "15"), "numeric", "min:30"))
        assertEquals(listOf("The f field must be at least 30."), errors(mapOf("f" to "15"), "integer", "min:30"))
        assertEquals(listOf("The f field must be at least 30."), errors(mapOf("f" to 15), "min:30"))
        assertEquals(listOf("The f field must have at least 2 items."), errors(mapOf("f" to listOf(1)), "min:2"))
        assertEquals(
            listOf("The f field must not be greater than 1 characters."),
            errors(mapOf("f" to "ab"), "max:1"),
        )
        assertEquals(listOf("The f field must not be greater than 9."), errors(mapOf("f" to 10.5), "max:9"))
        assertEquals(
            listOf("The f field must not have more than 1 items."),
            errors(mapOf("f" to mapOf("a" to 1, "b" to 2)), "max:1"),
        )
        assertEquals(
            listOf("The f field must not have more than 1 items."),
            errors(mapOf("f" to arrayOf(1, 2)), "max:1"),
        )
        // non-numeric strings under `numeric` fall back to length for sizing
        assertEquals(
            listOf("The f field must be a number.", "The f field must be at least 5 characters."),
            errors(mapOf("f" to "abc"), "numeric", "min:5"),
        )
        // multibyte strings measure code points
        ok("日本", "size:2")
        assertEquals(listOf("The f field must be 3."), errors(mapOf("f" to 2), "size:3"))
        assertEquals(listOf("The f field must contain 3 items."), errors(mapOf("f" to listOf(1)), "size:3"))
        assertEquals(listOf("The f field must be 3 characters."), errors(mapOf("f" to "ab"), "size:3"))
        ok(5, "between:1,10")
        assertEquals(listOf("The f field must be between 1 and 10."), errors(mapOf("f" to 0), "between:1,10"))
        assertEquals(listOf("The f field must be between 1 and 10."), errors(mapOf("f" to 11), "between:1,10"))
        assertEquals(
            listOf("The f field must be between 2 and 3 characters."),
            errors(mapOf("f" to "a"), "between:2,3"),
        )
        assertEquals(
            listOf("The f field must have between 2 and 3 items."),
            errors(mapOf("f" to listOf(1)), "between:2,3"),
        )
    }

    @Test
    fun comparisonRules() {
        ok("b", "in:a,b"); ok(listOf("a", "b"), "in:a,b")
        assertEquals(listOf("The selected f is invalid."), errors(mapOf("f" to "c"), "in:a,b"))
        bad(listOf("a", "c"), "in:a,b")

        val pw = mapOf("password" to "s3cret", "password_confirmation" to "s3cret")
        assertEquals(emptyList<String>(), errors(pw, "confirmed", field = "password"))
        assertEquals(
            listOf("The password field confirmation does not match."),
            errors(mapOf("password" to "s3cret", "password_confirmation" to "x"), "confirmed", field = "password"),
        )
        assertEquals(emptyList<String>(), errors(mapOf("f" to "a", "repeat" to "a"), "confirmed:repeat"))
        assertEquals(emptyList<String>(), errors(mapOf("f" to "a", "other" to "a"), "same:other"))
        assertEquals(
            listOf("The first name field must match last name."),
            errors(mapOf("first_name" to "a", "last_name" to "b"), "same:last_name", field = "first_name"),
        )
    }

    @Test
    fun regexRuleUsesPcreDelimitersAndFlags() {
        ok("abc", "regex:/^[a-z]+$/")
        bad("ABC1", "regex:/^[a-z]+$/")
        ok("ABC", "regex:/^[a-z]+$/i")
        ok("x\nabc", "regex:/^abc$/m")
        ok("a\nb", "regex:/a.b/s")
        ok("ab", "regex:/a b # comment/x")
        ok("é", "regex:#^.$#u")
        ok("a|b", "regex:/a|b/")
        bad(5, "regex:/5/")
        assertEquals(listOf("The f field format is invalid."), errors(mapOf("f" to "x"), "regex:/y/"))
    }

    @Test
    fun pipeJoinedRules() {
        assertEquals(listOf("The f field is required."), errors(emptyMap(), "required|email"))
        assertEquals(listOf("The f field must be a valid email address."), errors(mapOf("f" to "x"), "string|email"))
    }

    @Test
    fun unknownAndMalformedRulesFailClosed() {
        assertEquals(listOf("Unknown validation rule [nope]."), errors(emptyMap(), "nope"))
        assertEquals(listOf("Unknown validation rule []."), errors(mapOf("f" to "x"), "string|"))
        for (rule in listOf(
            "min", "min:", "min:abc", "max:x", "size:", "between:1", "between:a,2", "in", "in:", "same",
            "regex", "regex:", "regex:abc", "regex:/", "regex:/a/q", "regex:/(/",
        )) {
            assertEquals(listOf("Invalid validation rule [$rule]."), errors(emptyMap(), rule), rule)
        }
        assertThrows(ValidationException::class.java) {
            Validator.make(mapOf("f" to "x"), mapOf("f" to listOf("unknown-rule"))).validate()
        }
    }

    @Test
    fun validateReturnsOnlyRuledKeysAndSummarizesErrors() {
        val data = mapOf("name" to "Ada", "extra" to 1, "age" to 36)
        assertEquals(
            mapOf("name" to "Ada", "age" to 36),
            Validator.make(data, mapOf("name" to listOf("required"), "age" to listOf("integer"))).validate(),
        )
        val one = assertThrows(ValidationException::class.java) {
            Validator.make(emptyMap(), mapOf("a" to listOf("required"))).validate()
        }
        assertEquals("The a field is required.", one.message)
        val two = assertThrows(ValidationException::class.java) {
            Validator.make(emptyMap(), mapOf("a" to listOf("required"), "b" to listOf("required"))).validate()
        }
        assertEquals("The a field is required. (and 1 more error)", two.message)
        val three = assertThrows(ValidationException::class.java) {
            Validator.make(
                emptyMap(),
                mapOf("a" to listOf("required"), "b" to listOf("required"), "c" to listOf("required")),
            ).validate()
        }
        assertEquals("The a field is required. (and 2 more errors)", three.message)
        assertEquals("The given data was invalid.", ValidationException(emptyMap()).message)
    }

    @Test
    fun requestDataKeepsJsonTypesForValidation() {
        val json = Request(
            "POST",
            "/",
            headers = mapOf("Content-Type" to "application/json"),
            query = mapOf("page" to "2"),
            body = """{"tags":["a","b"],"age":36,"admin":true,"note":null}""",
        )
        assertEquals(listOf("a", "b"), json.data()["tags"])
        assertEquals("2", json.data()["page"])
        assertEquals(
            mapOf("tags" to listOf("a", "b"), "age" to 36, "admin" to true, "note" to null),
            json.validate(
                mapOf(
                    "tags" to listOf("required", "array", "min:2", "in:a,b"),
                    "age" to listOf("integer", "between:18,99"),
                    "admin" to listOf("boolean"),
                    "note" to listOf("nullable", "string"),
                ),
            ),
        )
        assertThrows(ValidationException::class.java) { json.validate(mapOf("age" to listOf("string"))) }
        assertEquals(mapOf("a" to "b"), Request.post("/", "a=b").data())
        assertEquals(emptyMap<String, Any?>(), JsonSupport.parseObject(" "))
        assertEquals(emptyMap<String, Any?>(), JsonSupport.parseObject("[1]"))
    }
}
