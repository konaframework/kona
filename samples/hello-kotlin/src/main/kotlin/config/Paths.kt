package config

import org.konaframework.core.ConfigFile

/**
 * Project layout paths (`paths.*`).
 *
 * These tell grind and the HTTP kernel where source, views, storage, and the
 * public web root live relative to the application base path.
 */
object Paths : ConfigFile {
    override fun key(): String = "paths"

    override fun config(): Map<String, Any?> = mapOf(
        /*
        |--------------------------------------------------------------------------
        | Language / source root
        |--------------------------------------------------------------------------
        |
        | Where Kotlin (or Java) application code lives. grind generators write
        | controllers, models, and config next to this tree.
        |
        */
        "langRoot" to "src/main/kotlin",

        /*
        |--------------------------------------------------------------------------
        | Resources
        |--------------------------------------------------------------------------
        |
        | Crema views, lang files shipped as resources, and similar classpath
        | assets. The public disk of static files is configured separately.
        |
        */
        "resources" to "src/main/resources",

        /*
        |--------------------------------------------------------------------------
        | Storage
        |--------------------------------------------------------------------------
        |
        | Writable application storage (logs, compiled views, sessions, uploads,
        | the SQLite file). Keep this path out of version control except for
        | .gitkeep placeholders.
        |
        */
        "storage" to "storage",

        /*
        |--------------------------------------------------------------------------
        | Public directory
        |--------------------------------------------------------------------------
        |
        | HTTP document root served by ServePublicDirectory (logo, favicon, robots.txt,
        | and Vite output under `public/build`). `./grind storage:link` places the
        | public-disk symlink here.
        |
        */
        "public" to "public",
    )
}
