package hello;

import dev.kona.broadcasting.Broadcast;
import dev.kona.broadcasting.BroadcastServiceProvider;
import dev.kona.broadcasting.Channel;
import dev.kona.broadcasting.ShouldBroadcast;
import dev.kona.core.Kona;
import dev.kona.search.Scout;
import dev.kona.search.SearchServiceProvider;
import dev.kona.search.Searchable;
import java.util.List;
import java.util.Map;

public final class M10Demo {
    static final class Ping implements ShouldBroadcast {
        @Override
        public List<Channel> broadcastOn() {
            return List.of(Channel.publicChannel("demo"));
        }

        @Override
        public String broadcastAs() {
            return "ping";
        }

        @Override
        public Map<String, Object> broadcastWith() {
            return Map.of("ok", true);
        }
    }

    static final class Item implements Searchable {
        private final String id;
        private final String name;

        Item(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String searchableAs() {
            return "items";
        }

        @Override
        public String getScoutKey() {
            return id;
        }

        @Override
        public Map<String, Object> toSearchableArray() {
            return Map.of("name", name);
        }
    }

    public static List<String> run(String[] args) {
        var app = Kona.create(args)
            .providers(new BroadcastServiceProvider(), new SearchServiceProvider())
            .boot();
        Broadcast.event(new Ping());
        Scout.update(new Item("1", "kona roast"));
        var search = Scout.search("items", "kona");
        return List.of(
            "broadcast=" + Broadcast.manager().log().messages().size(),
            "search=" + search.keys()
        );
    }

    private M10Demo() {}
}
