package org.konaframework.crema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CremaAccessTest {
    class Named(val name: String)
    class Flag {
        fun isActive(): Boolean = true
    }
    class Label {
        fun label(): String = "tag"
    }
    class FieldBox {
        @JvmField
        var title: String = "box"
    }
    class Row {
        private val values = linkedMapOf("email" to "a@b.c")
        fun getAttribute(key: String): Any? = values[key]
    }
    class Greeter {
        fun getEmail(): String = "a@b.c"
        fun greet(name: String): String = "hi $name"
        fun box(): String = "boxed"
    }
    class Plain
    class IntAttribute {
        fun getAttribute(key: Int): Int = key
    }
    class ZeroAttribute {
        fun getAttribute(): Int = 0
    }
    class IdHolder {
        fun getA_b(): String = "ok"
        fun wrap(value: String): String = value
    }

    private fun evalLiteral(expr: String): Any? = when (val text = expr.trim()) {
        "null" -> null
        "1" -> 1
        "2" -> 2
        "1.0" -> 1.0
        "3" -> 3
        else -> when {
            text.length >= 2 && (text.first() == '\'' || text.first() == '"') && text.last() == text.first() ->
                text.substring(1, text.lastIndex)
            else -> text
        }
    }

    @Test
    fun `members cover getters flags methods fields attributes and maps`() {
        assertEquals("Ada", CremaAccess.member(Named("Ada"), "name"))
        assertEquals(true, CremaAccess.member(Flag(), "active"))
        assertEquals("tag", CremaAccess.member(Label(), "label"))
        assertEquals("box", CremaAccess.member(FieldBox(), "title"))
        assertEquals("a@b.c", CremaAccess.member(Row(), "email"))
        assertNull(CremaAccess.member(Plain(), "missing"))
        assertNull(CremaAccess.member(Plain(), ""))
        assertNull(CremaAccess.member(Plain(), "Title"))
        assertNull(CremaAccess.member(IntAttribute(), "email"))
        assertNull(CremaAccess.member(ZeroAttribute(), "email"))
        assertEquals("Ada", CremaAccess.member(mapOf("name" to "Ada"), "name"))
    }

    @Test
    fun `calls optional chains and bad paths`() {
        val greeter = Greeter()
        val lookup = mapOf("user" to greeter, "box" to greeter)
        fun find(name: String): Any? = lookup[name]
        assertEquals("a@b.c", CremaAccess.read("user.getEmail()", ::find, eval = ::evalLiteral))
        assertEquals("hi Ada", CremaAccess.read("user.greet('Ada')", ::find, eval = ::evalLiteral))
        assertNull(CremaAccess.read("user.missing()", ::find, eval = ::evalLiteral))
        assertNull(CremaAccess.read("user?.getEmail()", { null }, eval = ::evalLiteral))
        assertNull(CremaAccess.read("user.getEmail()", { null }, eval = ::evalLiteral))
        assertNull(CremaAccess.read("missing()", ::find, eval = ::evalLiteral))
        assertEquals("boxed", CremaAccess.read("box()", ::find, eval = ::evalLiteral))
        assertEquals("a@b.c", CremaAccess.read("user.email", ::find))
        assertNull(CremaAccess.read(""))
        assertNull(CremaAccess.read("ghost"))
        assertNull(CremaAccess.read("   "))
        assertNull(CremaAccess.read("."))
        assertTrue(CremaAccess.read("user.", ::find) is Greeter)
        assertEquals("a@b.c", CremaAccess.read(" user . getEmail() ", ::find, eval = ::evalLiteral))
        assertEquals("a@b.c", CremaAccess.read("user.Email", ::find))
        assertEquals("ok", CremaAccess.read("holder.a_b", { IdHolder() }))
        assertEquals("a@b.c", CremaAccess.read("holder.wrap(user.getEmail())", { IdHolder() }) { "a@b.c" })
        assertEquals("hi Ada", CremaAccess.read("user.greet(\"Ada\")", ::find, eval = ::evalLiteral))
        assertNull(CremaAccess.read("user.greet()", ::find, eval = ::evalLiteral))
        assertNull(CremaAccess.read("user-name"))
        assertNull(CremaAccess.read("user.greet("))
        assertNull(CremaAccess.read("user name"))
    }

    @Test
    fun `comparisons keep null and numbers distinct from text equality`() {
        fun cmp(expr: String): Any? = CremaAccess.compare(expr, ::evalLiteral)
        assertTrue(cmp("1 === 1") as Boolean)
        assertTrue(cmp("1 === 1.0") as Boolean)
        assertFalse(cmp("1 === 2") as Boolean)
        assertTrue(cmp("null === null") as Boolean)
        assertFalse(cmp("1 === null") as Boolean)
        assertFalse(cmp("1 !== 1") as Boolean)
        assertTrue(cmp("1 !== 2") as Boolean)
        assertTrue(cmp("1 == 1") as Boolean)
        assertFalse(cmp("1 == 2") as Boolean)
        assertTrue(cmp("1 != 2") as Boolean)
        assertFalse(cmp("1 != 1") as Boolean)
        assertTrue(cmp("1 < 2") as Boolean)
        assertFalse(cmp("2 < 1") as Boolean)
        assertTrue(cmp("1 < a") as Boolean)
        assertFalse(cmp("a < 1") as Boolean)
        assertTrue(cmp("b > a") as Boolean)
        assertFalse(cmp("1 > 2") as Boolean)
        assertFalse(cmp("a > b") as Boolean)
        assertTrue(cmp("2 <= 2") as Boolean)
        assertTrue(cmp("2 >= 2") as Boolean)
        assertFalse(cmp("2 >= 3") as Boolean)
        assertSame(CremaAccess.NotComparison, cmp("name"))
        assertSame(CremaAccess.NotComparison, cmp("<x"))
    }

    @Test
    fun `templates call methods after a null check and read attributes`() {
        val crema = CremaEngine(MapViewFinder())
        val source = "@if(user !== null){{ user.getEmail() }}@else none@endif"
        assertEquals("a@b.c", crema.renderSource(source, mapOf("user" to Greeter())))
        assertEquals(" none", crema.renderSource(source, mapOf("user" to null)))
        assertEquals("", crema.renderSource("{{ user?.getEmail() }}", mapOf("user" to null)))
        assertEquals("a@b.c", crema.renderSource("{{ user?.getEmail() }}", mapOf("user" to Greeter())))
        assertEquals("a@b.c", crema.renderSource("{{ row.email }}", mapOf("row" to Row())))
        assertEquals("yes", crema.renderSource("@if(user === null)yes@else no@endif", mapOf("user" to null)))
    }
}
