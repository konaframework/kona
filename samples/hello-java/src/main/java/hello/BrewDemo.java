package hello;

import dev.kona.brew.Brew;
import dev.kona.brew.BrewManager;
import dev.kona.brew.BrewServiceProvider;
import dev.kona.brew.Component;
import dev.kona.core.Kona;
import dev.kona.http.HttpKernel;
import dev.kona.http.HttpServiceProvider;
import dev.kona.http.Request;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BrewDemo {
    public static class Counter extends Component {
        public int count = 0;

        public void increment() {
            count++;
        }

        public void decrement() {
            count--;
        }

        @Override
        public String render() {
            return "<div><p>Count: " + count + "</p>"
                + "<button data-brew-click=\"increment\">+</button>"
                + "<button data-brew-click=\"decrement\">-</button></div>";
        }
    }

    public static List<String> run(String[] args) {
        var app = Kona.create(args)
            .providers(new HttpServiceProvider(), new BrewServiceProvider())
            .boot();
        BrewManager brew = app.resolve(BrewManager.class);
        brew.register("counter", Counter::new);

        String html = Brew.mount("counter");
        Matcher m = Pattern.compile("data-brew=\"([^\"]+)\"").matcher(html);
        if (!m.find()) {
            throw new IllegalStateException("missing snapshot");
        }
        var updated = brew.update("counter", m.group(1), "increment");
        HttpKernel kernel = app.resolve(HttpKernel.class);
        var script = kernel.handle(Request.get("/brew/brew.js"));
        return List.of(
            "mount=" + html.contains("Count: 0"),
            "increment=" + updated.getHtml().contains("Count: 1"),
            "script=" + script.getStatus()
        );
    }

    private BrewDemo() {}
}
