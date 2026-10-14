package dev.kona.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaApiTest {
    @Test
    void staticFactoriesAndValidator() {
        assertEquals("ok", Response.text("ok").getBody());
        assertEquals(201, Response.text("ok", 201).getStatus());
        assertEquals("h", Response.html("h").getBody());
        assertEquals(201, Response.html("h", 201).getStatus());
        assertTrue(Response.json(Map.of("a", 1)).getBody().contains("a"));
        assertEquals(201, Response.json(Map.of("a", 1), 201).getStatus());
        assertEquals(204, Response.noContent().getStatus());

        Request get = Request.get("/x");
        assertEquals("GET", get.getMethod());
        assertEquals(null, get.getAttribute("missing"));
        get.setAttribute("k", "v");
        assertEquals("v", get.getAttribute("k"));
        Request post = Request.post("/y", "a=b");
        assertEquals("POST", post.getMethod());

        Validator v = Validator.make(Map.of("n", "Ada"), Map.of("n", List.of("required", "min:2")));
        assertTrue(v.passes());
        Validator v2 = Validator.make(Request.get("/"), Map.of("n", List.of("required")));
        assertTrue(v2.fails());
    }
}
