package org.konaframework.brew

import org.konaframework.brew.features.InMemoryComputedCache
import org.konaframework.brew.fixtures.Calculator
import org.konaframework.brew.fixtures.Clock
import org.konaframework.brew.fixtures.Conventional
import org.konaframework.brew.fixtures.Counter
import org.konaframework.brew.fixtures.Injected
import org.konaframework.brew.fixtures.Lifecycle
import org.konaframework.brew.fixtures.Utilities
import org.konaframework.core.Container
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ComponentTest {
    private val manager = brew(mapOf("brew.conventional" to "<section>conv {{ \$this.missing }}</section>"))
        .component("counter", Counter::class.java)
        .component("lifecycle", Lifecycle::class.java)
        .component("calculator", Calculator::class.java)
        .component("utilities", Utilities::class.java)
        .component("conventional", Conventional::class.java)

    private fun mount(name: String, params: Map<String, Any?> = emptyMap()) = manager.mountComponent(name, params, null)

    private fun next(result: ComponentResult, updates: Map<String, Any?> = emptyMap(), vararg calls: Map<String, Any?>) =
        manager.updateComponent(parse(manager.json(result.snapshot)), updates, calls.toList())

    @Test
    fun `mount lifecycle order including interface mixins`() {
        val log = (mount("lifecycle").component as Lifecycle).log
        assertEquals(
            listOf(
                "boot", "bootHasAudit", "initializeHasAudit", "mount", "mountHasAudit", "booted", "bootedHasAudit",
                "rendering", "renderingHasAudit", "rendered", "renderedHasAudit",
                "dehydrate", "dehydrateHasAudit", "dehydrateName:",
            ),
            log,
        )
    }

    @Test
    fun `update lifecycle order and nested update hooks`() {
        val first = mount("lifecycle")
        val result = next(first, mapOf("name" to "x"))
        val log = (result.component as Lifecycle).log.drop(14)
        assertEquals(
            listOf(
                "boot", "bootHasAudit", "initializeHasAudit", "hydrate", "hydrateHasAudit", "hydrateName:", "booted",
                "bootedHasAudit", "updating:name=x", "updatingHasAudit:name", "updatingName:x", "updated:name",
                "updatedHasAudit:name", "updatedName:x", "rendering", "renderingHasAudit", "rendered", "renderedHasAudit",
                "dehydrate", "dehydrateHasAudit", "dehydrateName:x",
            ),
            log,
        )
        val nested = (next(first, mapOf("items.0.title" to "z")).component as Lifecycle).log.drop(14)
        assertEquals(
            listOf(
                "updating:items.0.title=z", "updatingHasAudit:items.0.title", "updatingItems:0.title",
                "updated:items.0.title", "updatedHasAudit:items.0.title", "updatedItems:0.title", "updatedItems0Title:title",
            ),
            nested.filter { it.startsWith("upd") },
        )
    }

    @Test
    fun `exception hook can stop propagation`() {
        val first = mount("lifecycle")
        val error = assertThrows<IllegalStateException> { next(first, emptyMap(), call("fail")) }
        assertEquals("boom", error.message)
        val swallowed = next(first, mapOf("swallow" to true), call("fail"))
        val log = (swallowed.component as Lifecycle).log
        assertTrue(log.containsAll(listOf("exception:boom", "exceptionHasAudit")))
        assertTrue(swallowed.html!!.contains("wire:id"))
    }

    @Test
    fun `lifecycle, base and unknown methods are not callable`() {
        val first = mount("lifecycle")
        for (method in listOf("mount", "boot", "booted", "hydrate", "hydrateName", "dehydrate", "updatingName", "updatedName", "rendering", "rendered", "exception", "bootHasAudit")) {
            assertThrows<DirectlyCallingLifecycleHooksNotAllowedException>(method) { next(first, emptyMap(), call(method)) }
        }
        for (method in listOf("render", "template", "fill", "reset", "skipRender", "id", "getLog", "missing", "toString")) {
            assertThrows<MethodNotFoundException>(method) { next(first, emptyMap(), call(method)) }
        }
    }

    @Test
    fun `computed properties memoize, persist and cache`() {
        val first = mount("calculator")
        assertTrue(first.html!!.contains("5-2"), first.html)
        assertEquals(1, (first.component as Calculator).calls)
        val read = next(first, emptyMap(), call("readSum"))
        assertEquals(listOf(10), read.effects["returns"])
        assertEquals(2, (read.component as Calculator).calls)
        assertThrows<CannotCallComputedDirectlyException> { next(first, emptyMap(), call("sum")) }

        val persisted = manager.computedValue(read.component, "persisted")
        val later = next(read, mapOf("a" to 7))
        assertEquals(persisted, manager.computedValue(later.component, "persisted"))
        assertEquals(42, manager.computedValue(later.component, "shared"))
        assertEquals("dk", manager.computedValue(later.component, "sharedDefaultKey"))
        assertEquals(42, manager.computedValue(mount("calculator").component, "shared"))

        val forgotten = next(later, emptyMap(), call("forget"))
        assertTrue(forgotten.html!!.contains("10-7"), forgotten.html)
        manager.forgetComputed(forgotten.component, "persisted")
        assertEquals(70, manager.computedValue(forgotten.component, "persisted"))
        manager.forgetComputed(forgotten.component, "shared")
        assertThrows<IllegalArgumentException> { manager.computedValue(forgotten.component, "nope") }
    }

    @Test
    fun `in-memory computed cache expires`() {
        var now = 0L
        val cache = InMemoryComputedCache { now }
        cache.put("k", "v", 1)
        assertEquals("v", cache.get("k")!!.value)
        now = 1000
        assertNull(cache.get("k"))
        assertNull(cache.get("missing"))
        cache.put("k", null, 5)
        cache.forget("k")
        assertNull(cache.get("k"))
    }

    @Test
    fun `fill, reset, pull, only and all`() {
        val first = mount("utilities")
        fun run(vararg calls: Map<String, Any?>) = next(first, emptyMap(), *calls)
        assertEquals(mapOf("a" to 1, "b" to "two", "c" to listOf(1)), first.component.all())
        assertEquals(mapOf("a" to 1, "b" to "one-ignored").keys, setOf("a", "b"))
        assertEquals(mapOf("a" to 1, "b" to "two", "c" to listOf(1)), run(call("mutate"), call("resetAll")).component.all())
        assertEquals(mapOf("a" to 1, "b" to "changed", "c" to listOf(1, 2)), run(call("mutate"), call("resetA")).component.all())
        assertEquals(mapOf("a" to 9, "b" to "two", "c" to listOf(1)), run(call("mutate"), call("resetExceptA")).component.all())
        val pulled = run(call("mutate"), call("pullB"))
        assertEquals(listOf(null, "changed"), pulled.effects["returns"])
        assertEquals("two", (pulled.component as Utilities).b)
        assertEquals(
            listOf(null, mapOf("a" to 9, "b" to "changed", "c" to listOf(1, 2))),
            run(call("mutate"), call("pullAll")).effects["returns"],
        )
        assertEquals(listOf(null, mapOf("a" to 9, "b" to "changed")), run(call("mutate"), call("pullMany")).effects["returns"])
        assertEquals(listOf(mapOf("a" to 1)), run(call("onlyA")).effects["returns"])
        assertEquals(mapOf("a" to 5, "b" to "filled", "c" to listOf(1)), run(call("fillMap")).component.all())
        assertThrows<PublicPropertyNotFoundException> { first.component.reset("nope") }
        first.component.resetExcept("a", "b", "c")
    }

    @Test
    fun `reset works on detached components`() {
        val loose = Utilities()
        loose.a = 4
        loose.reset()
        assertEquals(1, loose.a)
        assertThrows<PublicPropertyNotFoundException> { loose.reset("zzz") }
        assertThrows<BrewException> { loose.id() }
    }

    @Test
    fun `renderless, skipRender and forceRender`() {
        val first = mount("counter")
        val quiet = next(first, emptyMap(), call("quiet"))
        assertFalse(quiet.effects.containsKey("html"))
        assertEquals(1, (quiet.component as Counter).count)
        assertTrue(next(first, emptyMap(), call("quiet"), call("increment")).effects.containsKey("html"))
        val modifier = mapOf("path" to "", "method" to "increment", "params" to emptyList<Any?>(), "metadata" to mapOf("renderless" to true))
        assertFalse(next(first, emptyMap(), modifier).effects.containsKey("html"))
        assertFalse(next(first, emptyMap(), call("skip")).effects.containsKey("html"))
        val replaced = next(first, emptyMap(), call("skipWith")).effects["html"] as String
        assertTrue(replaced.startsWith("<div wire:id=\""), replaced)
        assertTrue(replaced.contains(">skipped</div>"))
        assertNull(next(first, emptyMap(), call("skip")).html)

        val component = first.component
        component.forceRender()
        component.skipRender()
        component.renderless()
        assertTrue(first.html!!.contains("Count: 0"))
    }

    @Test
    fun `magic actions`() {
        val first = mount("counter")
        assertEquals(0, (next(first, emptyMap(), call("\$refresh")).component as Counter).count)
        assertTrue(next(first, emptyMap(), call("\$commit")).effects.containsKey("html"))
        assertEquals(5, (next(first, emptyMap(), call("\$set", "count", "5")).component as Counter).count)
        val calc = mount("utilities")
        assertEquals(listOf(1, 2), (next(calc, emptyMap(), call("\$set", "c.1", 2)).component as Utilities).c)
        val lifecycle = mount("lifecycle")
        val toggled = next(lifecycle, emptyMap(), call("\$toggle", "swallow"))
        assertEquals(true, (toggled.component as Lifecycle).swallow)
        assertEquals(false, (next(toggled, emptyMap(), call("\$toggle", "swallow")).component as Lifecycle).swallow)
        val nested = next(lifecycle, emptyMap(), call("\$toggle", "items.0.done"))
        assertEquals("true", (nested.component as Lifecycle).items[0]["done"])
        assertThrows<CannotUpdateLockedPropertyException> {
            val m = brew().component("kitchen", org.konaframework.brew.fixtures.Kitchen::class.java)
            val k = m.mountComponent("kitchen", emptyMap(), null)
            m.updateComponent(k.snapshot, emptyMap(), listOf(call("\$set", "secret", "x")))
        }
    }

    @Test
    fun `action overloads, dependency injection and named defaults`() {
        val first = mount("counter")
        assertEquals(7, (next(first, emptyMap(), call("add", 7)).component as Counter).count)
        assertEquals(6, (next(first, emptyMap(), call("add", 2, 3)).component as Counter).count)
        val container = Container()
        container.singleton(Clock::class.java) { Clock() }
        val di = brew(container = container).component("injected", Injected::class.java)
        val mounted = di.mountComponent("injected", emptyMap(), null)
        val ran = di.updateComponent(mounted.snapshot, emptyMap(), listOf(call("run", "t-")))
        assertEquals("t-tick", (ran.component as Injected).result)
    }

    @Test
    fun `convention view lookup and component accessors`() {
        val html = manager.mount("conventional")
        assertTrue(html.contains(">conv </section>"), html)
        assertTrue(html.startsWith("<section wire:snapshot="))
        val view = mount("conventional").view!!
        assertEquals("brew.conventional", view.name)
    }

    @Test
    fun `render must return a supported type`() {
        class Bad : Component() {
            override fun render(): Any = 42
        }
        val m = brew().component("bad") { Bad() }
        assertThrows<BrewException> { m.mount("bad") }
    }

    @Test
    fun `components without a root element fail`() {
        class Rootless : Component() {
            override fun template() = "just text"
        }
        assertThrows<RootTagMissingFromViewException> { brew().component("rootless") { Rootless() }.mount("rootless") }
    }

    @Test
    fun `views require a configured engine`() {
        val bare = BrewManager(BrewConfig(key = "k")).component("counter", Counter::class.java)
        assertThrows<BrewException> { bare.mount("counter") }
        bare.setViews(org.konaframework.crema.CremaEngine(org.konaframework.crema.MapViewFinder()))
        assertTrue(bare.mount("counter").contains("Count: 0"))
        assertTrue(bare.views().extensions.hasTag("brew"))
    }
}
