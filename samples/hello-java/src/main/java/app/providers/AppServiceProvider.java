package app.providers;

import io.konaframework.core.Application;
import io.konaframework.core.ServiceProvider;

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
