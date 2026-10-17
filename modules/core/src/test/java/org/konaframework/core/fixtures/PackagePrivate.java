package org.konaframework.core.fixtures;

public final class PackagePrivate {
    private PackagePrivate() {
    }

    public static Class<?> hiddenType() {
        return Hidden.class;
    }
}

class Hidden {
    public Hidden() {
    }
}
