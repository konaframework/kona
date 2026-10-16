package io.konaframework.crema

import io.konaframework.core.Application
import io.konaframework.support.lang.Lang
import io.konaframework.support.lang.Translator
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
    fun bladePrismParityDirectives() {
        val crema = engine(
            "elseif" to "@if(n == 1)one@elseif(n == 2)two@else other@endif",
            "isset-block" to "@isset(name)Y@endisset",
            "empty-block" to "@empty(name)E@endempty",
            "while" to "@while(loop.iteration <= n){{ loop.iteration }}@endwhile",
            "for-in" to "@for(i in 0..2){{ i }}@endfor",
            "for-c" to "@for(i = 0; i < n; i++){{ i }}@endfor",
            "loop-meta" to "@foreach(items as item){{ loop.even }}-{{ loop.odd }}-{{ loop.index }};@endforeach",
            "forelse-loop" to "@forelse(items as item){{ item }}:{{ loop.count }}:{{ loop.even }}@empty none@endforelse",
            "nested-loop" to "@foreach(outer as o)@foreach(inner as i){{ loop.depth }}:{{ loop.parent.iteration }};@endforeach@endforeach",
            "auth" to "@auth A @endauth @guest G @endguest",
            "can" to "@can('edit')E@endcan @cannot('edit')N@endcannot",
            "switch" to "@switch(n)@case(1)one@break@case(2)two@break@default other@endswitch",
            "include-unless" to "@includeUnless(hide, 'partials.nav')",
            "include-first" to "@includeFirst(['missing.a', 'partials.nav'])",
            "partials.nav" to "Nav",
            "once" to "@once X @endonce @once X @endonce",
            "method" to "@method('PUT')",
            "asset" to "@asset('css/app.css')",
            "lang" to "@lang('hi')|@lang('greet', replace)",
            "vite" to "@vite('resources/js/app.js')",
            "vite-css" to "@vite('resources/css/app.css')",
            "vite-list" to "@vite(['resources/js/a.js', 'resources/css/b.css'])",
            "has-section" to "@section('title')T@endsection @hasSection('title')Y@endif @sectionMissing('x')M@endif",
            "error" to "@error('email') bad @enderror",
            "verbatim" to "@verbatim{{ not }}@if(x)@endverbatim",
            "components.alert" to "[{{ slot }}]",
            "x-comp" to "<x-alert>Hi</x-alert><x-alert />",
            "components.card" to "<h>{{ title }}</h><b>{{ slot }}</b><f>{{ footer }}</f>",
            "x-slots" to """
                <x-card>
                  <x-slot name="title">Notice</x-slot>
                  Body
                  <x-slot:footer>Bye</x-slot:footer>
                </x-card>
            """.trimIndent(),
        )

        assertEquals("one", crema.render("elseif", mapOf("n" to 1)).trim())
        assertEquals("two", crema.render("elseif", mapOf("n" to 2)).trim())
        assertEquals(" other", crema.render("elseif", mapOf("n" to 3)))
        assertEquals("Y", crema.render("isset-block", mapOf("name" to "Ada")))
        assertEquals("", crema.render("isset-block", emptyMap()))
        assertEquals("E", crema.render("empty-block", emptyMap()))
        assertEquals("", crema.render("empty-block", mapOf("name" to "x")))
        assertEquals("123", crema.render("while", mapOf("n" to 3)))
        assertEquals("012", crema.render("for-in"))
        assertEquals("012", crema.render("for-c", mapOf("n" to 3)))
        assertEquals("true-false-0;false-true-1;", crema.render("loop-meta", mapOf("items" to listOf("a", "b"))))
        assertEquals("a:2:true", crema.render("forelse-loop", mapOf("items" to listOf("a", "b"))).take(8))
        assertTrue(crema.render("forelse-loop", mapOf("items" to listOf("a", "b"))).contains("false"))
        assertEquals("none", crema.render("forelse-loop", mapOf("items" to emptyList<String>())).trim())
        assertEquals(
            "2:1;2:1;",
            crema.render("nested-loop", mapOf("outer" to listOf(1), "inner" to listOf("a", "b"))),
        )
        assertTrue(crema.render("auth", mapOf("user" to "u")).contains("A"))
        assertFalse(crema.render("auth", mapOf("user" to "u")).contains("G"))
        assertTrue(crema.render("auth", emptyMap()).contains("G"))
        assertTrue(crema.render("auth", mapOf("auth_user" to "u")).contains("A"))
        assertTrue(crema.render("auth", mapOf("__authenticated" to true)).contains("A"))
        assertTrue(crema.render("can", mapOf("__abilities" to setOf("edit"))).contains("E"))
        assertFalse(crema.render("can", mapOf("__abilities" to setOf("edit"))).contains("N"))
        assertTrue(crema.render("can", mapOf("can" to mapOf("edit" to false))).contains("N"))
        assertTrue(crema.render("can", mapOf("__abilities" to listOf("edit"))).contains("E"))
        assertEquals("one", crema.render("switch", mapOf("n" to 1)).trim())
        assertEquals("two", crema.render("switch", mapOf("n" to 2)).trim())
        assertEquals(" other", crema.render("switch", mapOf("n" to 9)))
        assertEquals("Nav", crema.render("include-unless", mapOf("hide" to false)).trim())
        assertEquals("", crema.render("include-unless", mapOf("hide" to true)).trim())
        assertEquals("Nav", crema.render("include-first").trim())
        assertEquals("X", crema.render("once").replace(" ", ""))
        assertTrue(crema.render("method").contains("name=\"_method\""))
        assertTrue(crema.render("method").contains("value=\"PUT\""))
        assertEquals("/css/app.css", crema.render("asset"))
        assertEquals(
            "https://cdn.example/css/app.css",
            crema.render("asset", mapOf("asset_url" to "https://cdn.example")),
        )

        val t = Translator()
        t.addLines("en", mapOf("hi" to "Hello", "greet" to "Hi :name"))
        Lang.setTranslator(t)
        assertEquals(
            "Hello|Hi Ada",
            crema.render("lang", mapOf("replace" to mapOf("name" to "Ada"))),
        )
        assertEquals(
            "FromCtx",
            crema.renderSource("@lang('k')", mapOf("lang" to mapOf("k" to "FromCtx"))),
        )

        assertTrue(crema.render("vite").contains("/build/resources/js/app.js"))
        assertTrue(crema.render("vite").contains("<script"))
        assertTrue(crema.render("vite-css").contains("<link"))
        assertTrue(crema.render("vite-list").contains("a.js"))
        assertTrue(crema.render("vite-list").contains("b.css"))
        assertTrue(crema.render("has-section").contains("Y"))
        assertTrue(crema.render("has-section").contains("M"))
        assertEquals(" bad ", crema.render("error", mapOf("errors" to mapOf("email" to "required"))))
        assertEquals("", crema.render("error", mapOf("errors" to emptyMap<String, String>())))
        assertEquals("{{ not }}@if(x)", crema.render("verbatim"))
        assertEquals("[Hi][]", crema.render("x-comp").replace(Regex("\\s+"), ""))
        assertEquals(
            "<h>Notice</h><b>Body</b><f>Bye</f>",
            crema.render("x-slots").replace(Regex("\\s+"), ""),
        )
        assertEquals(
            """<script type="module" src="/build/resources/js/app.js"></script>""",
            ViteTags.tag("resources/js/app.js"),
        )
        assertEquals(
            """<script type="module" src="/build/assets/app.js"></script>""",
            ViteTags.tag("/build/assets/app.js"),
        )
        assertEquals(
            """<link rel="stylesheet" href="/build/assets/app.css">""",
            ViteTags.tag("build/assets/app.css"),
        )
        assertEquals(ViteTags.tags(listOf("a.js", "b.css")), ViteTags.tags("a.js", "b.css"))
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

        val jvmRoot = temp.resolve("jvm-app")
        val jvmViews = jvmRoot.resolve("src/main/resources/views")
        Files.createDirectories(jvmViews)
        Files.writeString(jvmViews.resolve("hi.crema.html"), "Hi {{ name }}")
        assertEquals(jvmViews, CremaServiceProvider.resolveDefaultViews(jvmRoot))
        val jvmApp = Application(jvmRoot)
        jvmApp.register(CremaServiceProvider())
        jvmApp.boot()
        assertEquals("Hi Ada", jvmApp.resolve(CremaEngine::class.java).render("hi", mapOf("name" to "Ada")))

        val buildOnly = temp.resolve("build-only")
        val buildViews = buildOnly.resolve("build/resources/main/views")
        Files.createDirectories(buildViews)
        Files.writeString(buildViews.resolve("built.crema.html"), "Built")
        assertEquals(buildViews, CremaServiceProvider.resolveDefaultViews(buildOnly))

        val empty = temp.resolve("empty-app").also { Files.createDirectories(it) }
        assertEquals(empty.resolve("src/main/resources/views"), CremaServiceProvider.resolveDefaultViews(empty))
        assertEquals(
            empty.resolve("src/main/resources/views"),
            CremaServiceProvider.resolveDefaultViews(Application(empty)),
        )

        val legacyOnly = temp.resolve("legacy-views")
        val legacyViews = legacyOnly.resolve("resources/views")
        Files.createDirectories(legacyViews)
        assertEquals(legacyViews, CremaServiceProvider.resolveDefaultViews(legacyOnly))
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
