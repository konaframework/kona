package org.konaframework.firefly;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.konaframework.core.Application;
import org.konaframework.http.HttpServiceProvider;
import org.konaframework.http.Router;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FireflyRegisterTest {
    @AfterEach
    public void resetRegistry() {
        Firefly.reset();
    }

    @Test
    public void registerMountsAPanelAndASupplier() throws Exception {
        Firefly.reset();
        var root = Files.createTempDirectory("firefly-register");
        Firefly.register(new Panel().id("billing").authenticate(false).page(new ListPage("orders")));
        Firefly.register(() -> new Panel().id("ops").path("").authenticate(false).page(new ListPage("home")));
        var app = Application.builder(root).withProvider(new HttpServiceProvider()).build();
        var router = app.resolve(Router.class);
        assertNotNull(router.find("GET", "/billing/orders"));
        assertNotNull(router.find("GET", "/home"));
        Firefly.mount(app);
    }
}
