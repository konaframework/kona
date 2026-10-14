package hello;

import dev.kona.core.Application;
import dev.kona.core.Kona;
import dev.kona.crema.CremaEngine;
import dev.kona.crema.CremaServiceProvider;
import dev.kona.crema.MapViewFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CremaDemo {
    private CremaDemo() {}

    public static Map<String, String> run(Path base) throws Exception {
        Path views = base.resolve("resources/views");
        Files.createDirectories(views.resolve("layouts"));
        Files.writeString(
            views.resolve("layouts").resolve("app.crema.html"),
            "<html>@yield('content')</html>"
        );
        Files.writeString(
            views.resolve("home.crema.html"),
            "@extends('layouts.app')@section('content')Hi {{ name }}@endsection"
        );
        Application app = Kona.create(new String[] {"--base=" + base})
            .providers(new CremaServiceProvider())
            .boot();
        CremaEngine engine = app.resolve(CremaEngine.class);
        CremaEngine memory = new CremaEngine(new MapViewFinder().put("ping", "pong {{ n }}"));
        Map<String, String> out = new LinkedHashMap<>();
        out.put("file", engine.render("home", Map.of("name", "Kona")));
        out.put("memory", memory.render("ping", Map.of("n", 1)));
        return out;
    }
}
