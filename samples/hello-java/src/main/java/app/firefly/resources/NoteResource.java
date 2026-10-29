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

import java.util.Map;

public class NoteResource extends Resource {
    public NoteResource() {
        super("notes");
        label("Note");
        simple();
        model(Note.class);
        formColumns(2);
        infolistColumns(2);
    }

    @Override
    public Schema form(Schema schema) {
        return schema.components(
            TextInput.make("name").label("Name").required(),
            Select.make("status").label("Status").options(Map.of("open", "Open", "done", "Done")),
            DateTimePicker.make("due").label("Due")
        );
    }

    @Override
    public Schema infolist(Schema schema) {
        return schema.components(
            TextEntry.make("name").label("Name"),
            TextEntry.make("status").label("Status"),
            TextEntry.make("due").label("Due")
        );
    }

    @Override
    public Table<?, ?> table(Table<?, ?> table) {
        return table.columns(
            TextColumn.make("name").label("Name").searchable().sortable(),
            TextColumn.make("status").label("Status").badge(),
            TextColumn.make("due").label("Due")
        );
    }
}
