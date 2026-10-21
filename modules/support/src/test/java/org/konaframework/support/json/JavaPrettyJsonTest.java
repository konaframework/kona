package org.konaframework.support.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

public class JavaPrettyJsonTest {
    @Test
    void stringifyFromJava() {
        LinkedHashMap<String, Object> scripts = new LinkedHashMap<>();
        scripts.put("dev", "vite");
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("private", true);
        body.put("scripts", scripts);
        body.put("lib", List.of("DOM"));
        assertEquals(
            "{\n  \"private\": true,\n  \"scripts\": {\n    \"dev\": \"vite\"\n  },\n  \"lib\": [\n    \"DOM\"\n  ]\n}\n",
            PrettyJson.stringify(body)
        );
        assertEquals("null\n", PrettyJson.stringify(null));
    }
}
