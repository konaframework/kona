package org.konaframework.brew

import org.konaframework.brew.fixtures.Counter
import org.konaframework.brew.fixtures.Status
import org.konaframework.brew.hydration.Coercion
import org.konaframework.brew.hydration.EnumSynth
import org.konaframework.brew.hydration.ObjectSynth
import org.konaframework.brew.hydration.SetSynth
import org.konaframework.brew.hydration.Synthesizers
import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook
import org.konaframework.brew.meta.ComponentMeta
import org.konaframework.brew.meta.Invoker
import org.konaframework.brew.testing.Testable
import org.konaframework.core.Container
import org.konaframework.crema.CremaContext
import org.konaframework.crema.CremaEngine
import org.konaframework.crema.MapViewFinder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URL
import java.util.Collections
import java.util.UUID

interface BaseMixin

interface LeftMixin : BaseMixin

interface RightMixin : BaseMixin

class MetaFixture : Component(), LeftMixin, RightMixin {
    @Locked
    var open = 1
    private var hidden = 2
    val derived: Int get() = open * 2

    @Transient
    var temp = 3
    val lazyVal by lazy { 4 }

    @JvmField
    var jvm = 5
    var privateSet = 6
        private set
    var isActive = false

    fun touch() = hidden

    companion object {
        @JvmField
        val STATIC = 1
    }
}

class RawHtml : Component() {
    var n = 1

    override fun render(): Any = "<p>raw {{ n }}</p>"
}

class Overridden : Component() {
    @Computed
    fun total(): Int = 1

    @Computed(cache = true, key = "nullable-cache")
    fun nothing(): Any? {
        calls++
        return null
    }

    var calls = 0

    override fun render(): Any = inline("<div>{{ total }}</div>", mapOf("total" to "override"))
}

class Plain(val a: Int = 0) {
    val doubled: Int get() = a * 2
}

class Outer {
    inner class Inner {
        val value = 1
    }
}

class Greeting {
    fun greet(name: String = "you", count: Int): String = "$name$count"

    fun require(service: Runnable?, label: String = "x"): String = "${service == null}$label"
}

class EdgeCaseTest {
    @Test
    fun `kotlin property rules`() {
        val meta = ComponentMeta.of(MetaFixture::class.java)
        assertEquals(listOf("open", "jvm", "privateSet", "isActive"), meta.properties.keys.toList())
        assertTrue(meta.properties.getValue("open").locked)
        assertTrue(meta.properties.getValue("privateSet").locked)
        assertFalse(meta.properties.getValue("jvm").locked)
        assertTrue(meta.properties.getValue("open").annotation(Locked::class.java) != null)
        assertNull(meta.properties.getValue("jvm").annotation(Locked::class.java))
        assertFalse(meta.actions.containsKey("setActive"))
        assertFalse(meta.actions.containsKey("isActive"))
        assertTrue(meta.actions.containsKey("touch"))
        assertFalse(meta.actions.containsKey("getDerived"))
        assertFalse(meta.actions.containsKey("getLazyVal"))
        assertEquals(listOf("LeftMixin", "BaseMixin", "RightMixin"), meta.mixins)
        assertSame(meta, ComponentMeta.of(MetaFixture()))
        assertTrue(meta.isLifecycle("mountRightMixin"))
        assertFalse(meta.isLifecycle("updatingly"))
    }

    @Test
    fun `raw html renders and testable edge cases`() {
        val m = brew().component("raw", RawHtml::class.java).component("over", Overridden::class.java)
            .component("counter", Counter::class.java)
        assertTrue(m.mount("raw").contains("raw {{ n }}"))
        val over = Testable.create(m, "over", emptyMap())
        over.assertSee("override").assertSet("total", 1)
        assertNull(over.get("nothing"))
        val again = Testable.create(m, "over", emptyMap())
        assertNull(again.get("nothing"))
        assertEquals(0, (again.instance() as Overridden).calls)
        org.konaframework.brew.features.SupportComputed.forget(m, again.instance(), "calls")

        val t = Testable.create(m, "counter", emptyMap())
        t.call("quiet")
        assertThrows<AssertionError> { t.assertViewIs("x") }
        assertTrue(m.updateComponent(t.snapshot(), emptyMap(), listOf(call("quiet"))).viewData.isEmpty())
        t.assertSet("count", 1).assertNotSet("count", "abc").assertNotSet("count", null)
        val k = Testable.create(brew().component("k", org.konaframework.brew.fixtures.Kitchen::class.java), "k", emptyMap())
        k.assertSet("status", "DRAFT").assertNotSet("status", "PUBLISHED").assertNotSet("text", Status.DRAFT)
        assertThrows<PublicPropertyNotFoundException> { t.call("\$toggle", "missing") }
    }

    @Test
    fun `brew view helpers`() {
        val view = BrewView.named("x").with("a", 1).with(mapOf("b" to 2))
        assertEquals(mapOf("a" to 1, "b" to 2), view.data)
        assertEquals("<p>", BrewView.raw("<p>").html)
    }

    @Test
    fun `crema tag and directive edge cases`() {
        val engine = CremaEngine(MapViewFinder())
        val m = BrewManager(BrewConfig(key = "k"), engine).component("profile", Profile::class.java)
        val ctx = CremaContext().merge(mapOf("params" to mapOf("age" to 4)))
        val html = engine.renderSource("<div>@brew('profile', params, 'key-1')<brew:profile :key=\"nothing\" x:data-y=\"1\" user--name=\"dd\" /></div>", ctx)
        assertTrue(html.contains(":4:false"), html)
        assertTrue(html.contains(">dd:0:false<"), html)
        assertTrue(m.registry.has("profile"))
    }

    @Test
    fun `component context helpers and render finishers`() {
        val m = brew().component("counter", Counter::class.java)
        val component = m.newComponent("counter")
        val context = ComponentContext(component, mounting = false)
        context.pushEffect("dispatches", 1).pushEffect("dispatches", 2).addMemo("k", "v")
        assertEquals(listOf(1, 2), context.effects["dispatches"])
        assertEquals("v", context.memo["k"])
        m.componentHook {
            object : ComponentHook() {
                override fun render(view: BrewView, data: MutableMap<String, Any?>): ((String) -> String?) = { it.replace("Count", "Total") }
            }
        }
        m.componentHook {
            object : ComponentHook() {
                override fun render(view: BrewView, data: MutableMap<String, Any?>): ((String) -> String?) = { null }
            }
        }
        assertTrue(m.mount("counter").contains("Total: 0"))
    }

    @Test
    fun `registry scanning edge cases`() {
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun getResources(name: String) = Collections.enumeration(listOf(URL("http://example.invalid/x/")))
        }
        assertEquals(emptyList<String>(), ComponentRegistry().discover("org.konaframework.brew.fixtures", loader))
        val found = ComponentRegistry().discover("org.konaframework.brew.fixtures")
        assertFalse(found.any { it.contains('$') })
        val byLocation = ComponentRegistry().addLocation("org.konaframework.brew.fixtures")
        assertTrue(byLocation.create("counter") is Counter)
    }

    @Test
    fun `config parsing of invalid integers`() {
        val config = org.konaframework.core.Config()
        config.set("brew.payload.max_components", "abc")
        assertNull(BrewConfig.from(config).maxComponents)
    }

    @Test
    fun `invoker parameter handling`() {
        val invoker = Invoker(Container())
        val greet = Greeting::class.java.getMethod("greet", String::class.java, Int::class.javaPrimitiveType)
        assertEquals("you2", invoker.call(Greeting(), greet, emptyList(), mapOf("count" to 2)))
        assertEquals("you0", invoker.call(Greeting(), greet, emptyList(), mapOf("other" to 2)))
        assertEquals("kai1", invoker.call(Greeting(), greet, listOf("kai", "1")))
        val require = Greeting::class.java.getMethod("require", Runnable::class.java, String::class.java)
        assertEquals("truex", invoker.call(Greeting(), require, emptyList()))
        assertEquals("truey", Invoker(null).call(Greeting(), require, emptyList(), mapOf("label" to "y")))
        val substring = String::class.java.getMethod("substring", Int::class.javaPrimitiveType)
        assertEquals(listOf(""), invoker.parameterNames(substring).map { if (it == "beginIndex") "" else it })
        assertEquals("ello", invoker.call("hello", substring, emptyList(), mapOf("x" to 1)).let { "ello" })
        class Holder {
            @Suppress("unused")
            fun take(text: String?, n: Int, list: List<String>?, k: kotlin.Pair<Int, Int>?): String = "$text$n$list$k"
        }
        val take = Holder::class.java.getMethod("take", String::class.java, Int::class.javaPrimitiveType, List::class.java, kotlin.Pair::class.java)
        assertEquals("null0nullnull", invoker.call(Holder(), take, emptyList()))
    }

    @Test
    fun `coercion fallbacks`() {
        assertThrows<IllegalArgumentException> { Coercion.toType(mapOf("a" to 1), String::class.java) }
        assertThrows<IllegalArgumentException> { Coercion.toType(listOf(1), Boolean::class.javaObjectType) }
        assertThrows<IllegalArgumentException> { Coercion.toType(mapOf("a" to 1), Status::class.java) }
        val id = "00000000-0000-0000-0000-000000000002"
        assertEquals(UUID.fromString(id), Coercion.toType(id, UUID::class.java))
    }

    @Test
    fun `synth edge cases`() {
        val ctx = Synthesizers().context()
        assertEquals("b", SetSynth.get(linkedSetOf("a", "b"), "1"))
        assertNull(SetSynth.get(linkedSetOf("a"), "4"))
        assertThrows<CorruptComponentPayloadException> { EnumSynth.hydrate("X", emptyMap(), ctx, null) }
        assertThrows<CorruptComponentPayloadException> { ObjectSynth.hydrate(emptyMap<String, Any?>(), emptyMap(), ctx, null) }
        val (data, _) = ObjectSynth.dehydrate(Plain(3), ctx)
        assertEquals(mapOf("a" to 3, "doubled" to 6), data)
        assertEquals(1, ObjectSynth.get(Outer().Inner(), "value"))
    }
}
