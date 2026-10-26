package org.konaframework.firefly.discovered;

import org.konaframework.firefly.ListPage;
import org.konaframework.firefly.Resource;

public class JavaResource extends Resource {
    public JavaResource() {
        super("java-notes");
        page(new ListPage("all"));
    }
}
