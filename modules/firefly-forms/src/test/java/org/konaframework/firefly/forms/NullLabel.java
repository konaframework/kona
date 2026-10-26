package org.konaframework.firefly.forms;

import java.util.function.Function;

public final class NullLabel implements Function<Object, String> {
    @Override
    public String apply(Object record) {
        return null;
    }
}
