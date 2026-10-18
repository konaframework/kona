package app.brew;

import org.konaframework.brew.Component;
import org.konaframework.brew.Computed;
import java.util.ArrayList;
import java.util.List;

/** A class-based component; its markup lives in {@code views/brew/todos.crema.html}. */
public class Todos extends Component {
    public static class Todo {
        public String title = "";
        public boolean done = false;

        public Todo() {}

        public Todo(String title, boolean done) {
            this.title = title;
            this.done = done;
        }
    }

    public String draft = "";
    public List<Todo> items = new ArrayList<>(List.of(new Todo("Grind the beans", false), new Todo("Steam the milk", true)));

    public void add() {
        String title = draft.trim();
        if (title.isEmpty()) {
            return;
        }
        items.add(new Todo(title, false));
        draft = "";
    }

    public void remove(int index) {
        items.remove(index);
    }

    @Computed
    public long remaining() {
        return items.stream().filter(todo -> !todo.done).count();
    }
}
