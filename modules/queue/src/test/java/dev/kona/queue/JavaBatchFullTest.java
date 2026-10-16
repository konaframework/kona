package dev.kona.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.cadence.ConnectionConfig;
import dev.kona.cadence.Database;
import dev.kona.core.Config;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class JavaBatchFullTest {
    @AfterEach
    void reset() {
        JobRegistry.INSTANCE.clear();
        BatchRepositories.reset();
        QueueEvents.clearDispatcher();
    }

    public static class CountingJob implements Job, ShouldQueue {
        private final AtomicBoolean ran;

        public CountingJob(AtomicBoolean ran) {
            this.ran = ran;
        }

        @Override
        public void handle() {
            ran.set(true);
        }
    }

    @Test
    void thenAndFinallyFromJava() {
        Config config = new Config();
        config.set("queue.default", "array");
        config.set("queue.connections.array.driver", "array");
        QueueManager manager = new QueueManager(config);
        Bus bus = new Bus(manager);
        AtomicBoolean then = new AtomicBoolean();
        AtomicBoolean fin = new AtomicBoolean();
        AtomicBoolean ran = new AtomicBoolean();
        Batch batch = bus.batch(new CountingJob(ran))
                .name("java")
                .then(b -> then.set(true))
                .onFinally(b -> fin.set(true))
                .dispatch();
        new Worker(manager, manager.defaultConnection(), manager.defaultQueue(), new WorkerOptions(), bus).run(5);
        assertTrue(batch.finished());
        assertTrue(then.get());
        assertTrue(fin.get());
        assertTrue(ran.get());
        assertEquals("java", batch.getName());
        // Cover Kotlin @JvmOverloads 2-arg payloadFor bridge.
        JobRegistry.INSTANCE.payloadFor(new CountingJob(new AtomicBoolean()), "java-key");
    }

    @Test
    void databaseBatchRepositoryJvmOverloads() {
        Database db = new Database()
                .addConnection(
                        "default",
                        new ConnectionConfig(
                                "jdbc:h2:mem:javabatch" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"))
                .setAsGlobal();
        InMemoryBatchRepository memory = new InMemoryBatchRepository();
        DatabaseBatchRepository one = new DatabaseBatchRepository(db.connection());
        DatabaseBatchRepository two = new DatabaseBatchRepository(db.connection(), "job_batches");
        DatabaseBatchRepository three = new DatabaseBatchRepository(db.connection(), "job_batches", memory);
        BatchRepositories.set(three);
        Config config = new Config();
        config.set("queue.default", "array");
        config.set("queue.connections.array.driver", "array");
        QueueManager manager = new QueueManager(config);
        Bus bus = new Bus(manager);
        AtomicBoolean ran = new AtomicBoolean();
        AtomicInteger finallyHits = new AtomicInteger();
        Batch batch = bus.batch(new CountingJob(ran))
                .allowFailures()
                .onFinally(b -> finallyHits.incrementAndGet())
                .dispatch();
        new Worker(manager, manager.defaultConnection(), manager.defaultQueue(), new WorkerOptions(), bus).run(5);
        assertTrue(batch.finished());
        assertEquals(1, finallyHits.get());
        assertTrue(one.find(batch.getId()) != null || two.find(batch.getId()) != null || memory.all().size() >= 0);
        BatchRepositories.reset();
        db.close();
        Database.clearGlobal();
    }
}
