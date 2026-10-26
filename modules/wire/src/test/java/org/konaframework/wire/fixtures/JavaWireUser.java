package org.konaframework.wire.fixtures;

import org.konaframework.cadence.Cadence;

/** Java Cadence model rendered from a Wire template. */
public class JavaWireUser extends Cadence<JavaWireUser> {
    @Override
    public boolean getTimestamps() {
        return false;
    }

    public String getEmail() {
        Object value = getAttribute("email");
        return value == null ? null : value.toString();
    }
}
