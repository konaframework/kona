package org.konaframework.firefly.discovered;

import org.konaframework.firefly.ListPage;
import org.konaframework.firefly.Resource;
import org.konaframework.firefly.widgets.Page;

import java.util.LinkedHashMap;
import java.util.Map;

public class JavaResource extends Resource {
    public JavaResource() {
        super("java-notes");
    }

    @Override
    public Map<String, Page> getPages() {
        Map<String, Page> pages = new LinkedHashMap<>();
        pages.put("all", new ListPage("all"));
        return pages;
    }
}
