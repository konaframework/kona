package hello;

import dev.kona.core.Application;
import dev.kona.core.Kona;
import dev.kona.events.EventDispatcher;
import dev.kona.events.EventServiceProvider;
import dev.kona.filesystem.FilesystemAdapter;
import dev.kona.filesystem.FilesystemManager;
import dev.kona.filesystem.FilesystemServiceProvider;
import dev.kona.mail.Content;
import dev.kona.mail.Envelope;
import dev.kona.mail.LogTransport;
import dev.kona.mail.MailServiceProvider;
import dev.kona.mail.Mailable;
import dev.kona.mail.Mailer;
import dev.kona.notifications.Notifiable;
import dev.kona.notifications.Notification;
import dev.kona.notifications.NotificationSender;
import dev.kona.notifications.NotificationServiceProvider;
import dev.kona.queue.Bus;
import dev.kona.queue.Job;
import dev.kona.queue.QueueManager;
import dev.kona.queue.QueueServiceProvider;
import dev.kona.queue.ShouldQueue;
import dev.kona.queue.Worker;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class AsyncDemo {
    public static final class OrderShipped {
        public final String id;

        public OrderShipped(String id) {
            this.id = id;
        }
    }

    static final class WorkJob implements Job, ShouldQueue {
        private final AtomicInteger counter;

        WorkJob(AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public void handle() {
            counter.incrementAndGet();
        }
    }

    public static Map<String, String> run(Path base) {
        Application app =
                Kona.create(new String[] {"--base=" + base})
                        .configure(
                                config -> {
                                    config.set("queue.default", "array");
                                    config.set("queue.connections.array.driver", "array");
                                    config.set("mail.default", "log");
                                    config.set("filesystems.default", "local");
                                    config.set(
                                            "filesystems.disks.local.root",
                                            base.resolve("storage").toString());
                                })
                        .providers(
                                new EventServiceProvider(),
                                new QueueServiceProvider(),
                                new MailServiceProvider(),
                                new NotificationServiceProvider(),
                                new FilesystemServiceProvider())
                        .boot();

        StringBuilder event = new StringBuilder();
        app.resolve(EventDispatcher.class).listen(OrderShipped.class, e -> event.append(e.id));
        app.resolve(EventDispatcher.class).dispatch(new OrderShipped("42"));

        AtomicInteger counter = new AtomicInteger();
        app.resolve(Bus.class).dispatch(new WorkJob(counter));
        new Worker(app.resolve(QueueManager.class)).run(1);

        LogTransport transport = new LogTransport();
        new Mailer(transport, null)
                .to("user@example.com")
                .send(
                        new Mailable() {
                            @Override
                            public Envelope envelope() {
                                return new Envelope("Hi", null, List.of(), List.of(), List.of());
                            }

                            @Override
                            public Content content() {
                                return new Content(null, "Hello");
                            }
                        });

        app.resolve(NotificationSender.class)
                .send(
                        (Notifiable) channel -> "user@example.com",
                        new Notification() {
                            @Override
                            public List<String> via(Notifiable notifiable) {
                                return List.of("log", "database");
                            }

                            @Override
                            public Map<String, Object> toArray(Notifiable notifiable) {
                                return Map.of("ok", true);
                            }
                        });

        FilesystemAdapter disk = app.resolve(FilesystemManager.class).disk();
        disk.put("demo.txt", "stored");

        return Map.of(
                "event",
                event.toString(),
                "jobs",
                Integer.toString(counter.get()),
                "mail",
                Integer.toString(transport.messages().size()),
                "file",
                disk.getString("demo.txt"));
    }

    private AsyncDemo() {}
}
