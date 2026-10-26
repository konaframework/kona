package org.konaframework.firefly.infolists;

import org.konaframework.firefly.schemas.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Schema child walk is internal to the schemas module. This type overrides the
 * public JVM method so entry slots stay in the same tree.
 */
public abstract class EntryHost<T extends EntryHost<T>> extends Component {
    protected EntryHost(String name) {
        super(name);
    }

    protected abstract List<Component> slotChildren();

    public List<Component> hostedChildren() {
        return allChildren$kona_firefly_schemas();
    }

    @Override
    public List<Component> allChildren$kona_firefly_schemas() {
        List<Component> all = new ArrayList<>();
        all.addAll(super.allChildren$kona_firefly_schemas());
        all.addAll(slotChildren());
        return all;
    }
}
