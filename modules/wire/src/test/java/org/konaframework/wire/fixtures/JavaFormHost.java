package org.konaframework.wire.fixtures;

import org.konaframework.wire.Component;
import org.konaframework.wire.Form;

/** Java host whose form field starts null so Wire constructs it. */
public class JavaFormHost extends Component {
    public JavaProfileForm form;
    public String label;

    public void save() {
        validate();
    }

    @Override
    public String template() {
        return "<div></div>";
    }
}
