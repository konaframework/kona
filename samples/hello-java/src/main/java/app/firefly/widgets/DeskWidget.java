package app.firefly.widgets;

import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.widgets.Widget;

public class DeskWidget extends Widget {
    public DeskWidget() {
        super("desk");
    }

    @Override
    public String renderBody(Schema schema) {
        return "<p class=\"ff-desk-widget\">Open orders</p>";
    }
}
