package dev.kona.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

public class JavaConcurrencyApiTest {
    @Test
    void mapFromJava() {
        assertEquals(List.of(2, 4), Concurrency.map(List.of(1, 2), n -> n * 2));
        assertEquals(List.of(1), Concurrency.run(List.of((Callable<Integer>) () -> 1)));
    }
}
