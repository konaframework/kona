package dev.kona.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class JavaScheduleApiTest {
    @Test
    void scheduleFromJava() {
        AtomicInteger n = new AtomicInteger();
        Schedule schedule = new Schedule().everyMinute("t", n::incrementAndGet);
        assertTrue(new CronExpression("* * * * *").matches(LocalDateTime.now()));
        assertEquals(1, schedule.runDue(LocalDateTime.now()).size());
        assertEquals(1, n.get());
    }
}
