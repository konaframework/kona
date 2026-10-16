package io.konaframework.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaNicheValidationTest {
    @Test
    void nicheRulesAndStreamFromJava() {
        Validator.setCurrentPasswordChecker(plain -> plain.equals("pw"));
        assertTrue(Validator.make(Map.of("f", "pw"), Map.of("f", List.of("current_password"))).passes());
        assertTrue(Validator.make(Map.of("f", "01:23:45:67:89:ab"), Map.of("f", List.of("mac_address"))).passes());
        assertTrue(
                Validator.make(Map.of("f", "01ARZ3NDEKTSV4RRFFQ69G5FAV"), Map.of("f", List.of("ulid"))).passes());
        Validator.setCurrentPasswordChecker(null);

        Response streamed = Response.stream(out -> {
            try {
                out.write("java".getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals("java", new String(streamed.bodyBytes(), StandardCharsets.UTF_8));
        assertEquals("application/octet-stream", streamed.getHeaders().get("Content-Type"));
    }
}
