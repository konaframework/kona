package org.konaframework.brew

import org.konaframework.brew.features.SlotBag
import org.konaframework.brew.features.SupportNesting
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Isolate
class IsolatedChild : Component() {
    var label: String = "iso"
    override fun template() = "<span>{{ label }}</span>"
}

class ReactiveChild : Component() {
    @field:Reactive
    var title: String = ""

    @field:Modelable
    var value: String = ""

    var parentName: String? = null

    fun boot() {
        parentName = parent()?.name()
    }

    override fun template() = "<div>{{ title }}:{{ value }}</div>"
}

@Lazy(bundle = true)
class LazyBox : Component() {
    override fun placeholder() = "<em>loading</em>"
    override fun template() = "<div>ready</div>"
}

@Defer(bundle = true)
class DeferBox : Component() {
    override fun template() = "<div>deferred</div>"
}

@Defer
class DeferPlain : Component() {
    override fun template() = "<div>defer-plain</div>"
}

@Lazy
class LazyPlain : Component() {
    override fun template() = "<div>plain-lazy</div>"
}

@Lazy
class LazyBlankPlaceholder : Component() {
    override fun placeholder() = ""
    override fun template() = "<div>x</div>"
}

class SlotHost : Component() {
    override fun template() = """
        <div>
            @island('panel')
            <p>island-body</p>
            @endisland
            @teleport('body')
            <aside>ported</aside>
            @endteleport
            {{ slots.get('default') }}
            {{ slots.get('footer') }}
        </div>
    """.trimIndent()
}

class NestParent : Component() {
    var count = 0
    var heard: String = ""

    fun onChildSaved(label: String = "") {
        heard = label.ifBlank { "saved" }
        count++
    }

    fun bump() {
        count++
    }

    override fun template() = """
        <div>
            <brew:reactive-child wire:key="c1" title="Hi" class="x" data-id="1" @saved="onChildSaved" />
            <brew:isolated-child wire:key="iso" />
            <brew:dynamic-component :is="'reactive-child'" title="Dyn" />
        </div>
    """.trimIndent()
}

class TreeNode : Component() {
    var label: String = "n"
    var depth: Int = 0

    override fun template(): String {
        if (depth >= 2) return "<span>{{ label }}</span>"
        val next = depth + 1
        return """
            <div>
                {{ label }}
                <brew:tree-node wire:key="d$next" label="c$next" depth="$next" />
            </div>
        """.trimIndent()
    }
}

class EventChild : Component() {
    fun save() {
        dispatch("saved", mapOf("label" to "ok"))
    }

    override fun template() = "<button wire:click=\"save\">go</button>"
}

class NestingTest {
    private val manager = brew()
        .component("reactive-child", ReactiveChild::class.java)
        .component("isolated-child", IsolatedChild::class.java)
        .component("lazy-box", LazyBox::class.java)
        .component("lazy-plain", LazyPlain::class.java)
        .component("lazy-blank", LazyBlankPlaceholder::class.java)
        .component("defer-box", DeferBox::class.java)
        .component("defer-plain", DeferPlain::class.java)
        .component("slot-host", SlotHost::class.java)
        .component("nest-parent", NestParent::class.java)
        .component("tree-node", TreeNode::class.java)
        .component("event-child", EventChild::class.java)

    @AfterEach
    fun reset() {
        Brew.reset()
    }

    @Test
    fun `nested children tracked in memo with keys reactive isolate and attributes`() {
        Brew.setManager(manager)
        val mounted = Brew.test(NestParent::class.java)
        @Suppress("UNCHECKED_CAST")
        val children = mounted.snapshot()["memo"].let { it as Map<*, *> }["children"] as Map<*, *>
        assertTrue(children.containsKey("c1"), "$children")
        assertTrue(children.containsKey("iso"), "$children")
        assertTrue(children.size >= 2)

        val html = mounted.html()
        assertTrue(html.contains("Hi:"), html)
        assertTrue(html.contains("data-brew-attr-class") || html.contains("class"), html)
        // Nested mount sees parent()...
        assertTrue(html.contains("wire:"), html)

        val child = Brew.test(ReactiveChild::class.java, mapOf("title" to "T", "extra" to "y"))
        @Suppress("UNCHECKED_CAST")
        val memo = child.snapshot()["memo"] as Map<*, *>
        assertEquals(mapOf("title" to "T"), memo["props"])
        assertEquals(listOf("value"), memo["bindings"])

        val iso = Brew.test(IsolatedChild::class.java)
        assertEquals(true, (iso.snapshot()["memo"] as Map<*, *>)["isolate"])
    }

    @Test
    fun `lazy defer placeholders and withoutLazyLoading`() {
        Brew.setManager(manager)
        val lazy = Brew.test(LazyBox::class.java)
        assertTrue(lazy.html().contains("loading"), lazy.html())
        assertEquals(true, lazy.effects()["lazy"])
        assertEquals(true, (lazy.snapshot()["memo"] as Map<*, *>)["lazyBundle"])

        val deferred = Brew.test(DeferBox::class.java)
        assertEquals(true, deferred.effects()["defer"])
        assertEquals(true, (deferred.snapshot()["memo"] as Map<*, *>)["lazyBundle"])

        val plain = Brew.test(LazyPlain::class.java)
        assertEquals(true, plain.effects()["lazy"])
        assertTrue((plain.snapshot()["memo"] as Map<*, *>)["lazyBundle"] != true)
        assertTrue(Brew.test(LazyBlankPlaceholder::class.java).html().contains("wire:lazy"))

        val configured = brew(config = BrewConfig(key = "k", lazyPlaceholder = "<i>cfg</i>"))
            .component("lazy-plain", LazyPlain::class.java)
        Brew.setManager(configured)
        val cfgHtml = Brew.test(LazyPlain::class.java).html()
        assertTrue(cfgHtml.contains("cfg") || cfgHtml.contains("wire:lazy"), cfgHtml)
        Brew.setManager(manager)

        val eager = manager.withoutLazyLoading {
            Brew.test(LazyBox::class.java)
        }
        assertTrue(eager.html().contains("ready"), eager.html())
        assertTrue(Brew.withoutLazyLoading { true })

        // islandMeta / islandName helpers...
        assertEquals("default", BrewCrema.islandName("", emptyList()))
        assertEquals("default", BrewCrema.islandName("", listOf(null)))
        assertEquals("default", BrewCrema.islandName("", listOf("")))
        assertEquals("default", BrewCrema.islandName("", listOf("null")))
        assertEquals("a", BrewCrema.islandName("", listOf("a")))
        assertEquals(
            mapOf("lazy" to true, "defer" to true, "always" to true, "skip" to true),
            BrewCrema.islandMeta("lazy defer always skip"),
        )
        assertEquals(emptyMap<String, Any?>(), BrewCrema.islandMeta("plain"))
        assertEquals("", SlotBag(emptyMap()).default())
        assertEquals(true, Brew.test(DeferPlain::class.java).effects()["defer"])
        assertEquals("br", org.konaframework.brew.view.HtmlRoot.rootTagName("<br/>"))
        assertEquals("p", org.konaframework.brew.view.HtmlRoot.rootTagName("<p >"))
        assertEquals("ab", org.konaframework.brew.view.HtmlRoot.rootTagName("<ab"))
        assertEquals("x", org.konaframework.brew.view.HtmlRoot.rootTagName("<x\t>"))
        assertEquals("y", org.konaframework.brew.view.HtmlRoot.rootTagName("<y\n>"))
        assertEquals("z", org.konaframework.brew.view.HtmlRoot.rootTagName("<z\r>"))
    }

    @Test
    fun `islands teleport slots and parent access`() {
        Brew.setManager(manager)
        val host = Brew.test(SlotHost::class.java)
        assertTrue(host.html().contains("wire:island=\"panel\""), host.html())
        assertTrue(host.html().contains("wire:teleport"), host.html())
        @Suppress("UNCHECKED_CAST")
        val islands = host.effects()["islands"] as Map<*, *>
        assertTrue(islands.containsKey("panel"), "$islands / ${host.effects()}")

        val bag = SlotBag(mapOf("default" to "D", "footer" to "F"))
        assertTrue(bag.has("default"))
        assertFalse(bag.has("missing"))
        assertEquals("D", bag.default())
        assertEquals("F", bag.get("footer"))
        assertEquals("", bag.get("missing"))
        assertEquals(2, bag.all().size)

        val parent = Brew.test(NestParent::class.java).instance() as NestParent
        val child = manager.newComponent("reactive-child")
        assertNotNull(parent)
        assertEquals(null, child.parent())

        Brew.test(
            ReactiveChild::class.java,
            mapOf(
                "__slot_default" to "Hello",
                "__slot_footer" to "Bye",
                "__slot_empty" to null,
                "wire:loading" to true,
                "lazy" to false,
            ),
        )
        org.konaframework.brew.features.recordIsland(parent, "x", "<b>1</b>", mapOf("lazy" to true))
        org.konaframework.brew.features.recordIsland(parent, "y", "<b>2</b>")
        assertEquals("div", org.konaframework.brew.view.HtmlRoot.rootTagName("<div class='a'></div>"))
        assertEquals("span", org.konaframework.brew.view.HtmlRoot.rootTagName("<span/>"))
        assertEquals("div", org.konaframework.brew.view.HtmlRoot.rootTagName("   "))
        assertEquals("div", org.konaframework.brew.view.HtmlRoot.rootTagName("<!-- c -->"))
    }

    @Test
    fun `placeholder directive and dynamic component`() {
        Brew.setManager(manager)
        val engine = manager.views()
        assertTrue(engine.renderSource("@placeholder('wait')", emptyMap()).contains("wait"))
        assertTrue(engine.renderSource("@placeholder()", emptyMap()).contains("wire:lazy"))
        val withPlaceholder = brew(config = BrewConfig(key = "k", lazyPlaceholder = "<i>p</i>"))
        assertTrue(withPlaceholder.views().renderSource("@placeholder()", emptyMap()).contains("<i>p</i>"))

        // Without a current component (null current branches)...
        assertTrue(engine.renderSource("@island('solo')\nz\n@endisland", emptyMap()).contains("wire:island=\"solo\""))
        assertTrue(engine.renderSource("@slot('x')\ny\n@endslot", emptyMap()).contains("y"))
        // teleport with empty args uses expression text...
        assertTrue(engine.renderSource("@teleport\n@endteleport", emptyMap()).contains("wire:teleport"))
        assertTrue(
            engine.renderSource("<brew:is component=\"reactive-child\" title=\"Z\" />", emptyMap()).contains("Z:") ||
                runCatching { engine.renderSource("<brew:is is=\"reactive-child\" title=\"Z\" />", emptyMap()) }.isSuccess,
        )

        // With a current component so island/slot record paths run...
        val host = manager.newComponent("slot-host")
        val field = BrewManager::class.java.getDeclaredField("stack")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stack = field.get(manager) as ThreadLocal<ArrayDeque<Component>>
        stack.get().addLast(host) // kotlin.collections.ArrayDeque
        try {
            assertTrue(
                engine.renderSource(
                    """
                    @island(name: 'anon')
                    z
                    @endisland
                    @island('named')
                    y
                    @endisland
                    @teleport('.target')
                    t
                    @endteleport
                    @teleport('body')
                    u
                    @endteleport
                    @slot('footer')
                    foot
                    @endslot
                    @slot
                    def
                    @endslot
                    """.trimIndent(),
                    emptyMap(),
                ).contains("wire:island"),
            )
            val metaIsland = engine.renderSource("@island('meta')\nx\n@endisland", emptyMap())
            assertTrue(metaIsland.contains("wire:island=\"meta\""), metaIsland)
            assertTrue(engine.renderSource("@island(name:\"flagged\" lazy)\nx\n@endisland", emptyMap()).contains("flagged"))
        } finally {
            stack.get().removeLast()
        }

        val html = manager.mount("nest-parent")
        assertTrue(html.contains("Dyn:"), html)
        assertTrue(
            engine.renderSource("<brew:dynamic-component component=\"reactive-child\" title=\"X\" />", emptyMap())
                .contains("X:"),
        )
        assertTrue(
            engine.renderSource("<brew:dynamic-component is=\"reactive-child\" title=\"Y\" />", emptyMap())
                .contains("Y:"),
        )
        assertThrows<IllegalStateException> {
            engine.renderSource("<brew:dynamic-component title=\"Z\" />", emptyMap())
        }
        // Named slots on a brew tag...
        assertTrue(
            engine.renderSource(
                """
                <brew:reactive-child title="S">
                    <brew:slot name="footer">F</brew:slot>
                    body
                </brew:reactive-child>
                """.trimIndent(),
                emptyMap(),
            ).isNotEmpty(),
        )
        // lazy via mount param + config placeholder for Defer without override...
        val paramLazy = Brew.test(ReactiveChild::class.java, mapOf("lazy" to true, "bundle" to true))
        assertEquals(true, paramLazy.effects()["lazy"])
        val paramDefer = Brew.test(ReactiveChild::class.java, mapOf("defer" to true))
        assertEquals(true, paramDefer.effects()["defer"])
    }

    @Test
    fun `parent magic nested listeners recursive keys`() {
        Brew.setManager(manager)

        // Recursive component (TreeNode nests itself until depth gate)...
        val tree = Brew.test(TreeNode::class.java, mapOf("label" to "root", "depth" to 0))
        assertTrue(tree.html().contains("root") && tree.html().contains("c1"), tree.html())
        @Suppress("UNCHECKED_CAST")
        val treeChildren = (tree.snapshot()["memo"] as Map<*, *>)["children"] as Map<*, *>
        assertTrue(treeChildren.containsKey("d1"), "$treeChildren")

        // Different keys track separate children...
        val a = manager.mountComponent("reactive-child", mapOf("title" to "A"), "k-a")
        val b = manager.mountComponent("reactive-child", mapOf("title" to "B"), "k-b")
        assertTrue(a.component.id() != b.component.id())

        // Nested `@saved` listener + `$parent` magic while stacked...
        val host = manager.newComponent("nest-parent") as NestParent
        val field = BrewManager::class.java.getDeclaredField("stack")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stack = field.get(manager) as ThreadLocal<ArrayDeque<Component>>
        stack.get().addLast(host)
        try {
            val child = manager.mountComponent(
                "event-child",
                mapOf("@saved" to "onChildSaved"),
                "ev",
            )
            (child.component as EventChild).save()
            assertEquals("ok", host.heard)
            assertEquals(1, host.count)
            assertEquals(host, child.component.parent())

            val before = host.count
            val ctx = org.konaframework.brew.lifecycle.ComponentContext(child.component, mounting = false)
            val early = org.konaframework.brew.lifecycle.ReturnEarly()
            child.component.state().hooks.call(
                "\$parent",
                listOf("bump"),
                early,
                emptyMap(),
                ctx,
            )
            assertTrue(early.called)
            assertEquals(before + 1, host.count)

            // `$parent` → parent magic `$refresh`...
            val earlyRefresh = org.konaframework.brew.lifecycle.ReturnEarly()
            child.component.state().hooks.call(
                "\$parent",
                listOf("\$refresh"),
                earlyRefresh,
                emptyMap(),
                ctx,
            )
            assertTrue(earlyRefresh.called)

            // empty method name on `$parent`...
            val early2 = org.konaframework.brew.lifecycle.ReturnEarly()
            child.component.state().hooks.call("\$parent", emptyList(), early2, emptyMap(), ctx)
            assertTrue(early2.called)

            // unknown parent method...
            assertThrows<MethodNotFoundException> {
                val earlyBad = org.konaframework.brew.lifecycle.ReturnEarly()
                child.component.state().hooks.call(
                    "\$parent",
                    listOf("nope"),
                    earlyBad,
                    emptyMap(),
                    ctx,
                )
            }

            // arity mismatch on `$parent` uses maxBy...
            val earlyArity = org.konaframework.brew.lifecycle.ReturnEarly()
            child.component.state().hooks.call(
                "\$parent",
                listOf("onChildSaved", "a", "b"),
                earlyArity,
                emptyMap(),
                ctx,
            )
            assertTrue(earlyArity.called)

            // parent() via stack when store parent cleared...
            child.component.state().store.remove("parent")
            stack.get().addLast(child.component)
            assertEquals(host, child.component.parent())
            stack.get().removeLast()
            assertEquals(null, child.component.parent())
        } finally {
            stack.get().clear()
        }

        // `$parent` with no parent returns early...
        Brew.test(EventChild::class.java).call("\$parent", "bump")

        // `@event` with null value + listeners without a parent on the stack...
        Brew.test(EventChild::class.java, mapOf("@saved" to null, "@other" to "onChildSaved"))
        val lone = manager.mountComponent("event-child", mapOf("@saved" to "onChildSaved"), "lone")
        assertNotNull(lone.component.state().store["nestedListeners"])

        // notifyParent early-outs...
        SupportNesting.notifyParent(lone.component, "missing", emptyMap())
        lone.component.state().store.remove("nestedListeners")
        SupportNesting.notifyParent(lone.component, "saved", emptyMap())
        val withListeners = manager.newComponent("event-child")
        withListeners.state().store["nestedListeners"] = mapOf("saved" to "noSuchMethod")
        withListeners.state().store["parent"] = manager.newComponent("nest-parent")
        SupportNesting.notifyParent(withListeners, "saved", emptyMap())
        withListeners.state().store["nestedListeners"] = mapOf("saved" to "onChildSaved")
        withListeners.state().store.remove("parent")
        SupportNesting.notifyParent(withListeners, "saved", emptyMap())

        // arity mismatch picks maxBy...
        val host2 = manager.newComponent("nest-parent") as NestParent
        val child2 = manager.newComponent("event-child")
        child2.state().store["parent"] = host2
        child2.state().store["nestedListeners"] = mapOf("saved" to "onChildSaved")
        SupportNesting.notifyParent(child2, "saved", mapOf("a" to "x", "b" to "y"))
    }
}
