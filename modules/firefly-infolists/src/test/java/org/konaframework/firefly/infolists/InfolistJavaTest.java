package org.konaframework.firefly.infolists;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.konaframework.core.Config;
import org.konaframework.filesystem.FilesystemManager;
import org.konaframework.filesystem.LocalAdapter;
import org.konaframework.firefly.support.FireflyException;

import java.nio.file.Files;
import org.konaframework.firefly.actions.Action;
import org.konaframework.firefly.schemas.Grid;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.schemas.Section;
import org.konaframework.firefly.schemas.Tab;
import org.konaframework.firefly.schemas.Tabs;
import org.konaframework.firefly.schemas.Text;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfolistJavaTest {
    @AfterEach
    void clear() {
        TextEntry.configureUsing(null);
        IconEntry.configureUsing(null);
        ImageEntry.configureUsing(null);
        ImageEntry.clearDisks();
        ColorEntry.configureUsing(null);
        KeyValueEntry.configureUsing(null);
        CodeEntry.configureUsing(null);
        RepeatableEntry.configureUsing(null);
    }

    @Test
    void javaBuildsAnInfolist() {
        TextEntry.configureUsing(entry -> entry.badge(true));
        TextEntry title = TextEntry.make("title");
        title.label("Title");
        title.color(get -> "primary");
        title.placeholder("Empty");
        title.icon("user");
        title.date();
        title.state(LocalDate.of(2024, 6, 15));
        IconEntry published = IconEntry.make("published");
        published.bool(true);
        published.whenTrue("user", "success");
        published.whenFalse("x", "danger");
        published.state(true);
        ImageEntry photo = ImageEntry.make("photo");
        photo.imageHeight(40);
        photo.circular(true);
        photo.state("https://example.test/a.png");
        ColorEntry ink = ColorEntry.make("ink");
        ink.copyable(true);
        ink.state("#DF8723");
        KeyValueEntry meta = KeyValueEntry.make("meta");
        meta.keyLabel("Name");
        meta.valueLabel("Detail");
        meta.state(Map.of("lane", "latte"));
        CodeEntry snippet = CodeEntry.make("snippet");
        snippet.language("kotlin");
        snippet.lightTheme("github-light");
        snippet.darkTheme("github-dark");
        snippet.state("val a = 1");
        RepeatableEntry comments = RepeatableEntry.make("comments");
        comments.schema(TextEntry.make("body"));
        comments.grid(2);
        comments.contained(true);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("body", "Hello");
        comments.state(List.of(row));
        Infolist infolist = Infolist.make()
            .statePath("user")
            .operation("view")
            .record("ada")
            .components(
                Section.make("Profile").schema(
                    Grid.make(2).schema(title, published),
                    Tabs.make().tabs(Tab.make("More").schema(photo, ink, meta, snippet, comments))
                )
            );
        String html = infolist.render();
        assertTrue(html.contains("ff-section"));
        assertTrue(html.contains("ff-grid"));
        assertTrue(html.contains("ff-tab"));
        assertTrue(html.contains("June 15, 2024"));
        assertTrue(html.contains("data-ff-shiki=\"shiki\""));
        assertTrue(html.contains("ffEntry"));
        assertTrue(html.contains("latte"));
        assertTrue(html.contains("Hello"));
        assertEquals("view", infolist.operation());
        assertEquals("ada", infolist.record());
        infolist.fill(Map.of("user.title", "Ada"));
        infolist.hydrate();
        assertEquals("Ada", infolist.get("user.title"));
        assertTrue(infolist.schema().render().contains("ff-schema"));
        NoteEntry note = new NoteEntry();
        String custom = Schema.make().components(note).render();
        assertTrue(custom.contains("data-ff-view=\"firefly.entries.note\""));
        assertTrue(ShikiCode.source().contains("createHighlighter"));
        assertTrue(ShikiCode.manifest().contains("codemirror"));
        assertEquals("A", TableColumn.make("A").label());
        assertTrue(Text.make("Hello").render(Schema.make()).contains("Hello"));
        TextEntry cash = TextEntry.make("price").money().state(12);
        assertTrue(Schema.make().components(cash).render().contains("$12.00"));
        assertTrue(Schema.make().components(TextEntry.make("price").money("EUR").state(5)).render().contains("€5.00"));
        assertTrue(Schema.make().components(TextEntry.make("price").currency().state(3)).render().contains("$3.00"));
        assertTrue(Schema.make().components(TextEntry.make("price").currency("KES", "en-KE").state(100)).render().contains("ff-text-money"));
        TextEntry titled = TextEntry.make("title").state("Ada");
        titled.prefixAction(Action.make("copy").label("Copy"));
        titled.suffixAction(Action.make("open").label("Open"));
        String actions = Schema.make().components(titled).render();
        assertTrue(actions.contains("data-ff-action=\"copy\""));
        assertTrue(actions.contains("data-ff-action=\"open\""));
    }

    @Test
    void javaAggregatesRelatedRows() {
        Map<String, Object> low = new LinkedHashMap<>();
        low.put("votes", 3);
        Map<String, Object> high = new LinkedHashMap<>();
        high.put("votes", 7);
        Map<String, Object> author = new LinkedHashMap<>();
        author.put("books", List.of(low, high));
        String html = Infolist.make().record(author).components(
            TextEntry.make("books_count").counts("books"),
            TextEntry.make("books_avg_votes").avg("books", "votes").numeric(0),
            TextEntry.make("books_min_votes").min("books", "votes"),
            TextEntry.make("books_max_votes").max("books", "votes"),
            TextEntry.make("books_sum_votes").sum("books", "votes").numeric(0)
        ).render();
        assertTrue(html.contains("ff-value\">2<"));
        assertTrue(html.contains("ff-value\">5<"));
        assertTrue(html.contains("ff-value\">3<"));
        assertTrue(html.contains("ff-value\">7<"));
        assertTrue(html.contains("ff-value\">10<"));
        assertThrows(FireflyException.class, () -> TextEntry.make("books_count").counts(" "));
    }

    @Test
    void javaSignsAPrivateImageUrl() throws Exception {
        var root = Files.createTempDirectory("ff-img");
        var disk = new LocalAdapter(root, "/media");
        disk.buildTemporaryUrlsUsing((url, exp) -> url + "?expires=" + exp);
        disk.put("ada.png", "png");
        var manager = new FilesystemManager(new Config(), root);
        manager.set("avatars", disk);
        ImageEntry.useDisks(name -> disk);
        ImageEntry.useDisks(manager);
        try {
            String html = Infolist.make().components(
                ImageEntry.make("photo").disk("avatars").visibility("private").state("ada.png"),
                ImageEntry.make("missing").disk("avatars").defaultImageUrl("/fallback.png").state("gone.png")
            ).render();
            assertTrue(html.contains("/media/ada.png?expires=300"));
            assertTrue(html.contains("/fallback.png"));
            assertThrows(FireflyException.class, () -> ImageEntry.make("photo").temporaryUrlExpiration(0));
        } finally {
            ImageEntry.clearDisks();
        }
    }
}

class NoteEntry extends Entry<NoteEntry> {
    NoteEntry() {
        super("note");
        view("firefly.entries.note");
        meta("ff-view", "firefly.entries.note");
    }
}
