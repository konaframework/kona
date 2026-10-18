package org.konaframework.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.brew.meta.ComponentMeta;
import org.konaframework.brew.testing.Testable;
import org.konaframework.crema.CremaEngine;
import org.konaframework.crema.MapViewFinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JavaComponentTest {
    public interface Auditable {}

    public static class TodoList extends Component implements Auditable {
        public static int instances = 0;
        public String title = "";
        public List<String> todos = new ArrayList<>();
        public int limit = 3;
        public transient String scratch = "x";
        public final String kind = "todo";
        @Locked public String owner = "ana";
        private String hidden = "h";
        public List<String> log = new ArrayList<>();

        public void mount(String title, int limit) {
            this.title = title;
            this.limit = limit;
        }

        public void bootAuditable() {
            log.add("bootAuditable");
        }

        public void updatedTitle(String value) {
            log.add("updatedTitle:" + value);
        }

        public void add(String item) {
            todos.add(item);
        }

        public String described(String prefix, int count) {
            return prefix + count;
        }

        @Computed
        public int remaining() {
            return limit - todos.size();
        }

        @Renderless
        public void ping() {}

        public String getHidden() {
            return hidden;
        }

        @Override
        public String template() {
            return """
                <div>
                    <h1>{{ title }}</h1>
                    @foreach(todos as todo)<li>{{ todo }}</li>@endforeach
                    <span>{{ remaining }}</span>
                </div>
                """;
        }
    }

    public static class ViewBacked extends Component {
        public int count = 1;

        @Override
        public Object render() {
            return view("brew.view-backed", Map.of("label", "Java"));
        }
    }

    private BrewManager manager() {
        MapViewFinder views = new MapViewFinder().put("brew.view-backed", "<p>{{ label }} {{ count }}</p>");
        return new BrewManager(new BrewConfig("java-key"), new CremaEngine(views), null)
            .component("todo-list", TodoList.class)
            .component(ViewBacked.class);
    }

    @AfterEach
    void reset() {
        Brew.reset();
    }

    @Test
    void javaFieldsAreStateAndHooksWork() {
        BrewManager brew = manager();
        ComponentMeta meta = ComponentMeta.of(TodoList.class);
        assertEquals(List.of("title", "todos", "limit", "kind", "owner", "log"), new ArrayList<>(meta.getProperties().keySet()));
        assertTrue(meta.getProperties().get("kind").getLocked());
        assertTrue(meta.getProperties().get("owner").getLocked());
        assertTrue(meta.getActions().containsKey("getHidden"));

        Testable test = Testable.create(brew, "todo-list", Map.of("title", "Chores", "limit", 5));
        test.assertSet("title", "Chores").assertSet("limit", 5).assertSee("Chores").assertSee("5");
        test.call("add", "dishes").assertSet("todos", List.of("dishes")).assertSee("<li>dishes</li>", false).assertSet("remaining", 4);
        test.set("title", "Home").assertSet("title", "Home");
        assertTrue(((TodoList) test.instance()).log.containsAll(List.of("bootAuditable", "updatedTitle:Home")));
        test.call("described", "n=", "7").assertReturned("n=7");
        test.call("ping").assertRenderSkipped();
        assertThrows(CannotUpdateLockedPropertyException.class, () -> test.set("owner", "kai"));
        assertThrows(CannotUpdateLockedPropertyException.class, () -> test.set("kind", "x"));
        assertThrows(DirectlyCallingLifecycleHooksNotAllowedException.class, () -> test.call("bootAuditable"));
    }

    @Test
    void positionalMountAndJavaDefaults() {
        BrewManager brew = manager();
        Testable positional = Testable.create(brew, "todo-list", Map.of("0", "First", "1", 2));
        positional.assertSet("title", "First").assertSet("limit", 2);
        Testable missing = Testable.create(brew, "todo-list", Map.of());
        missing.assertSet("title", null).assertSet("limit", 0);
        missing.call("described", "only").assertReturned("only0");
    }

    @Test
    void facadeAndViews() {
        Brew.setManager(manager());
        String html = Brew.mount("view-backed");
        assertTrue(html.contains("<p wire:snapshot="));
        assertTrue(html.contains("Java 1"));
        Brew.test(ViewBacked.class).assertViewIs("brew.view-backed").assertViewHas("label", "Java");
    }
}
