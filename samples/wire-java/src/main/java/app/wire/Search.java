package app.wire;

import org.konaframework.wire.Component;
import org.konaframework.wire.Computed;
import java.util.List;

/** Live search: the input syncs 300ms after typing stops and the list re-renders. */
public class Search extends Component {
    static final List<String> BEANS = List.of("Kona Extra Fancy", "Kona Peaberry", "Ethiopia Yirgacheffe", "Colombia Supremo", "Sumatra Mandheling");

    public String query = "";

    @Computed
    public List<String> results() {
        String needle = query.trim().toLowerCase();
        return BEANS.stream().filter(bean -> needle.isEmpty() || bean.toLowerCase().contains(needle)).toList();
    }

    @Override
    public String template() {
        return """
            <div class="card">
                <h2>Search beans</h2>
                <input type="search" wire:model.live.debounce.300ms="query" placeholder="Try 'kona'">
                <ul>
                @foreach(results as bean)
                    <li wire:key="{{ bean }}">{{ bean }}</li>
                @endforeach
                </ul>
            </div>
            """;
    }
}
