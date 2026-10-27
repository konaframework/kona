package app.firefly.resources;

import app.models.Note;
import org.konaframework.firefly.Resource;
import org.konaframework.firefly.forms.DateTimePicker;
import org.konaframework.firefly.forms.Select;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.infolists.TextEntry;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.tables.Table;
import org.konaframework.firefly.tables.TextColumn;

import java.util.LinkedHashMap;
import java.util.Map;

public class NoteResource extends Resource {
    public NoteResource() {
        super("notes");
        label("Notes");
        simple();
        model(Note.class);
    }

    @Override
    public Schema form(Schema schema) {
        return schema.components(
            TextInput.make("name"),
            Select.make("status").options(Map.of("open", "Open", "done", "Done")),
            DateTimePicker.make("due")
        );
    }

    @Override
    public Schema infolist(Schema schema) {
        return schema.components(TextEntry.make("name"));
    }

    @Override
    public Table<?, ?> table(Table<?, ?> table) {
        return Table.memory(row()).columns(TextColumn.make("name"));
    }

    private static Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "1");
        row.put("name", "Ada");
        return row;
    }
}
