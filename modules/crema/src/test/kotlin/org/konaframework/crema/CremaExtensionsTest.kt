package org.konaframework.crema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CremaExtensionsTest {
    private val views = mutableMapOf<String, String>()
    private val crema = CremaEngine { name -> views[name] ?: throw ViewNotFoundException(name) }

    class Bean {
        val title: String get() = "T"
        val isOpen: Boolean get() = true
        fun shout(): String = "HEY"

        @JvmField
        val count: Int = 3
    }

    @Test
    fun ifInsideForeachSeesLoopVariable() {
        val out = crema.renderSource(
            "@foreach(\$items as \$item)@if(\$item.done)[{{ \$item.name }}]@else({{ \$item.name }})@endif@endforeach",
            mapOf("items" to listOf(mapOf("name" to "a", "done" to true), mapOf("name" to "b", "done" to false))),
        )
        assertEquals("[a](b)", out)
    }

    @Test
    fun nestedStructuresAndFalseBranchesDoNotLeak() {
        views["inc"] = "INC"
        val src = "@if(false)@include('missing')@push('s')X@endpush@endif" +
            "@unless(\$on)U@endunless@isset(\$v)I@endisset@empty(\$e)E@endempty" +
            "@auth A@endauth@guest G@endguest@can('edit')C@endcan@cannot('edit')N@endcannot" +
            "@error('name')ERR@enderror@switch(\$k)@case(1)one@break@default def@endswitch" +
            "@for(\$i in 1..2){{ \$i }}@endfor@each(\$xs as \$x){{ \$x }}@endeach" +
            "@forelse(\$none as \$n)n@empty none@endforelse@stack('s')"
        val out = crema.renderSource(
            src,
            mapOf("on" to false, "v" to 1, "e" to "", "k" to 1, "xs" to listOf("p"), "none" to emptyList<Any>()),
        )
        assertEquals("UIE GNone12p none", out)
    }

    @Test
    fun outputOfBlocksIsNotReinterpreted() {
        val out = crema.renderSource("@if(true){!! \$raw !!}@endif", mapOf("raw" to "{{ \$secret }}", "secret" to "S"))
        assertEquals("{{ \$secret }}", out)
    }

    @Test
    fun keyedLoops() {
        val out = crema.renderSource(
            "@foreach(\$m as \$k => \$v){{ \$k }}={{ \$v }};@endforeach@foreach(\$l as \$i => \$x){{ \$i }}{{ \$x }}@endforeach" +
                "@foreach(\$l as \$i => \$x)@foreach(\$l as \$j => \$y){{ \$loop.depth }}@endforeach@endforeach",
            mapOf("m" to linkedMapOf("a" to 1), "l" to listOf("p")),
        )
        assertEquals("a=1;0p2", out)
    }

    @Test
    fun morphMarkersWrapBlocksButNotInsideTags() {
        val ctx = CremaContext().set(CremaContext.MORPH_MARKERS, true).set("on", true)
        val out = crema.renderSource("<div @if(\$on) class=\"a\" @endif>@if(\$on)Y@endif</div>", ctx)
        assertEquals(
            "<div  class=\"a\" >${CremaContext.BLOCK_START}Y${CremaContext.BLOCK_END}</div>",
            out,
        )
        assertEquals("Y", crema.renderSource("@if(true)Y@endif"))
    }

    @Test
    fun inlineDirectivesWithAndWithoutArguments() {
        crema.directive("hello") { call -> "hi(${call.arguments().joinToString("|")})" }
        crema.directive("plain") { call -> "plain[${call.expression}]${call.name}" }
        val out = crema.renderSource(
            "@hello('a, b', \$n) @plain @plain () @@hello @unknown x@y.com",
            mapOf("n" to 2),
        )
        assertEquals("hi(a, b|2) plain[]plain plain[]plain @hello @unknown x@y.com", out)
        assertTrue(crema.extensions.hasDirective("hello"))
        assertFalse(crema.extensions.hasDirective("nope"))
    }

    @Test
    fun directiveOutputIsNotReinterpreted() {
        crema.directive("raw") { "{{ \$x }}" }
        assertEquals("{{ \$x }}", crema.renderSource("@raw", mapOf("x" to "X")))
    }

    @Test
    fun blockDirectivesReceiveRawBody() {
        crema.blockDirective("twice") { call -> call.expand() + call.expand(call.body, call.context.child(mapOf("v" to "B"))) }
        crema.blockDirective("named") { call -> "<${call.evaluate(call.expression)}>" + call.expand("{{ \$v }}") }
        val out = crema.renderSource(
            "@twice {{ \$v }}@endtwice|@named('n')ignored@endnamed|@foreach(\$xs as \$v)@twice({{ \$v }})-@endtwice@endforeach",
            mapOf("v" to "A", "xs" to listOf("1")),
        )
        assertEquals(" A B|<n>A|--", out)
        assertTrue(crema.extensions.hasBlockDirective("twice"))
        assertFalse(crema.extensions.hasBlockDirective("x"))
    }

    @Test
    fun blockMarkersSkipCustomBlocks() {
        crema.blockDirective("island") { call -> "[" + call.expand() + "]" }
        val ctx = CremaContext().set(CremaContext.MORPH_MARKERS, true)
        assertEquals("[ x]", crema.renderSource("@island x@endisland", ctx))
    }

    @Test
    fun tagsParseAttributesSlotsAndNesting() {
        crema.tag("ui") { tag ->
            val attrs = tag.attributes.entries.joinToString(",") { "${it.key}=${it.value}" }
            "<${tag.name} $attrs self=${tag.selfClosing} slots=${tag.slots} " +
                "eval=${tag.evaluate("\$n")} exp=${tag.expand("{{ \$n }}")}>${tag.slot}</${tag.name}>"
        }
        val out = crema.renderSource(
            "<ui:card :count=\"\$n\" :\$n title=\"Hi {{ \$n }}\" lazy ::alpine=\"open\" single='q' :flag>" +
                "<ui:slot name=\"footer\">F{{ \$n }}</ui:slot><x-slot name=\"head\">H</x-slot><x-slot:side>S</x-slot:side>" +
                "<ui:card>inner</ui:card> body</ui:card>",
            mapOf("n" to 7),
        )
        assertTrue(out.startsWith("<card count=7,n=7,title=Hi 7,lazy=true,:alpine=open,single=q,flag=true self=false"), out)
        assertTrue(out.contains("slots={footer=F7, head=H, side=S}"), out)
        assertTrue(out.contains("<card  self=false slots={} eval=7 exp=7>inner</card> body</card>"), out)
        assertEquals("<chip  self=true slots={} eval=null exp=>", crema.renderSource("<ui:chip/>").substringBefore("</chip>"))
        assertTrue(crema.extensions.hasTag("ui"))
        assertFalse(crema.extensions.hasTag("x"))
    }

    @Test
    fun tagsInsideLoopsUseLoopContextAndOutputIsParked() {
        crema.tag("t") { tag -> "{{ ${tag.attributes["v"]} }}" }
        val out = crema.renderSource("@foreach(\$xs as \$x)<t:i :v=\"\$x\" />@endforeach", mapOf("xs" to listOf(1, 2)))
        assertEquals("{{ 1 }}{{ 2 }}", out)
    }

    @Test
    fun tagEdgeCases() {
        crema.tag("t") { "T" }
        assertEquals("<t:slot name=\"x\">a</t:slot>", crema.renderSource("<t:slot name=\"x\">a</t:slot>"))
        assertEquals("<t: x", crema.renderSource("<t: x"))
        assertEquals("<t:open", crema.renderSource("<t:open"))
        assertEquals("T", crema.renderSource("<t:x-y><t:x>a</t:x></t:x-y>"))
        assertEquals("T", crema.renderSource("<t:a attr=\">\"><t:a/><t:ab>q</t:ab></t:a>"))
        assertThrows(IllegalArgumentException::class.java) { crema.renderSource("<t:a>never closed") }
    }

    @Test
    fun lazyValuesMembersAndFields() {
        var calls = 0
        val lazy = CremaLazy {
            calls++
            mapOf("inner" to CremaLazy { "deep" })
        }
        val out = crema.renderSource(
            "{{ \$lazy.inner }} {{ \$b.title }} {{ \$b.open }} {{ \$b.shout }} {{ \$b.count }} [{{ \$b.missing }}]",
            mapOf("lazy" to lazy, "b" to Bean()),
        )
        assertEquals("deep T true HEY 3 []", out)
        assertEquals(1, calls)
        assertEquals("", crema.renderSource("{{ \$nothing.x }}"))
    }

    @Test
    fun engineRenderOverloadsAndExists() {
        views["v"] = "@if(\$a)A@endif"
        val ctx = CremaContext().set("a", true)
        assertEquals("A", crema.render("v", ctx))
        assertTrue(crema.exists("v"))
        assertFalse(crema.exists("nope"))
    }

    @Test
    fun registryRejectsInvalidNames() {
        assertThrows(IllegalArgumentException::class.java) { CremaExtensions().directive("1bad") { "" } }
        assertThrows(IllegalArgumentException::class.java) { CremaExtensions().tag("a-b") { "" } }
        assertThrows(IllegalArgumentException::class.java) { CremaExtensions().blockDirective("") { "" } }
    }

    @Test
    fun argumentSplitting() {
        assertEquals(emptyList<String>(), CremaArguments.split(""))
        assertEquals(listOf("'a,b'", "\"c,d\"", "f(1, 2)", "[3, 4]"), CremaArguments.split("'a,b', \"c,d\", f(1, 2), [3, 4]"))
        assertEquals(listOf("x"), CremaArguments.split("x,"))
        assertEquals(listOf("{a, b}", "c"), CremaArguments.split("{a, b}, c"))
        assertEquals(listOf("x"), CremaArguments.split(", x"))
    }

    @Test
    fun structuralErrors() {
        assertThrows(IllegalArgumentException::class.java) { crema.renderSource("@if(true) never") }
        assertThrows(IllegalStateException::class.java) { crema.renderSource("@if(true") }
        assertThrows(IllegalStateException::class.java) { crema.renderSource("@foreach(nope)x@endforeach") }
        assertThrows(IllegalArgumentException::class.java) { crema.renderSource("@forelse(\$a as \$b)x@endforelse") }
        assertThrows(IllegalStateException::class.java) { crema.renderSource("@forelse(bad)x@empty y@endforelse") }
        crema.directive("d") { "" }
        assertThrows(IllegalStateException::class.java) { crema.renderSource("@d(oops") }
        assertEquals("@if no parens", crema.renderSource("@if no parens"))
        assertEquals("@", crema.renderSource("@"))
    }
}
