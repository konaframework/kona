package hello

import dev.kona.crema.CremaEngine
import dev.kona.crema.CremaServiceProvider
import dev.kona.crema.MapViewFinder
import dev.kona.core.Kona
import java.nio.file.Files
import java.nio.file.Path

object CremaDemo {
    @JvmStatic
    fun run(base: Path): Map<String, String> {
        val views = base.resolve("resources/views")
        Files.createDirectories(views.resolve("layouts"))
        Files.writeString(
            views.resolve("layouts").resolve("app.crema.html"),
            "<html>@yield('content')</html>",
        )
        Files.writeString(
            views.resolve("home.crema.html"),
            "@extends('layouts.app')@section('content')Hi {{ name }}@endsection",
        )
        val app = Kona.create(arrayOf("--base=$base")).providers(CremaServiceProvider()).boot()
        val engine = app.resolve(CremaEngine::class.java)
        val memory = CremaEngine(
            MapViewFinder().put("ping", "pong {{ n }}"),
        )
        return mapOf(
            "file" to engine.render("home", mapOf("name" to "Kona")),
            "memory" to memory.render("ping", mapOf("n" to 1)),
        )
    }
}
