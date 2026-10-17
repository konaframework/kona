package org.konaframework.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class JavaMailApiTest {
    static final class Note extends Mailable {
        @Override
        public Envelope envelope() {
            return new Envelope("Note", new Address("from@kona.dev"), java.util.List.of(), java.util.List.of(), java.util.List.of());
        }

        @Override
        public Content content() {
            return new Content(null, "hello");
        }
    }

    @Test
    void sendMailableFromJava() {
        LogTransport transport = new LogTransport();
        Mailer mailer = new Mailer(transport, new Address("default@kona.dev", null));
        mailer.to("user@example.com").send(new Note());
        assertEquals(1, transport.messages().size());
        assertEquals("Note", transport.messages().get(0).getSubject());
    }
}
