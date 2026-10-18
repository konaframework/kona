package org.konaframework.brew.fixtures;

import org.konaframework.brew.Form;
import org.konaframework.brew.Rule;
import org.konaframework.brew.Validate;

/** Java form so {@code @Rule} lands on the public field for FormMeta. */
public class JavaProfileForm extends Form {
    @Validate({"required", "min:2"})
    public String name = "";

    @Rule({"max:40"})
    public String nickname = "";
}
