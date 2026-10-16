package dev.kona.http

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidationNicheRulesTest {
    private fun errors(data: Map<String, Any?>, rules: Map<String, List<String>>): Map<String, List<String>> =
        Validator.make(data, rules).also { it.fails() }.errors()

    @AfterEach
    fun clear() {
        Validator.setCurrentPasswordChecker(null)
        Validator.setExistenceChecker(null)
    }

    @Test
    fun prohibitedFamily() {
        assertTrue(Validator.make(mapOf("f" to null), mapOf("f" to listOf("prohibited"))).passes())
        assertTrue(Validator.make(emptyMap(), mapOf("f" to listOf("prohibited"))).passes())
        assertFalse(Validator.make(mapOf("f" to "x"), mapOf("f" to listOf("prohibited"))).passes())

        assertTrue(
            Validator.make(mapOf("f" to "x", "g" to "no"), mapOf("f" to listOf("prohibited_if:g,yes"))).passes(),
        )
        assertFalse(
            Validator.make(mapOf("f" to "x", "g" to "yes"), mapOf("f" to listOf("prohibited_if:g,yes"))).passes(),
        )
        assertTrue(
            Validator.make(mapOf("f" to "x", "g" to "yes"), mapOf("f" to listOf("prohibited_unless:g,yes"))).passes(),
        )
        assertFalse(
            Validator.make(mapOf("f" to "x", "g" to "no"), mapOf("f" to listOf("prohibited_unless:g,yes"))).passes(),
        )
        assertFalse(
            Validator.make(
                mapOf("f" to "x", "g" to "y"),
                mapOf("f" to listOf("prohibits:g")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("f" to "x", "g" to null),
                mapOf("f" to listOf("prohibits:g")),
            ).passes(),
        )
        assertTrue(
            Validator.make(
                mapOf("f" to null, "g" to "y"),
                mapOf("f" to listOf("prohibits:g")),
            ).passes(),
        )
    }

    @Test
    fun excludeFamilyOmitsFromValidated() {
        val always = Validator.make(mapOf("a" to 1, "b" to 2), mapOf("a" to listOf("exclude"), "b" to listOf("required")))
        assertEquals(mapOf("b" to 2), always.validate())

        val ifYes = Validator.make(
            mapOf("secret" to "s", "flag" to "yes"),
            mapOf("secret" to listOf("exclude_if:flag,yes"), "flag" to listOf("required")),
        )
        assertEquals(mapOf("flag" to "yes"), ifYes.validate())

        val ifNo = Validator.make(
            mapOf("secret" to "s", "flag" to "no"),
            mapOf("secret" to listOf("exclude_if:flag,yes"), "flag" to listOf("required")),
        )
        assertEquals(mapOf("secret" to "s", "flag" to "no"), ifNo.validate())

        val unless = Validator.make(
            mapOf("secret" to "s", "flag" to "no"),
            mapOf("secret" to listOf("exclude_unless:flag,yes"), "flag" to listOf("required")),
        )
        assertEquals(mapOf("flag" to "no"), unless.validate())

        val unlessKeep = Validator.make(
            mapOf("secret" to "s", "flag" to "yes"),
            mapOf("secret" to listOf("exclude_unless:flag,yes"), "flag" to listOf("required")),
        )
        assertEquals(mapOf("secret" to "s", "flag" to "yes"), unlessKeep.validate())

        val with = Validator.make(
            mapOf("a" to 1, "b" to 2),
            mapOf("a" to listOf("exclude_with:b"), "b" to listOf("required")),
        )
        assertEquals(mapOf("b" to 2), with.validate())

        val withKeep = Validator.make(
            mapOf("a" to 1),
            mapOf("a" to listOf("exclude_with:b"), "b" to listOf("nullable")),
        )
        assertEquals(mapOf("a" to 1), withKeep.validate())

        val without = Validator.make(
            mapOf("a" to 1),
            mapOf("a" to listOf("exclude_without:b"), "b" to listOf("nullable")),
        )
        assertEquals(emptyMap<String, Any?>(), without.validate())

        val withoutKeep = Validator.make(
            mapOf("a" to 1, "b" to 2),
            mapOf("a" to listOf("exclude_without:b"), "b" to listOf("required")),
        )
        assertEquals(mapOf("a" to 1, "b" to 2), withoutKeep.validate())

        assertTrue(errors(mapOf("a" to 1), mapOf("a" to listOf("exclude_if"))).containsKey("a"))
        assertTrue(errors(mapOf("a" to 1), mapOf("a" to listOf("exclude_unless"))).containsKey("a"))
        assertTrue(errors(mapOf("a" to 1), mapOf("a" to listOf("prohibited_if"))).containsKey("a"))
        assertTrue(errors(mapOf("a" to 1), mapOf("a" to listOf("prohibited_unless"))).containsKey("a"))
        assertTrue(errors(mapOf("a" to 1), mapOf("a" to listOf("accepted_if"))).containsKey("a"))
        assertTrue(errors(mapOf("a" to 1), mapOf("a" to listOf("declined_if"))).containsKey("a"))
        // otherAndExpected: missing comma / empty sides
        assertTrue(errors(mapOf("a" to 1), mapOf("a" to listOf("prohibited_if:g"))).containsKey("a"))
        assertTrue(errors(mapOf("a" to 1), mapOf("a" to listOf("exclude_if:,yes"))).containsKey("a"))
        assertTrue(errors(mapOf("a" to 1), mapOf("a" to listOf("accepted_if:g,"))).containsKey("a"))
    }

    @Test
    fun acceptedIfDeclinedIfUlidMacCurrentPassword() {
        assertTrue(
            Validator.make(mapOf("f" to "yes", "g" to "1"), mapOf("f" to listOf("accepted_if:g,1"))).passes(),
        )
        assertFalse(
            Validator.make(mapOf("f" to "no", "g" to "1"), mapOf("f" to listOf("accepted_if:g,1"))).passes(),
        )
        assertTrue(
            Validator.make(mapOf("f" to "no", "g" to "0"), mapOf("f" to listOf("accepted_if:g,1"))).passes(),
        )
        assertFalse(
            Validator.make(mapOf("f" to null, "g" to "1"), mapOf("f" to listOf("accepted_if:g,1"))).passes(),
        )
        assertTrue(
            Validator.make(mapOf("f" to "no", "g" to "1"), mapOf("f" to listOf("declined_if:g,1"))).passes(),
        )
        assertFalse(
            Validator.make(mapOf("f" to "yes", "g" to "1"), mapOf("f" to listOf("declined_if:g,1"))).passes(),
        )
        assertFalse(
            Validator.make(mapOf("f" to null, "g" to "1"), mapOf("f" to listOf("declined_if:g,1"))).passes(),
        )

        assertTrue(Validator.make(mapOf("f" to "01ARZ3NDEKTSV4RRFFQ69G5FAV"), mapOf("f" to listOf("ulid"))).passes())
        assertFalse(Validator.make(mapOf("f" to "not-a-ulid"), mapOf("f" to listOf("ulid"))).passes())
        assertFalse(Validator.make(mapOf("f" to 1), mapOf("f" to listOf("ulid"))).passes())

        assertTrue(Validator.make(mapOf("f" to "01:23:45:67:89:AB"), mapOf("f" to listOf("mac_address"))).passes())
        assertTrue(Validator.make(mapOf("f" to "0123.4567.89ab"), mapOf("f" to listOf("mac_address"))).passes())
        assertFalse(Validator.make(mapOf("f" to "not-mac"), mapOf("f" to listOf("mac_address"))).passes())
        assertFalse(Validator.make(mapOf("f" to 1), mapOf("f" to listOf("mac_address"))).passes())

        assertTrue(
            errors(mapOf("f" to "x"), mapOf("f" to listOf("current_password"))).getValue("f")
                .any { it.contains("CurrentPasswordChecker") },
        )
        Validator.setCurrentPasswordChecker { it == "secret" }
        assertTrue(Validator.make(mapOf("f" to "secret"), mapOf("f" to listOf("current_password"))).passes())
        assertFalse(Validator.make(mapOf("f" to "wrong"), mapOf("f" to listOf("current_password"))).passes())
        assertFalse(Validator.make(mapOf("f" to null), mapOf("f" to listOf("current_password"))).passes())

        assertTrue(
            Validator.make(mapOf("f" to "no", "g" to "0"), mapOf("f" to listOf("declined_if:g,1"))).passes(),
        )
    }
}
