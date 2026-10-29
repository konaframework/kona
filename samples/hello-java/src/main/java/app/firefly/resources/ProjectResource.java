package app.firefly.resources;

import app.models.Project;
import org.konaframework.firefly.Resource;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.forms.Toggle;
import org.konaframework.firefly.infolists.IconEntry;
import org.konaframework.firefly.infolists.TextEntry;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.tables.IconColumn;
import org.konaframework.firefly.tables.Table;
import org.konaframework.firefly.tables.TextColumn;

public class ProjectResource extends Resource {
    public ProjectResource() {
        super("projects");
        label("Project");
        simple();
        model(Project.class);
        formColumns(2);
        infolistColumns(2);
    }

    @Override
    public Schema form(Schema schema) {
        return schema.components(
            TextInput.make("name").label("Name").required(),
            Toggle.make("active").label("Active")
        );
    }

    @Override
    public Schema infolist(Schema schema) {
        return schema.components(
            TextEntry.make("name").label("Name"),
            IconEntry.make("active").label("Active").bool(true)
        );
    }

    @Override
    public Table<?, ?> table(Table<?, ?> table) {
        return table.columns(
            TextColumn.make("name").label("Name").searchable().sortable(),
            IconColumn.make("active").bool().label("Active")
        );
    }
}
