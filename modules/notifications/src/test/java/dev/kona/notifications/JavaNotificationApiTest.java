package dev.kona.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.kona.mail.Content;
import dev.kona.mail.Envelope;
import dev.kona.mail.LogTransport;
import dev.kona.mail.Mailable;
import dev.kona.mail.Mailer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaNotificationApiTest {
    static final class Person implements Notifiable {
        private final String email;

        Person(String email) {
            this.email = email;
        }

        @Override
        public String routeNotificationFor(String channel) {
            return email;
        }
    }

    static final class Hello extends Notification {
        @Override
        public List<String> via(Notifiable notifiable) {
            return List.of("mail", "database");
        }

        @Override
        public Mailable toMail(Notifiable notifiable) {
            return new Mailable() {
                @Override
                public Envelope envelope() {
                    return new Envelope("Hello", null, List.of(), List.of(), List.of());
                }

                @Override
                public Content content() {
                    return new Content(null, "hi");
                }
            };
        }

        @Override
        public Map<String, Object> toArray(Notifiable notifiable) {
            return Map.of("ok", true);
        }
    }

    @Test
    void notifyFromJava() {
        LogTransport transport = new LogTransport();
        DatabaseChannel db = new DatabaseChannel();
        NotificationSender sender =
                new NotificationSender()
                        .extend("mail", new MailChannel(new Mailer(transport, null)))
                        .extend("database", db);
        sender.send(new Person("p@x.com"), new Hello());
        assertEquals(1, transport.messages().size());
        assertEquals(1, db.all().size());
        assertEquals(1, db.unread("p@x.com").size());
        NotificationRecord row = db.unread("p@x.com").get(0);
        db.markAsRead(row.getId());
        assertEquals(0, db.unread("p@x.com").size());
    }
}
