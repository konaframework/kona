package org.konaframework.brew

import org.konaframework.brew.fixtures.Address
import org.konaframework.brew.fixtures.Money
import org.konaframework.brew.fixtures.Status
import org.konaframework.brew.hydration.ArraySynth
import org.konaframework.brew.hydration.BigNumberSynth
import org.konaframework.brew.hydration.Checksum
import org.konaframework.brew.hydration.Coercion
import org.konaframework.brew.hydration.EnumSynth
import org.konaframework.brew.hydration.MapSynth
import org.konaframework.brew.hydration.ObjectSynth
import org.konaframework.brew.hydration.SecurityPolicy
import org.konaframework.brew.hydration.SetSynth
import org.konaframework.brew.hydration.Synth
import org.konaframework.brew.hydration.SynthContext
import org.konaframework.brew.hydration.Synthesizers
import org.konaframework.brew.hydration.TemporalSynth
import org.konaframework.brew.hydration.UuidSynth
import org.konaframework.brew.hydration.WireableSynth
import org.konaframework.brew.view.HtmlRoot
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Type
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import java.util.UUID

class HelpersTest {
    private val synths = Synthesizers()
    private val ctx: SynthContext = synths.context()

    @Suppress("unused")
    class Holder {
        lateinit var list: List<String>
        lateinit var wild: List<out Number>
        lateinit var map: Map<String, Int>
        lateinit var ints: IntArray
    }

    private fun type(name: String): Type = Holder::class.java.getDeclaredField(name).genericType

    @Test
    fun `html root helpers`() {
        assertEquals("<div a=\"1\" b=\"{&quot;x&quot;:true}\">x</div>", HtmlRoot.insertAttributes("<div>x</div>", linkedMapOf("a" to "1", "b" to mapOf("x" to true)), "c"))
        assertEquals("<br a=\"1\"/>", HtmlRoot.insertAttributes("<br/>", mapOf("a" to "1"), "c"))
        assertEquals("<div a=\"1\" class=\"x\">", HtmlRoot.insertAttributes("<div class=\"x\">", mapOf("a" to "1"), "c"))
        assertEquals("<div a=\"1\"", HtmlRoot.insertAttributes("<div", mapOf("a" to "1"), "c"))
        assertThrows<RootTagMissingFromViewException> { HtmlRoot.insertAttributes("text", emptyMap(), "c") }

        assertEquals(0, HtmlRoot.rootStart("<div>"))
        assertEquals(3, HtmlRoot.rootStart(" \n <div>"))
        assertEquals(8, HtmlRoot.rootStart("<!--x--><div>"))
        assertEquals(15, HtmlRoot.rootStart("<!DOCTYPE html><html>"))
        assertEquals(6, HtmlRoot.rootStart("<?x?> <p>"))
        assertNull(HtmlRoot.rootStart("<!--never"))
        assertNull(HtmlRoot.rootStart("<!never"))
        assertNull(HtmlRoot.rootStart("< div>"))
        assertNull(HtmlRoot.rootStart("<"))
        assertNull(HtmlRoot.rootStart("text"))
        assertNull(HtmlRoot.rootStart(""))

        assertEquals(1, HtmlRoot.topLevelElements("<div><p>a</p><br><img/></div>"))
        assertEquals(2, HtmlRoot.topLevelElements("<!-- c --><div></div>text<span></span>"))
        assertEquals(1, HtmlRoot.topLevelElements("<div><!-- unterminated"))
        assertEquals(1, HtmlRoot.topLevelElements("<div></div><p"))
        assertEquals(1, HtmlRoot.topLevelElements("<!doctype html><html></html>"))
        assertEquals(3, HtmlRoot.topLevelElements("<br><input name=\"a>b\" value='c>d'><x-icon/>"))
        assertEquals(1, HtmlRoot.topLevelElements("<script>if (a < b) { '<div>' }</script>"))
        assertEquals(2, HtmlRoot.topLevelElements("<style>p>a{}</style><TEXTAREA><div></TEXTAREA>"))
        assertEquals(1, HtmlRoot.topLevelElements("<script>never closed <div>"))
        assertEquals(1, HtmlRoot.topLevelElements("<a:b-c1></a:b-c1>"))
        HtmlRoot.assertSingleRoot("<div></div>", "c")
        assertThrows<MultipleRootElementsException> { HtmlRoot.assertSingleRoot("<a></a><b></b>", "c") }
        assertEquals("&amp;&quot;&lt;&gt;&#039;x", HtmlRoot.escape("&\"<>'x"))
    }

    @Test
    fun `checksum keys and normalization`() {
        val snapshot = mapOf("data" to mapOf("b" to 2.0, "a" to listOf(1.5f, 3.toShort())), "memo" to mapOf("id" to "x"))
        val plain = Checksum("secret")
        val b64 = Checksum("base64:" + java.util.Base64.getEncoder().encodeToString("secret".toByteArray()))
        assertEquals(plain.generate(snapshot), b64.generate(snapshot))
        val reordered = mapOf("memo" to mapOf("id" to "x"), "data" to mapOf("a" to listOf(1.5, 3), "b" to 2))
        assertEquals(plain.generate(snapshot), plain.generate(reordered))
        assertNotEquals(plain.generate(snapshot), Checksum("").generate(snapshot))
        assertEquals(Checksum("").generate(snapshot), Checksum(" ").generate(snapshot))
        assertEquals(1e16, Checksum.normalize(1e16))
        assertEquals(2.5, Checksum.normalize(2.5))
        plain.verify(snapshot + ("checksum" to plain.generate(snapshot)))
        assertThrows<CorruptComponentPayloadException> { plain.verify(snapshot + ("checksum" to 1)) }
    }

    enum class Size { S, M }

    @Test
    fun `coercion edge cases`() {
        assertEquals(false, Coercion.defaultFor(Boolean::class.javaPrimitiveType!!))
        assertEquals(0, Coercion.defaultFor(Int::class.javaPrimitiveType!!))
        assertEquals(0L, Coercion.defaultFor(Long::class.javaPrimitiveType!!))
        assertEquals(0.0, Coercion.defaultFor(Double::class.javaPrimitiveType!!))
        assertEquals(0f, Coercion.defaultFor(Float::class.javaPrimitiveType!!))
        assertEquals(0.toShort(), Coercion.defaultFor(Short::class.javaPrimitiveType!!))
        assertEquals(0.toByte(), Coercion.defaultFor(Byte::class.javaPrimitiveType!!))
        assertEquals('\u0000', Coercion.defaultFor(Char::class.javaPrimitiveType!!))
        assertNull(Coercion.defaultFor(String::class.java))
        for (t in listOf(Long::class, Double::class, Float::class, Short::class, Byte::class, Char::class, Boolean::class, Int::class)) {
            assertEquals(t.javaObjectType, Coercion.box(t.javaPrimitiveType!!))
        }
        assertEquals(String::class.java, Coercion.box(String::class.java))
        assertEquals(true, Coercion.toType(1, Boolean::class.javaPrimitiveType!!))
        assertEquals(false, Coercion.toType(0.0, java.lang.Boolean::class.java))
        assertEquals(true, Coercion.toType(" Yes ", Boolean::class.javaPrimitiveType!!))
        assertEquals("true", Coercion.toType(true, String::class.java))
        assertEquals("c", Coercion.toType('c', String::class.java))
        assertEquals(Size.M, Coercion.toType(1, Size::class.java))
        assertEquals(Size.M, Coercion.toType("M", Size::class.java))
        assertNull(Coercion.toType("", Char::class.javaObjectType))
        assertEquals('x', Coercion.toType("x", Char::class.javaObjectType))
        assertNull(Coercion.toType(" ", LocalDate::class.java))
        assertEquals(listOf(1, 2), Coercion.toType(listOf("1", 2), List::class.java, type("map").let { Holder::class.java.getDeclaredField("list").genericType }).let { (it as List<*>).map { v -> v.toString().toInt() } })
        assertEquals(listOf(1, 2), Coercion.toType(listOf(1, 2), List::class.java, Holder::class.java.getDeclaredField("wild").genericType))
        assertEquals("s", Coercion.toType("s", String::class.java))
        assertThrows<IllegalArgumentException> { Coercion.toType("x", Int::class.javaObjectType) }
    }

    @Test
    fun `security policy`() {
        val policy = SecurityPolicy().deny("org.konaframework.brew.fixtures.")
        assertFalse(policy.isAllowed("org.konaframework.brew.fixtures.Address"))
        assertTrue(policy.isAllowed("app.Thing"))
        assertThrows<CorruptComponentPayloadException> { policy.load(null, javaClass.classLoader) }
        assertThrows<CorruptComponentPayloadException> { policy.load(" ", javaClass.classLoader) }
        assertThrows<CorruptComponentPayloadException> { policy.load("java.lang.Runtime", javaClass.classLoader) }
        assertEquals(Holder::class.java, SecurityPolicy().load(Holder::class.java.name, javaClass.classLoader))
    }

    @Test
    fun `synth helpers and defaults`() {
        val bare = object : Synth() {
            override val key = "bare"
            override fun match(value: Any) = false
            override fun dehydrate(value: Any, context: SynthContext): Pair<Any?, Map<String, Any?>> = null to emptyMap()
            override fun hydrate(value: Any?, meta: Map<String, Any?>, context: SynthContext, type: Type?): Any? = null
        }
        assertThrows<UnsupportedOperationException> { bare.get(1, "a") }
        assertThrows<UnsupportedOperationException> { bare.set(1, "a", 1) }
        assertThrows<UnsupportedOperationException> { bare.unset(1, "a") }
        assertNull(bare.childType(null, null, "a"))
        assertEquals(String::class.java, Synth.typeArgument(type("list"), 0))
        assertEquals(Number::class.java, Synth.typeArgument(type("wild"), 0))
        assertNull(Synth.typeArgument(type("list"), 3))
        assertNull(Synth.typeArgument(String::class.java, 0))
        assertEquals(List::class.java, Synth.rawClass(type("list")))
        assertEquals(Number::class.java, Synth.rawClass((type("wild") as java.lang.reflect.ParameterizedType).actualTypeArguments[0]))
        assertNull(Synth.rawClass(null))
        assertEquals("a.b", ctx.child("a").child("b").path)
    }

    @Test
    fun `synthesizer registry paths`() {
        assertEquals("x", synths.dehydrate('x'))
        assertEquals(1.5, synths.dehydrate(1.5f))
        assertEquals(listOf("-9007199254740993", mapOf("s" to "num", "class" to "long")), synths.dehydrate(-9_007_199_254_740_993L))
        assertEquals(5L, synths.dehydrate(5L))
        assertEquals(3, synths.hydrate("3", Int::class.javaObjectType))
        assertEquals("raw", synths.hydrate("raw", null))
        assertNull(synths.hydrate(null, String::class.java))
        assertTrue(Synthesizers.isScalar(1.toShort()) && Synthesizers.isScalar(1.toByte()) && Synthesizers.isScalar(1f) && Synthesizers.isScalar('c'))
        assertFalse(Synthesizers.isScalar(Any()))
        assertFalse(Synthesizers.isTuple(listOf(1)))
        assertFalse(Synthesizers.isTuple(listOf(1, 2)))
        assertFalse(Synthesizers.isTuple(listOf(1, mapOf("x" to 1))))
        assertFalse(Synthesizers.isTuple("x"))
        assertNull(synths.getPath(null, listOf("a")))
        assertNull(synths.getPath("scalar", listOf("a")))
        assertEquals(2, synths.getPath(mapOf("a" to listOf(1, 2)), listOf("a", "1")))
        assertEquals(mapOf("a" to mapOf("b" to 1)), synths.setPath(null, null, listOf("a", "b"), 1, false))
        assertThrows<IllegalArgumentException> { synths.setPath("scalar", null, listOf("a"), 1, false) }
        assertEquals(mapOf("a" to null), synths.setPath(mapOf<String, Any?>(), type("map"), listOf("a"), null, false))
    }

    @Test
    fun `collection synths`() {
        assertNull(ArraySynth.get(listOf(1), "x"))
        assertNull(ArraySynth.get(listOf(1), "5"))
        assertEquals(listOf(1, 9), ArraySynth.set(listOf(1, 2), "1", 9))
        assertThrows<IllegalArgumentException> { ArraySynth.set(listOf(1), "x", 2) }
        assertEquals(listOf(1), ArraySynth.unset(listOf(1), "x"))
        assertEquals(listOf(1), ArraySynth.unset(listOf(1), "4"))
        assertEquals(Int::class.javaPrimitiveType, ArraySynth.childType(type("ints"), null, "0"))
        assertEquals(String::class.java, ArraySynth.childType(type("list"), null, "0"))
        assertTrue(ArraySynth.match(ArrayDeque<Int>()))
        assertFalse(ArraySynth.match(setOf(1)))
        assertFalse(ArraySynth.match("x"))
        val hydrated = ArraySynth.hydrate(listOf(1, 2), emptyMap(), ctx, type("ints"))
        assertArrayEquals(intArrayOf(1, 2), hydrated as IntArray)
        assertEquals(listOf(1), ArraySynth.hydrate(listOf(1), emptyMap(), ctx, null))

        assertNull(SetSynth.get(setOf(1), "x"))
        assertEquals(setOf(9), SetSynth.set(setOf(1), "0", 9))
        assertThrows<IllegalArgumentException> { SetSynth.set(setOf(1), "x", 2) }
        assertEquals(setOf(1), SetSynth.unset(setOf(1), "x"))
        assertEquals(setOf(1), SetSynth.unset(setOf(1), "3"))
        assertEquals(String::class.java, SetSynth.childType(type("list"), null, "0"))

        assertEquals(Int::class.javaObjectType, MapSynth.childType(type("map"), null, "a"))
        assertEquals(mapOf(1 to "x"), MapSynth.hydrate(mapOf(1 to "x"), emptyMap(), ctx, null).let { (it as Map<*, *>).mapKeys { e -> e.key.toString().toInt() } })
    }

    @Test
    fun `value synths`() {
        assertNull(EnumSynth.hydrate(null, emptyMap(), ctx, null))
        assertEquals(Status.PUBLISHED, EnumSynth.hydrate("PUBLISHED", mapOf("class" to Status::class.java.name), ctx, null))
        assertEquals(Status.DRAFT, EnumSynth.hydrate("DRAFT", emptyMap(), ctx, Status::class.java))
        assertThrows<CorruptComponentPayloadException> { EnumSynth.hydrate("X", mapOf("class" to "app.Missing"), ctx, String::class.java) }
        assertThrows<CorruptComponentPayloadException> { EnumSynth.hydrate("X", mapOf("class" to Address::class.java.name), ctx, null) }

        val date = Date.from(Instant.parse("2026-01-01T00:00:00Z"))
        assertEquals("2026-01-01T00:00:00Z" to mapOf("type" to "Date"), TemporalSynth.dehydrate(date, ctx))
        assertEquals(date, TemporalSynth.hydrate("2026-01-01T00:00:00Z", mapOf("type" to "Date"), ctx, null))
        assertEquals(Duration.ofSeconds(5), TemporalSynth.hydrate("PT5S", mapOf("type" to "Duration"), ctx, null))
        assertTrue(TemporalSynth.match(Duration.ZERO) && TemporalSynth.match(date) && !TemporalSynth.match("x"))
        assertNull(TemporalSynth.hydrate(null, emptyMap(), ctx, null))
        assertThrows<CorruptComponentPayloadException> { TemporalSynth.hydrate("x", mapOf("type" to "Nope"), ctx, null) }
        assertThrows<CorruptComponentPayloadException> { TemporalSynth.hydrate("x", emptyMap(), ctx, null) }

        assertEquals("5" to mapOf("class" to "integer"), BigNumberSynth.dehydrate(BigInteger.valueOf(5), ctx))
        assertEquals(BigInteger.TEN, BigNumberSynth.hydrate("10", mapOf("class" to "integer"), ctx, null))
        assertEquals(BigDecimal("1.0"), BigNumberSynth.hydrate("1.0", mapOf("class" to "decimal"), ctx, null))
        assertEquals(7L, BigNumberSynth.hydrate("7", mapOf("class" to "long"), ctx, null))
        assertNull(BigNumberSynth.hydrate(null, emptyMap(), ctx, null))
        assertTrue(BigNumberSynth.match(BigInteger.ONE) && !BigNumberSynth.match(1))

        assertNull(UuidSynth.hydrate(null, emptyMap(), ctx, null))
        assertEquals(UUID(0, 1), UuidSynth.hydrate(UUID(0, 1).toString(), emptyMap(), ctx, null))
    }

    class NoFactory : Wireable {
        override fun toBrew(): Any = 1
    }

    class InstanceFactory : Wireable {
        override fun toBrew(): Any = 1

        @Suppress("unused")
        fun fromBrew(value: Any?) = this

        @Suppress("unused")
        companion object {
            @JvmStatic
            fun fromBrew(a: Any?, b: Any?) = InstanceFactory()
        }
    }

    open class Base {
        var inherited = "i"
    }

    class Child : Base() {
        var own = 1

        companion object {
            @JvmStatic
            val ignored = 0
        }
    }

    class Observed : java.util.Observable() {
        var x = 1
    }

    @Test
    fun `wireable and object synths`() {
        assertEquals(250L, (WireableSynth.hydrate(250, mapOf("class" to Money::class.java.name), ctx, null) as Money).cents)
        assertThrows<IllegalStateException> { WireableSynth.hydrate(1, mapOf("class" to NoFactory::class.java.name), ctx, null) }
        assertThrows<IllegalStateException> { WireableSynth.hydrate(1, mapOf("class" to InstanceFactory::class.java.name), ctx, null) }
        assertThrows<CorruptComponentPayloadException> { WireableSynth.hydrate(1, emptyMap(), ctx, null) }

        assertEquals(String::class.java, ObjectSynth.childType(Base::class.java, null, "inherited"))
        assertEquals(String::class.java, ObjectSynth.childType(null, Child(), "inherited"))
        assertNull(ObjectSynth.childType(null, null, "x"))
        assertNull(ObjectSynth.childType(null, Child(), "missing"))
        assertEquals("i", ObjectSynth.get(Child(), "inherited"))
        assertEquals(1, ObjectSynth.get(Observed(), "x"))
        val (data, meta) = ObjectSynth.dehydrate(Child(), ctx)
        assertEquals(mapOf("own" to 1, "inherited" to "i"), data)
        assertEquals(Child::class.java.name, meta["class"])
        val set = ObjectSynth.set(Address("a", "b"), "city", "c") as Address
        assertEquals(Address("a", "c"), set)
        assertEquals(Address("x", ""), ObjectSynth.hydrate(mapOf("street" to "x"), mapOf("class" to Address::class.java.name), ctx, null))
    }
}
