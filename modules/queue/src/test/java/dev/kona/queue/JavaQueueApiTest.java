package dev.kona.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.kona.core.Config;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class JavaQueueApiTest {
    static final class IncJob implements Job, ShouldQueue {
        private final AtomicInteger n;

        IncJob(AtomicInteger n) {
            this.n = n;
        }

        @Override
        public void handle() {
            n.incrementAndGet();
        }
    }

    @Test
    void busAndArrayWorkerFromJava() {
        AtomicInteger n = new AtomicInteger();
        Config config = new Config();
        config.set("queue.default", "array");
        config.set("queue.connections.array.driver", "array");
        QueueManager manager = new QueueManager(config);
        Bus bus = new Bus(manager);
        bus.dispatch(new IncJob(n));
        assertEquals(0, n.get());
        assertEquals(1, new Worker(manager).run());
        assertEquals(1, n.get());
        bus.dispatchSync(() -> n.incrementAndGet());
        assertEquals(2, n.get());
    }
}
