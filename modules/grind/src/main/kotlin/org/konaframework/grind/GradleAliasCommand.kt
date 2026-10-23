package org.konaframework.grind

import java.nio.file.Path

/**
 * `grind gradle:build` / `gradle:install` / `gradle <tasks…>` — run the app's Gradle wrapper.
 *
 * Named aliases map to common tasks (`gradle:install` → `installDist`). Any other
 * `gradle:<task>` token is forwarded as that Gradle task. Extra flags (except grind's
 * `--base` / `--help` / verbosity) are passed through to Gradle.
 */
class GradleAliasCommand(
    private val token: String,
    private val gradleTasks: List<String>,
    private val workingDirectory: Path,
    private val processRunner: ProcessRunner,
    private val windows: Boolean,
) {
    fun run(args: List<String>): Int {
        val (positional, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        if (options.containsKey("help") || options.containsKey("h")) {
            printHelp(io)
            return 0
        }
        val start = if (options.containsKey("base")) {
            Path.of(options.getValue("base")).toAbsolutePath().normalize()
        } else {
            workingDirectory
        }
        val root = ServeCommand.findProjectRoot(start)
            ?: throw GrindException(
                "No Kona application found at $start (expected settings.gradle.kts or build.gradle.kts). " +
                    "Create one with: kona new",
            )
        val tasks = resolveTasks(positional)
        if (tasks.isEmpty()) {
            io.error("Usage: ./grind gradle <tasks…>   or   ./grind gradle:build")
            printHelp(io)
            return 1
        }
        val command = listOf(GradleInvocation.wrapper(root, windows)) + tasks + forwardedFlags(options)
        io.info("Running ${command.joinToString(" ")}")
        return processRunner.run(command, root, emptyMap())
    }

    private fun resolveTasks(positional: List<String>): List<String> {
        if (gradleTasks.isNotEmpty()) {
            return gradleTasks + positional
        }
        return positional
    }

    private fun forwardedFlags(options: Map<String, String>): List<String> =
        options
            .filterKeys { it !in GRIND_OWNED }
            .flatMap { (key, value) ->
                if (value == "true") listOf("--$key") else listOf("--$key=$value")
            }

    private fun printHelp(io: GrindIO) {
        io.box(
            "grind $token",
            listOf(
                "Run the Gradle wrapper in the application root",
                "./grind gradle:build          → ./gradlew build",
                "./grind gradle:install        → ./gradlew installDist",
                "./grind gradle:test           → ./gradlew test",
                "./grind gradle:check          → ./gradlew check",
                "./grind gradle:classes        → ./gradlew classes",
                "./grind gradle:clean          → ./gradlew clean",
                "./grind gradle:run            → ./gradlew run",
                "./grind gradle <tasks…>       → ./gradlew <tasks…>",
                "Extra flags (except --base / --help / -v / -q) are forwarded to Gradle.",
            ),
        )
    }

    companion object {
        private val GRIND_OWNED = setOf("base", "help", "h", "verbose", "quiet")

        /** Well-known `gradle:*` names shown in `./grind list`. */
        val ALIASES: Map<String, List<String>> =
            linkedMapOf(
                "gradle:build" to listOf("build"),
                "gradle:test" to listOf("test"),
                "gradle:check" to listOf("check"),
                "gradle:classes" to listOf("classes"),
                "gradle:clean" to listOf("clean"),
                "gradle:run" to listOf("run"),
                "gradle:install" to listOf("installDist"),
                "gradle:installDist" to listOf("installDist"),
                "gradle" to emptyList(),
            )

        @JvmStatic
        fun isGradleToken(command: String): Boolean =
            command == "gradle" || command.startsWith("gradle:")

        @JvmStatic
        @JvmOverloads
        fun fromToken(
            token: String,
            workingDirectory: Path = Path.of(".").toAbsolutePath().normalize(),
            processRunner: ProcessRunner = ProcessRunner.SYSTEM,
            windows: Boolean = System.getProperty("os.name").startsWith("Windows"),
        ): GradleAliasCommand =
            GradleAliasCommand(token, tasksFor(token), workingDirectory, processRunner, windows)

        @JvmStatic
        fun tasksFor(token: String): List<String> {
            ALIASES[token]?.let { return it }
            if (!token.startsWith("gradle:") || token.length <= "gradle:".length) {
                return emptyList()
            }
            return listOf(token.substring("gradle:".length))
        }

        @JvmField
        val LIST_ENTRIES: List<Pair<String, String>> = listOf(
            "gradle:build" to "Run ./gradlew build",
            "gradle:install" to "Run ./gradlew installDist",
            "gradle:test" to "Run ./gradlew test",
            "gradle:check" to "Run ./gradlew check",
            "gradle:classes" to "Run ./gradlew classes",
            "gradle:clean" to "Run ./gradlew clean",
            "gradle:run" to "Run ./gradlew run",
            "gradle" to "Run ./gradlew with the given tasks",
        )
    }
}
