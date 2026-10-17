package org.konaframework.http;

import java.util.HashMap;
import java.util.Map;

/** Relation-shaped model with arity/type overloads for findModelMethod branch coverage. */
public final class JavaRelationModel {
    private final Map<String, Object> relations = new HashMap<>();

    public boolean relationLoaded() {
        return false;
    }

    public boolean relationLoaded(int ignored) {
        return false;
    }

    public boolean relationLoaded(String name) {
        return relations.containsKey(name);
    }

    public Object getRelation() {
        return null;
    }

    public Object getRelation(int ignored) {
        return null;
    }

    public Object getRelation(String name) {
        return relations.get(name);
    }

    public void setRelation(String name, Object value) {
        relations.put(name, value);
    }
}
