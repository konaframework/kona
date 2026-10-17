package org.konaframework.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.core.Config;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Java-callable durable job restore (Law 8). */
public class JavaDurableJobTest {
    public static class TokenJob implements Job {
        public static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

        public String token = "";

        @Override
        public void handle() {
            SEEN.add(token);
        }
    }

    @AfterEach
    void reset() {
        JobRegistry.INSTANCE.clear();
        TokenJob.SEEN.clear();
    }

    @Test
    void restoresJobBodyAcrossRegistryClear() {
        Config config = new Config();
        config.set("queue.default", "array");
        config.set("queue.connections.array.driver", "array");
        QueueManager manager = new QueueManager(config);
        ArrayQueue queue = (ArrayQueue) manager.connection();
        TokenJob job = new TokenJob();
        job.token = "java-token";
        queue.push(job, "default");
        JobRegistry.INSTANCE.clear();
        assertEquals(1, new Worker(manager).run(1));
        assertTrue(TokenJob.SEEN.contains("java-token"));
    }
}
