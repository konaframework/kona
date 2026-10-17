package org.konaframework.crema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.http.Response;
import org.konaframework.support.lang.Lang;
import org.konaframework.support.lang.Translator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class JavaCremaApiTest {
    @Test
    void renderAndRespondFromJava() {
        MapViewFinder finder = new MapViewFinder();
        finder.put("greeting", "<h1>{{ name }}</h1>");
        finder.put("payload", "@json($user)");
        CremaEngine engine = new CremaEngine(finder);
        assertEquals("<h1>Ada</h1>", engine.render("greeting", Map.of("name", "Ada")));
        View view = engine.make("greeting").with("name", "Grace");
        assertEquals("<h1>Grace</h1>", view.render());
        Response response = Crema.view(engine, "greeting", Map.of("name", "Lin"), 200);
        assertTrue(response.getBody().contains("Lin"));
        assertTrue(response.getHeaders().get("Content-Type").contains("html"));
        assertEquals("plain", engine.renderSource("plain"));
        assertEquals("<h1></h1>", Crema.view(engine, "greeting").getBody());
        assertEquals("{\"name\":\"Ada\"}", engine.render("payload", Map.of("user", Map.of("name", "Ada"))));
    }

    @Test
    void parityDirectivesFromJava() {
        MapViewFinder finder = new MapViewFinder();
        finder.put("elseif", "@if(n == 1)one@elseif(n == 2)two@else other@endif");
        finder.put("auth", "@auth A@endauth@guest G@endguest");
        finder.put("can", "@can('edit')E@endcan@cannot('edit')N@endcannot");
        finder.put("switch", "@switch(color)@case('red')R@break@default D@endswitch");
        finder.put("method", "@method('DELETE')");
        finder.put("lang", "@lang('hi')");
        finder.put("vite", "@vite('app.js')");
        finder.put("error", "@error('name')err@enderror");
        finder.put("forIn", "@for(i in 1..3){{ i }}@endfor");
        finder.put("loop", "@foreach(items as item){{ loop.even }}@endforeach");
        finder.put("partial", "P");
        finder.put("inc", "@includeUnless(skip, 'partial')|@includeFirst(['nope', 'partial'])");
        finder.put("once", "@onceZ@endonce@onceZ@endonce");
        finder.put("verbatim", "@verbatim{{ x }}@endverbatim");
        finder.put("components.box", "<b>{{ slot }}</b>");
        finder.put("x", "<x-box>ok</x-box>");
        CremaEngine engine = new CremaEngine(finder);

        assertEquals("two", engine.render("elseif", Map.of("n", 2)).trim());
        assertTrue(engine.render("auth", Map.of("user", "u")).contains("A"));
        assertTrue(engine.render("auth", Map.of()).contains("G"));
        assertTrue(engine.render("can", Map.of("__abilities", Set.of("edit"))).contains("E"));
        assertFalse(engine.render("can", Map.of("__abilities", Set.of("edit"))).contains("N"));
        assertEquals("R", engine.render("switch", Map.of("color", "red")).trim());
        assertEquals(" D", engine.render("switch", Map.of("color", "blue")));
        assertTrue(engine.render("method").contains("_method"));
        assertTrue(engine.render("method").contains("DELETE"));

        Translator translator = new Translator();
        translator.addLines("en", Map.of("hi", "Hello"));
        Lang.setTranslator(translator);
        assertEquals("Hello", engine.render("lang"));

        assertTrue(engine.render("vite").contains("/build/app.js"));
        assertEquals("err", engine.render("error", Map.of("errors", Map.of("name", "x"))).trim());
        assertEquals("123", engine.render("forIn"));
        assertEquals("truefalse", engine.render("loop", Map.of("items", List.of("a", "b"))));
        assertEquals("P|P", engine.render("inc", Map.of("skip", false)).replace(" ", ""));
        assertEquals("Z", engine.render("once").trim());
        assertEquals("{{ x }}", engine.render("verbatim"));
        assertEquals("<b>ok</b>", engine.render("x").trim());
        assertEquals(
                "<script type=\"module\" src=\"/build/app.js\"></script>",
                ViteTags.tag("app.js"));
    }
}
