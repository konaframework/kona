package org.konaframework.firefly.schemas;

final class Markup {
    Markup() {
    }

    static String optionalSpan(String attribute, String value) {
        if (value == null) {
            return "";
        }
        return "<span " + attribute + "=\"" + EscapeKt.escapeHtml(value) + "\"></span>";
    }

    static Object stateAt(Schema schema, String path) {
        if (schema == null || path == null) {
            return null;
        }
        return schema.get(path);
    }

    static void hydrated(HydratedHook hook, Object state, Get get, Set set) {
        if (hook != null) {
            hook.hydrated(state, get, set);
        }
    }
}
