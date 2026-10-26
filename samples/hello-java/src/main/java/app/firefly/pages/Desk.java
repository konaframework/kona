package app.firefly.pages;

import org.konaframework.firefly.CustomPage;
import org.konaframework.firefly.actions.Action;
import org.konaframework.firefly.forms.DateTimePicker;
import org.konaframework.firefly.forms.Select;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.infolists.Infolist;
import org.konaframework.firefly.infolists.TextEntry;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.tables.Table;
import org.konaframework.firefly.tables.TextColumn;

import java.util.LinkedHashMap;
import java.util.Map;

public class Desk extends CustomPage {
    public Desk() {
        super("desk");
        title("Desk");
        mountForm(Schema.make().components(
            TextInput.make("name"),
            Select.make("status").options(Map.of("open", "Open", "done", "Done")),
            DateTimePicker.make("due")
        ));
        mountInfolist(Infolist.make().components(TextEntry.make("name")));
        table(Table.memory(row()).columns(TextColumn.make("name")));
        headerActions(Action.make("edit").label("Edit").modal().modalHeading("Edit note").schema(TextInput.make("note")));
    }

    private static Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "1");
        row.put("name", "Ada");
        return row;
    }
}
