package org.konaframework.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.crema.CremaEngine;
import org.konaframework.crema.MapViewFinder;
import org.konaframework.wire.fixtures.JavaFormHost;
import org.konaframework.wire.fixtures.JavaProfileForm;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Java callers use the same form-object surface as Kotlin. */
class FormObjectJavaTest {
    @AfterEach
    void reset() {
        Wire.reset();
    }

    @Test
    void javaFormObject() {
        WireManager manager = new WireManager(new WireConfig("test-key"), new CremaEngine(new MapViewFinder()));
        manager.component("java-form-host", JavaFormHost.class);
        Wire.setManager(manager);
        JavaFormHost host = (JavaFormHost) Wire.test(JavaFormHost.class).instance();
        JavaProfileForm form = host.form;
        assertEquals("form", form.getPropertyName());
        assertTrue(form.getComponent() == host);
        form.fill(Map.of("name", "Ada", "nickname", "nick"));
        assertEquals("Ada", form.pull().get("name"));
        assertEquals("", form.all().get("name"));
        form.fill(Map.of("name", "Ada", "nickname", "nick"));
        form.reset(List.of("nickname"));
        assertEquals("Ada", form.all().get("name"));
        assertEquals("", form.all().get("nickname"));
        form.resetExcept("name");
        assertEquals("Ada", form.all().get("name"));
        form.addError("name", "bad");
        assertTrue(form.getErrorBag().has("form.name"));
        form.resetErrorBag("name");
        assertFalse(host.errorBag().has("form.name"));
        form.name = "Ada";
        form.nickname = "ok";
        assertEquals("Ada", form.validate().get("name"));
    }
}
