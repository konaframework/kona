package org.konaframework.firefly.notifications;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.konaframework.session.ArraySessionStore;
import org.konaframework.session.StoreSession;

class FireflyToastsJavaTest {
    @Test
    void resourceDefaultsFlashSuccessToasts() {
        StoreSession session = new StoreSession("desk", new ArraySessionStore());
        FireflyToasts.created(session);
        FireflyToasts.saved(session);
        FireflyToasts.deleted(session);
        String html = FireflyNotification.pull(session);
        assertTrue(html.contains("Created"));
        assertTrue(html.contains("Saved"));
        assertTrue(html.contains("Deleted"));
        assertTrue(html.contains("data-ff-status=\"success\""));
    }
}
