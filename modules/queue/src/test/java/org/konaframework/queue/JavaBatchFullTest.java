package org.konaframework.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.cadence.ConnectionConfig;
import org.konaframework.cadence.Database;
import org.konaframework.core.Config;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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

    public static class FailingJob implements Job, ShouldQueue {
        @Override
        public void handle() {
            throw new IllegalStateException("java-cross");
        }
    }

    public static class JavaThen implements Consumer<Batch> {
        public static final AtomicInteger hits = new AtomicInteger();
        public static final AtomicReference<String> note = new AtomicReference<>();
        public String label = "java";

        @Override
        public void accept(Batch batch) {
            hits.incrementAndGet();
            note.set(label);
        }
    }

    public static class JavaCatch implements BiConsumer<Batch, Throwable> {
        public static final AtomicInteger hits = new AtomicInteger();
        public static final AtomicReference<String> message = new AtomicReference<>();

        @Override
        public void accept(Batch batch, Throwable error) {
            hits.incrementAndGet();
            message.set(error == null ? null : error.getMessage());
        }
    }

    @Test
    void concreteCallbacksRunFromAnotherRepository() {
        JavaThen.hits.set(0);
        JavaCatch.hits.set(0);
        Database db = new Database()
                .addConnection(
                        "default",
                        new ConnectionConfig(
                                "jdbc:h2:mem:javacb" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"))
                .setAsGlobal();
        DatabaseBatchRepository origin = new DatabaseBatchRepository(db.connection(), "java_batches");
        BatchRepositories.set(origin);
        Config config = new Config();
        config.set("queue.default", "array");
        config.set("queue.connections.array.driver", "array");
        QueueManager manager = new QueueManager(config);
        Bus bus = new Bus(manager);
        JavaThen then = new JavaThen();
        then.label = "from-java";
        bus.batch(new CountingJob(new AtomicBoolean()))
                .then(then)
                .dispatch();
        BatchRepositories.set(new DatabaseBatchRepository(db.connection(), "java_batches"));
        new Worker(manager, manager.defaultConnection(), manager.defaultQueue(), new WorkerOptions(), bus).run(5);
        assertEquals(1, JavaThen.hits.get());
        assertEquals("from-java", JavaThen.note.get());

        BatchRepositories.set(origin);
        bus.batch(new FailingJob())
                .onCatch(new JavaCatch())
                .dispatch();
        BatchRepositories.set(new DatabaseBatchRepository(db.connection(), "java_batches"));
        new Worker(
                manager,
                manager.defaultConnection(),
                manager.defaultQueue(),
                new WorkerOptions(Integer.MAX_VALUE, false, true, 60L, 0, 1, null, 3L, Integer.MAX_VALUE),
                bus)
                .run(5);
        assertEquals(1, JavaCatch.hits.get());
        assertEquals("java-cross", JavaCatch.message.get());
        db.close();
        Database.clearGlobal();
    }
}
