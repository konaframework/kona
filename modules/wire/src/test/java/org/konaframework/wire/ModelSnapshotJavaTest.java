package org.konaframework.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.crema.CremaEngine;
import org.konaframework.crema.MapViewFinder;
import org.konaframework.wire.fixtures.JavaModelHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Java components keep Cadence models across a Wire round trip. */
class ModelSnapshotJavaTest {
    @AfterEach
    void reset() {
        Wire.reset();
    }

    @Test
    void javaTemplateReadsTheModelAfterRefresh() {
        WireManager manager = new WireManager(new WireConfig("test-key"), new CremaEngine(new MapViewFinder()));
        Wire.setManager(manager);
        String html = Wire.test(JavaModelHost.class).refresh().html();
        assertTrue(html.contains("a@b.c|Ada"), html);
        JavaModelHost host = (JavaModelHost) Wire.test(JavaModelHost.class).instance();
        assertEquals("a@b.c", host.user.getEmail());
    }
}
