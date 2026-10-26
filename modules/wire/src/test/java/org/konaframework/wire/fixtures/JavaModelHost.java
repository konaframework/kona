package org.konaframework.wire.fixtures;

import org.konaframework.wire.Component;

/** Java component that prints a nullable Cadence model from its template. */
public class JavaModelHost extends Component {
    public JavaWireUser user = new JavaWireUser();

    public JavaModelHost() {
        user.setAttribute("email", "a@b.c");
        user.setAttribute("name", "Ada");
    }

    @Override
    public String template() {
        return "<p>{{ user?.getEmail() }}|{{ user?.getAttribute('name') }}</p>";
    }
}
