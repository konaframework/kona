package org.konaframework.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.core.Application;
import org.konaframework.support.collection.Collection;
import org.konaframework.support.date.KonaDate;
import org.konaframework.support.helpers.Arr;
import org.konaframework.support.lang.Lang;
import org.konaframework.support.lang.Translator;
import org.konaframework.support.console.ConsoleIO;
import org.konaframework.support.console.ConsolePrompts;
import org.konaframework.support.console.Tty;
import org.konaframework.support.str.Str;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaSupportApiTest {
    @Test
    void collectionStrDateArrLangFromJava(@TempDir Path base) {
        Collection<Integer> nums = Collection.make(3, 1, 2);
        assertEquals(3, nums.count());
        assertEquals(List.of(1, 2, 3), nums.sort().toList());
        assertEquals(Collection.of(List.of(1)), Collection.empty().push(1));

        assertEquals("helloWorld", Str.camel("hello_world"));
        assertEquals("hello-world", Str.slug("Hello World"));
        assertEquals("hello_world", Str.slug("Hello World", "_"));
        assertEquals("HELLO", Str.of("hello").upper().toString());

        KonaDate date = KonaDate.ofInstant(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        assertEquals("2024-01-01", date.toDateString());
        assertTrue(date.addDays(1).isAfter(date));

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        Arr.set(map, "user.name", "Ada");
        assertEquals("Ada", Arr.get(map, "user.name"));
        assertTrue(Arr.has(map, "user.name"));
        assertEquals(Map.of("user.name", "Ada"), Arr.only(Map.of("user.name", "Ada", "x", 1), List.of("user.name")));

        Application app = new Application(base);
        app.register(new SupportServiceProvider());
        Translator translator = app.resolve(Translator.class);
        translator.addLines("en", Map.of("greet", "Hi :name"));
        assertEquals("Hi Ada", Lang.get("greet", Map.of("name", "Ada")));
        Lang.setLocale("en");
        assertEquals("en", Lang.getLocale());
        assertFalse(Str.isBlank("x"));

        Translator oneArg = new Translator("en");
        oneArg.addLines("en", Map.of("hi", "Hello :name"));
        assertEquals("Hello Ada", oneArg.get("hi", Map.of("name", "Ada")));
        LinkedHashMap<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("name", null);
        assertEquals("Hello ", oneArg.get("hi", withNull));
        assertEquals("Hello Ada", oneArg.trans("hi", Map.of("name", "Ada")));
        KonaDate.now();
        KonaDate.now(ZoneOffset.UTC);
        KonaDate.today();
        KonaDate.today(ZoneOffset.UTC);
        KonaDate.setDefaultTimezone(ZoneOffset.UTC);
        assertEquals(ZoneOffset.UTC, KonaDate.defaultTimezone());
        KonaDate.resetDefaultTimezone();
        Lang.setFallback("en");
        assertEquals("en", Lang.getFallback());

        Collection<Integer> mutable = Collection.make(1);
        mutable.put(0, 7);
        mutable.put(99, 8);
        assertEquals(7, mutable.first());
        assertEquals(8, mutable.last());
        assertEquals("", Str.studly("___"));
        assertEquals("", Str.camel(""));
        assertEquals("Hello World", Str.title("hello   world"));
        Translator twoArg = new Translator("fr", "en");
        assertEquals("fr", twoArg.getLocale());

        ByteArrayOutputStream consoleOut = new ByteArrayOutputStream();
        ConsoleIO console = new ConsoleIO(new PrintStream(consoleOut), new PrintStream(consoleOut), false);
        console.taskLine("create_users_table", "DONE", 12L, true);
        console.taskLine("seeders", "DONE", 3L);
        console.taskLine("nested", "DONE");
        console.intro("kona");
        console.outro("done");
        console.note("hello");
        console.note("warn", "WARN");
        console.promptFrame("Name", List.of(""));
        console.promptFrame("Name", List.of("Ada"), 0);
        console.promptFrame("Name", List.of("Ada"), 0, ConsoleIO.CYAN);
        console.promptFrame("Name", List.of("Ada"), 0, ConsoleIO.CYAN, ConsoleIO.PROMPT_WIDTH);
        console.promptFrame("Name", List.of("Ada"), -1, ConsoleIO.CYAN, ConsoleIO.PROMPT_WIDTH, false);
        console.promptFrameLines("Name", List.of("Ada"));
        console.promptFrameLines("Name", List.of("Ada"), 0);
        console.promptFrameLines("Name", List.of("Ada"), 0, ConsoleIO.CYAN);
        console.promptFrameLines("Name", List.of("Ada"), 0, ConsoleIO.CYAN, ConsoleIO.PROMPT_WIDTH);
        console.promptFrameLines("Name", List.of("Ada"), -1, ConsoleIO.CYAN, ConsoleIO.PROMPT_WIDTH, false);
        console.promptSubmitted("Name", "Ada");
        assertTrue(consoleOut.toString().contains("create_users_table"));
        assertTrue(consoleOut.toString().contains("kona"));
        assertTrue(consoleOut.toString().contains("Ada"));
        assertEquals("12ms", ConsoleIO.formatDuration(12L));
        assertEquals("1.23s", ConsoleIO.formatDuration(1234L));
        assertEquals(72, ConsoleIO.TASK_WIDTH);
        assertEquals(60, ConsoleIO.PROMPT_WIDTH);

        java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream("Ada\n\ny\n1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.io.ByteArrayOutputStream promptOut = new java.io.ByteArrayOutputStream();
        java.io.PrintStream stream = new java.io.PrintStream(promptOut);
        ConsolePrompts prompts = new ConsolePrompts(in, stream, false, false);
        assertEquals("Ada", prompts.ask("Name"));
        assertEquals("Kona", prompts.ask("Name", "Kona"));
        assertTrue(prompts.confirm("Ok?", false));
        assertEquals("array", prompts.choice("Driver", List.of("array", "redis"), 0));
        new ConsolePrompts();
        new ConsolePrompts(in);
        new ConsolePrompts(in, stream);
        new ConsolePrompts(in, stream, false);
        ConsolePrompts.forTest(in, stream);
        ConsolePrompts.forTest(in, stream, false);
        ConsolePrompts.forTest(in, stream, false, false);
        ConsolePrompts.forTest(in, stream, false, false, false);
        assertFalse(Tty.shouldUseWidgets(in));
        assertEquals(60, Tty.promptContentWidth(0));
        assertEquals(76, Tty.promptContentWidth(80));
    }
}
