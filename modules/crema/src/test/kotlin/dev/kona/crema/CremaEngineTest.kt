package dev.kona.crema

import dev.kona.core.Application
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CremaEngineTest {
    @TempDir
    lateinit var temp: Path

    private fun engine(vararg pairs: Pair<String, String>): CremaEngine {
        val finder = MapViewFinder()
        pairs.forEach { (k, v) -> finder.put(k, v) }
        return CremaEngine(finder)
    }

    @Test
    fun echoesConditionalsAndLoops() {
        val crema = engine(
            "hello" to "<p>{{ name }}</p><p>{!! html !!}</p>",
            "if" to "@if(ok) yes @else no @endif",
            "unless" to "@unless(ok) hidden @endunless",
            "foreach" to "@foreach(items as item)<li>{{ item }}-{{ loop.iteration }}</li>@endforeach",
            "forelse-empty" to "@forelse(items as item){{ item }}@empty empty @endforelse",
            "forelse-full" to "@forelse(items as item){{ item }}@empty empty @endforelse",
            "for" to "@for(items as item){{ item }}@endfor",
            "each" to "@each(items as item){{ item }}@endeach",
            "csrf" to "@csrf",
            "json" to "@json(\$user)",
            "json-list" to "@json(items)",
            "escape" to "@@if",
            "cmp" to "{{ a == 1 }}{{ n >= 2 }}{{ n <= 2 }}",
        )
        assertEquals("<p>Ada</p><p><b>x</b></p>", crema.render("hello", mapOf("name" to "Ada", "html" to "<b>x</b>")))
        assertEquals(" yes ", crema.render("if", mapOf("ok" to true)))
        assertEquals(" no ", crema.render("if", mapOf("ok" to false)))
        assertEquals(" hidden ", crema.render("unless", mapOf("ok" to false)))
        assertEquals("", crema.render("unless", mapOf("ok" to true)))
        assertEquals("<li>a-1</li><li>b-2</li>", crema.render("foreach", mapOf("items" to listOf("a", "b"))))
        assertEquals(" empty ", crema.render("forelse-empty", mapOf("items" to emptyList<String>())))
        assertEquals("a", crema.render("forelse-full", mapOf("items" to listOf("a"))))
        assertEquals("xy", crema.render("for", mapOf("items" to listOf("x", "y"))))
        assertEquals("ab", crema.render("each", mapOf("items" to listOf("a", "b"))))
        assertTrue(crema.render("csrf", mapOf("_token" to "t")).contains("value=\"t\""))
        assertEquals(
            """{"name":"Ada"}""",
            crema.render("json", mapOf("user" to mapOf("name" to "Ada"))),
        )
        assertEquals("""[1,2]""", crema.render("json-list", mapOf("items" to listOf(1, 2))))
        assertEquals("null", crema.render("json", emptyMap()))
        assertEquals("@if", crema.render("escape"))
        assertEquals("truetruetrue", crema.render("cmp", mapOf("a" to 1, "n" to 2)))
        assertEquals("<p></p><p></p>", crema.render("hello", emptyMap()))
    }

    @Test
    fun layoutsIncludesComponents() {
        val crema = engine(
            "layouts.app" to "<html>@yield('title', 'Default')|@yield('content')|@stack('scripts')</html>",
            "home" to """
                @extends('layouts.app')
                @section('title', 'Home')
                @section('content')
                Hello {{ name }}
                @push('scripts')<script></script>@endpush
                @endsection
            """.trimIndent(),
            "partials.nav" to "Nav",
            "with-include" to "X @include('partials.nav') Y @includeIf('missing') Z @includeWhen(show, 'partials.nav')",
            "alert" to "<div>{{ title }}:{{ slot }}</div>",
            "uses-alert" to "@component('alert') @slot('title')Hi@endslot Body @endcomponent",
            "parent-layout" to "@section('content')PARENT@endsection @yield('content')",
            "child-parent" to """
                @extends('parent-layout')
                @section('content')CHILD @parent MORE@endsection
            """.trimIndent(),
            "prepend" to "@prepend('scripts')A@endprepend @push('scripts')B@endpush @stack('scripts')",
            "comments" to "A{{-- hide --}}B",
        )
        assertEquals(
            "<html>Home|HelloAda|<script></script></html>",
            crema.render("home", mapOf("name" to "Ada")).replace(Regex("\\s+"), ""),
        )
        assertEquals(
            "X Nav Y Z Nav",
            crema.render("with-include", mapOf("show" to true)).replace(Regex("\\s+"), " ").trim(),
        )
        assertEquals(
            "X Nav Y Z",
            crema.render("with-include", mapOf("show" to false)).replace(Regex("\\s+"), " ").trim(),
        )
        assertTrue(crema.render("uses-alert").contains("Hi"))
        assertTrue(crema.render("uses-alert").contains("Body"))
        assertEquals("CHILD PARENT MORE", crema.render("child-parent").replace(Regex("\\s+"), " ").trim())
        assertEquals("AB", crema.render("prepend").trim())
        assertEquals("AB", crema.render("comments"))
    }

    @Test
    fun helpersFilesAndProvider() {
        val crema = engine("expr" to "{{ name }}")
        val view = crema.make("expr").with("name", "Ada").with(mapOf("x" to 1))
        assertEquals("Ada", view.render())
        assertEquals(200, view.toResponse().status)
        assertEquals(201, Crema.view(crema, "expr", mapOf("name" to "x"), 201).status)
        assertEquals("z", crema.renderSource("{{ v }}", mapOf("v" to "z")))
        assertThrows(ViewNotFoundException::class.java) { crema.render("nope") }

        val views = temp.resolve("resources/views/posts")
        Files.createDirectories(views)
        Files.writeString(temp.resolve("resources/views/home.crema.html"), "Home {{ name }}")
        Files.writeString(views.resolve("show.crema.kt"), "Post")
        assertEquals("Home Ada", CremaEngine(FileViewFinder(temp.resolve("resources/views"))).render("home", mapOf("name" to "Ada")))
        assertEquals("Post", CremaEngine(FileViewFinder(temp.resolve("resources/views"))).render("posts.show"))
        assertThrows(ViewNotFoundException::class.java) { FileViewFinder(temp.resolve("resources/views")).get("x") }

        val app = Application(temp)
        app.register(CremaServiceProvider())
        app.boot()
        assertEquals("Home Ada", app.resolve(CremaEngine::class.java).render("home", mapOf("name" to "Ada")))

        val other = temp.resolve("other").also { Files.createDirectories(it) }
        val custom = Application(other)
        custom.config.set("view.path", temp.resolve("resources/views").toString())
        custom.register(CremaServiceProvider())
        custom.boot()
        assertEquals("Post", custom.resolve(CremaEngine::class.java).render("posts.show"))
    }

    @Test
    fun coverageEdges() {
        val crema = engine(
            "show" to "@section('s')Hi@show @yield('s')",
            "blank" to "@section('c')@endsection @section('c')P@endsection @yield('c')",
            "yield-default" to "@yield('missing', 'fallback')",
            "truth" to "@if(0)a@endif@if('')b@endif@if(nil)c@endif@if(items)d@endif",
            "range" to "@foreach(items as item){{ item }}@endforeach",
            "scalar" to "@foreach(n as item){{ item }}@endforeach",
            "user" to "{{ user.name }}",
            "csrf-empty" to "@csrf",
            "raw-fail" to "{!! x",
            "echo-fail" to "{{ x",
            "section-fail" to "@section('x')noend",
            "g" to "ok",
            "box" to "{{ slot }}",
            "comp" to "@component('box')Z@endcomponent",
            "isset" to "{{ isset(name) }}",
            "empty" to "{{ empty(name) }}",
            "not" to "{{ not ok }}",
            "bang" to "{{ !ok }}",
            "andor" to "{{ a == 1 and b == 2 }}",
            "or" to "{{ a == 1 or b == 2 }}",
            "ne" to "{{ a != b }}",
            "gt" to "{{ 2 > 1 }}",
            "str" to "{{ 'x' }}{{ \"y\" }}",
            "num" to "{{ 3.5 }}",
        )
        data class User(val name: String)

        assertTrue(crema.render("show").contains("Hi"))
        assertEquals("P", crema.render("blank").replace(Regex("\\s+"), ""))
        assertEquals("fallback", crema.render("yield-default"))
        assertEquals("d", crema.render("truth", mapOf("items" to listOf(1))))
        assertEquals("", crema.render("truth", mapOf("items" to emptyList<Int>())))
        assertEquals("12", crema.render("range", mapOf("items" to 1..2)))
        assertEquals("9", crema.render("scalar", mapOf("n" to 9)))
        assertEquals("Ada", crema.render("user", mapOf("user" to User("Ada"))))
        assertTrue(crema.render("csrf-empty").contains("value=\"\""))
        assertThrows(IllegalArgumentException::class.java) { crema.render("raw-fail") }
        assertThrows(IllegalArgumentException::class.java) { crema.render("echo-fail") }
        assertThrows(IllegalArgumentException::class.java) { crema.render("section-fail") }
        assertEquals("ok", Crema.view(crema, "g").body)
        assertEquals("Z", crema.render("comp").trim())
        assertEquals("true", crema.render("isset", mapOf("name" to "x")))
        assertEquals("true", crema.render("empty", emptyMap()))
        assertEquals("true", crema.render("not", mapOf("ok" to false)))
        assertEquals("true", crema.render("bang", mapOf("ok" to false)))
        assertEquals("true", crema.render("andor", mapOf("a" to 1, "b" to 2)))
        assertEquals("true", crema.render("or", mapOf("a" to 1, "b" to 0)))
        assertEquals("true", crema.render("ne", mapOf("a" to 1, "b" to 2)))
        assertEquals("true", crema.render("gt"))
        assertEquals("xy", crema.render("str"))
        assertEquals("3.5", crema.render("num"))
        assertEquals("@", crema.renderSource("@@"))
        assertEquals("plain", crema.renderSource("plain"))

        val ctx = CremaContext().merge(mapOf("a" to 1))
        assertEquals(1, ctx.get("a"))
        ctx.set("b", 2)
        assertEquals(mapOf("a" to 1, "b" to 2), ctx.snapshot())
        ctx.push("s", "A")
        ctx.prepend("s", "B")
        assertEquals("BA", ctx.stack("s"))
        assertEquals("", ctx.stack("missing"))
        val section = ctx.section("s1")
        assertTrue(section.isEmpty())
        section.setParent("P")
        assertFalse(section.isEmpty())
        assertEquals("P", section.render())
        section.append("hello")
        assertEquals("hello", section.render())
        val section2 = ctx.section("s2")
        section2.append("C @parent D")
        section2.setParent("P")
        assertEquals("C P D", section2.render())
        assertFalse(CremaEngine(MapViewFinder().put("t", "{{ x }}")).runtime().render("t", emptyMap()).contains("x"))
    }
}
