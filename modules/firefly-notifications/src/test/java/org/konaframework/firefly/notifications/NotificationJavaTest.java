package org.konaframework.firefly.notifications;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.konaframework.firefly.actions.Action;
import org.konaframework.notifications.DatabaseChannel;
import org.konaframework.notifications.Notifiable;
import org.konaframework.session.ArraySessionStore;
import org.konaframework.session.StoreSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NotificationJavaTest {
    @AfterEach
    void clear() {
        FireflyNotification.configureUsing(null);
    }

    @Test
    void javaSendsANotification() {
        FireflyNotification.configureUsing(notice -> notice.duration(2));
        StoreSession session = new StoreSession("java", new ArraySessionStore());
        FireflyNotification generated = FireflyNotification.make();
        generated.title("Generated");
        assertTrue(generated.render().contains("Generated"));
        FireflyNotification notice = FireflyNotification.make("java");
        notice.title(get -> "Java");
        notice.body("Saved");
        notice.icon("bell");
        notice.color(get -> "teal");
        notice.success();
        notice.extraAttributes(get -> Map.of("data-lang", "java"));
        notice.actions(Action.make("open").label("Open"));
        notice.send(session, Map.of("unused", "1"));
        assertTrue(FireflyNotification.render(session).contains("Java"));
        String html = FireflyNotification.pull(session);
        assertTrue(html.contains("Java"));
        assertTrue(html.contains("data-ff-color=\"teal\""));
        assertTrue(html.contains("data-ff-duration=\"2\""));
        assertTrue(html.contains("Open"));

        DatabaseChannel channel = new DatabaseChannel();
        notice.sendToDatabase(new Notifiable() {
            @Override
            public String routeNotificationFor(String name) {
                return "ada";
            }
        }, channel, Map.of());
        assertTrue(new NotificationBell(channel, "ada").render().contains("Java"));
        assertTrue(new BroadcastRefresh().channel(get -> "desk").event("ping").render().contains("desk"));
    }
}
