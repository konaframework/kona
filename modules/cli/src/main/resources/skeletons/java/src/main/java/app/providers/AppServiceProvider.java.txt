package app.providers;

import org.konaframework.core.Application;
import org.konaframework.core.ServiceProvider;

public class AppServiceProvider implements ServiceProvider {
    @Override
    public void register(Application app) {
        // Bind application services into app.getContainer() here.
    }

    @Override
    public void boot(Application app) {
        // Runs after every provider has registered.
    }
}
