package org.konaframework.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class JavaScheduleApiTest {
    @Test
    void scheduleFromJava() {
        AtomicInteger n = new AtomicInteger();
        Schedule schedule = new Schedule();
        schedule.everyMinute("t", n::incrementAndGet);
        schedule.dailyAt("noon", 12, 0, () -> {});
        schedule.everyFiveMinutes("five", () -> {}).withoutOverlapping(5);
        assertTrue(new CronExpression("* * * * *").matches(LocalDateTime.now()));
        assertTrue(schedule.runDue(LocalDateTime.now()).contains("t"));
        assertEquals(1, n.get());
        assertTrue(schedule.events().size() >= 3);
    }
}
