package org.konaframework.wire.fixtures;

import org.konaframework.wire.Form;
import org.konaframework.wire.Rule;
import org.konaframework.wire.Validate;

/** Java form so {@code @Rule} lands on the public field for FormMeta. */
public class JavaProfileForm extends Form {
    @Validate({"required", "min:2"})
    public String name = "";

    @Rule({"max:40"})
    public String nickname = "";
}
