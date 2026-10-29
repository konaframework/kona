package app.firefly.resources;

import app.models.Task;
import org.konaframework.firefly.Resource;
import org.konaframework.firefly.forms.Select;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.infolists.TextEntry;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.tables.Table;
import org.konaframework.firefly.tables.TextColumn;

import java.util.Map;

public class TaskResource extends Resource {
    public TaskResource() {
        super("tasks");
        label("Task");
        simple();
        model(Task.class);
        formColumns(2);
        infolistColumns(2);
    }

    @Override
    public Schema form(Schema schema) {
        return schema.components(
            TextInput.make("name").label("Name").required(),
            Select.make("project_id")
                .label("Project")
                .relationship("project", "name")
                .preload()
                .searchable(),
            Select.make("status")
                .label("Status")
                .options(Map.of("open", "Open", "done", "Done"))
                .searchable()
        );
    }

    @Override
    public Schema infolist(Schema schema) {
        return schema.components(
            TextEntry.make("name").label("Name"),
            TextEntry.make("project_id").label("Project"),
            TextEntry.make("status").label("Status")
        );
    }

    @Override
    public Table<?, ?> table(Table<?, ?> table) {
        return table.columns(
            TextColumn.make("name").label("Name").searchable().sortable(),
            TextColumn.make("project_id").label("Project").relationship("project", "name"),
            TextColumn.make("status").label("Status").badge()
        );
    }
}
