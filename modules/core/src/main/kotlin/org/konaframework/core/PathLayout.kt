package org.konaframework.core

import java.nio.file.Files
import java.nio.file.Path

/***********************************************************************************************************************
 * PathLayout is a utility class that defines the directory structure of a Kona application.
 * It provides methods to resolve paths for various application components such as source code, resources, storage, and public assets.
 * The layout follows the conventions of a typical JVM project with a `src/main` directory structure.
 *
 * Defaults:
 * - language root: `src/main/kotlin` or `src/main/java` (detected)
 * - resources: `src/main/resources`
 * - storage: project-root `storage/`
 * - public: project-root `public/` (HTTP document root; Vite writes `public/build`)
 *
 * Overrides via config keys `paths.langRoot`, `paths.resources`, `paths.storage`, `paths.public`
 * (relative to [base]).
 * *********************************************************************************************************************
 */
class PathLayout(
    val base: Path,
    val langRoot: Path,
    val resources: Path,
    val storage: Path,
    val publicDir: Path,
) {
    /** Joins a root path with multiple segments, ignoring empty segments. */
    fun join(root: Path, segments: Array<out String>): Path {
        var result = root
        for (segment in segments) {
            if (segment.isNotEmpty()) {
                result = result.resolve(segment)
            }
        }
        return result
    }

    /** Returns the base path joined with the given segments. */
    fun app(vararg segments: String): Path = join(langRoot.resolve("app"), segments)

    /** Returns the bootstrap path joined with the given segments. */
    fun bootstrap(vararg segments: String): Path = join(langRoot.resolve("bootstrap"), segments)

    /** Returns the routes path joined with the given segments. */
    fun routes(vararg segments: String): Path = join(langRoot.resolve("routes"), segments)

    /** Returns the database path joined with the given segments. */
    fun database(vararg segments: String): Path = join(langRoot.resolve("database"), segments)

    /** Executable config modules live under the language root (`src/main/kotlin/config`, …). */
    fun config(vararg segments: String): Path = join(langRoot.resolve("config"), segments)

    /** Returns the resources path joined with the given segments. */
    fun resource(vararg segments: String): Path = join(resources, segments)

    /** Returns the views path joined with the given segments. */
    fun view(vararg segments: String): Path = join(resources.resolve("views"), segments)

    /** Returns the public directory path joined with the given segments. */
    fun publicPath(vararg segments: String): Path = join(publicDir, segments)

    /** Returns the language root path joined with the given segments. */
    fun lang(vararg segments: String): Path = join(resources.resolve("lang"), segments)

    /** Returns the storage path joined with the given segments. */
    fun storage(vararg segments: String): Path = join(storage, segments)

    /** Returns the language root path joined with the given segments. */
    companion object {
        const val DEFAULT_RESOURCES = "src/main/resources"
        const val DEFAULT_STORAGE = "storage"
        const val DEFAULT_PUBLIC = "public"
        const val KOTLIN_ROOT = "src/main/kotlin"
        const val JAVA_ROOT = "src/main/java"

        @JvmStatic
        @JvmOverloads
        fun detect(base: Path, config: Config? = null): PathLayout {
            val langRootRel = config?.getString("paths.langRoot") ?: detectLangRoot(base)
            val resourcesRel = config?.getString("paths.resources") ?: DEFAULT_RESOURCES
            val storageRel = config?.getString("paths.storage") ?: DEFAULT_STORAGE
            val publicRel = config?.getString("paths.public") ?: DEFAULT_PUBLIC
            return PathLayout(
                base = base,
                langRoot = base.resolve(langRootRel).normalize(),
                resources = base.resolve(resourcesRel).normalize(),
                storage = base.resolve(storageRel).normalize(),
                publicDir = base.resolve(publicRel).normalize(),
            )
        }

        /** Prefer Java when any `.java` file exists under `src/main/java`; otherwise Kotlin. */
        @JvmStatic
        fun detectLangRoot(base: Path): String {
            val javaRoot = base.resolve(JAVA_ROOT)
            if (!Files.isDirectory(javaRoot)) {
                return KOTLIN_ROOT
            }
            val hasJava = Files.walk(javaRoot).use { stream ->
                stream.anyMatch { it.toString().endsWith(".java") }
            }
            return if (hasJava) JAVA_ROOT else KOTLIN_ROOT
        }
    }
}

/**
 * Facade for [Application] path helpers after boot (`Kona.app()`).
 */
object Paths {

    private fun app(): Application = Kona.app()

    @JvmStatic
    fun base(vararg segments: String): Path = app().basePath(*segments)

    @JvmStatic
    fun app(vararg segments: String): Path = app().appPath(*segments)

    @JvmStatic
    fun bootstrap(vararg segments: String): Path = app().bootstrapPath(*segments)

    @JvmStatic
    fun routes(vararg segments: String): Path = app().routesPath(*segments)

    @JvmStatic
    fun database(vararg segments: String): Path = app().databasePath(*segments)

    @JvmStatic
    fun config(vararg segments: String): Path = app().configPath(*segments)

    @JvmStatic
    fun resource(vararg segments: String): Path = app().resourcePath(*segments)

    @JvmStatic
    fun view(vararg segments: String): Path = app().viewPath(*segments)

    @JvmStatic
    fun publicDir(vararg segments: String): Path = app().publicPath(*segments)

    @JvmStatic
    fun lang(vararg segments: String): Path = app().langPath(*segments)

    @JvmStatic
    fun storage(vararg segments: String): Path = app().storagePath(*segments)
}
