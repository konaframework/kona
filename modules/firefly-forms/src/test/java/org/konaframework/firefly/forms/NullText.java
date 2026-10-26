package org.konaframework.firefly.forms;

import java.util.function.Function;
import org.konaframework.firefly.schemas.Get;

public final class NullText implements Function<Get, String> {
    @Override
    public String apply(Get get) {
        return null;
    }
}
