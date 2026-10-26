package org.konaframework.cadence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Java calls the getters and setters generated for column properties. */
class ColumnJavaTest {
    @Test
    void columnPropertiesFromJava() {
        ColumnUser user = new ColumnUser();
        assertNull(user.getName());
        user.setName("Ada");
        user.setEmail("ada@example.com");
        user.setNickname("A");
        assertEquals("Ada", user.getName());
        assertEquals("ada@example.com", user.getAttribute("email_address"));
        assertEquals("A", user.getNickname());
    }
}
