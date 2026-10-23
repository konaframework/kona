package app.wire;

import org.konaframework.wire.Component;

/** Parent that nests children, an island, and a lazy panel. */
public class Dashboard extends Component {
    public String heard = "";

    public void onPanelSaved(String label) {
        heard = label == null || label.isBlank() ? "saved" : label;
    }

    @Override
    public String template() {
        return """
            <div class="card">
                <h2>Dashboard</h2>
                <p>heard={{ heard }}</p>
                @island('stats')
                <p>Island stats</p>
                @endisland
                <wire:panel wire:key="main" title="Main" @saved="onPanelSaved" />
                <wire:lazy-panel lazy />
            </div>
            """;
    }
}
