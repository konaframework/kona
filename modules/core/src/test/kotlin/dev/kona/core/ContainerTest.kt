package dev.kona.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContainerTest {
    class Greeter(val name: String)
    class Defaultable {
        var value = "ok"
    }

    @Test
    fun bindResolveAndSingleton() {
        val c = Container()
        c.bind("greeter", { Greeter("a") })
        val a = c.resolve<Greeter>("greeter")
        val b = c.resolve<Greeter>("greeter")
        assertEquals("a", a.name)
        assertTrue(a !== b)

        c.singleton(Greeter::class.java) { Greeter("shared") }
        val s1 = c.resolve(Greeter::class.java)
        val s2 = c.resolve(Greeter::class.java)
        assertTrue(s1 === s2)

        c.bind(Greeter::class.java, { Greeter("typed") }, shared = false)
        assertEquals("typed", c.resolve(Greeter::class.java).name)
        c.bind(Greeter::class.java, { Greeter("defaultSharedFlag") })
        assertEquals("defaultSharedFlag", c.resolve(Greeter::class.java).name)
        c.bind("other", { Greeter("s") }, shared = true)
        assertTrue(c.resolve<Greeter>("other") === c.resolve<Greeter>("other"))
    }

    @Test
    fun instanceAliasMakeAndFlush() {
        val c = Container()
        val g = Greeter("x")
        c.instance(Greeter::class.java, g)
        c.alias("g", Greeter::class.java.name)
        assertTrue(c.bound(Greeter::class.java))
        assertTrue(c.bound("g"))
        assertEquals(g, c.resolve<Greeter>("g"))
        assertEquals("ok", c.make(Defaultable::class.java).value)
        c.flush()
        assertFalse(c.bound(Greeter::class.java))
        assertThrows(BindingException::class.java) { c.resolve<Greeter>(Greeter::class.java.name) }
    }
}
