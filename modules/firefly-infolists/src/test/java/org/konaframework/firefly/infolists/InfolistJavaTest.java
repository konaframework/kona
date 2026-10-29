package org.konaframework.firefly.infolists;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfolistJavaTest {
    @AfterEach
    void clear() {
        TextEntry.configureUsing(null);
        IconEntry.configureUsing(null);
        ImageEntry.configureUsing(null);
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
    }
}

class NoteEntry extends Entry<NoteEntry> {
    NoteEntry() {
        super("note");
        view("firefly.entries.note");
        meta("ff-view", "firefly.entries.note");
    }
}
