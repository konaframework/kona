package app.firefly;

import org.konaframework.core.Kona;

/** Desk panel. Pages, widgets, and resources are discovered from this directory. */
public class Panel extends org.konaframework.firefly.Panel {
    public Panel() {
        id("desk");
        path("admin");
        brand("Desk");
        authenticate(false);
        discover(Kona.app().basePath("src/main/java/app/firefly"));
    }
}
