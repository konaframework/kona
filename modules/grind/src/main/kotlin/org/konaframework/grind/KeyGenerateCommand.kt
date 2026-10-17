package org.konaframework.grind

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Base64

/**
 * `grind key:generate` — set `APP_KEY` in the application `.env` (Laravel `key:generate` shape).
 *
 * Flags:
 * - `--show` — print a new key and exit without writing files
 * - `--force` — allow overwriting an existing key when `APP_ENV=production`
 * - `--base=` — start directory (walks up to the Gradle project root)
 */
class KeyGenerateCommand
@JvmOverloads
constructor(
    private val workingDirectory: Path = Path.of(".").toAbsolutePath().normalize(),
    private val prompts: Prompts = Prompts(),
    private val environment: Map<String, String> = System.getenv(),
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        val key = generateAppKey()
        if (options.containsKey("show")) {
            io.line(key)
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
                    "Create one with: kona new <name>",
            )
        val envFile = ensureEnvFile(root, io)
        val currentKey = ServePorts.readDotEnv(envFile)["APP_KEY"].orEmpty()
        if (currentKey.isNotEmpty() && !confirmOverwrite(envFile, options, io)) {
            return 1
        }
        if (!writeKey(envFile, key, io)) {
            return 1
        }
        io.success("Application key set successfully.")
        return 0
    }

    private fun confirmOverwrite(
        envFile: Path,
        options: Map<String, String>,
        io: GrindIO,
    ): Boolean {
        if (options.containsKey("force")) {
            return true
        }
        if (!isProduction(envFile)) {
            return true
        }
        io.warn("Application In Production!")
        val confirmed = prompts.confirm("Are you sure you want to run this command?", default = false)
        if (!confirmed) {
            io.warn("Command cancelled.")
            return false
        }
        return true
    }

    private fun isProduction(envFile: Path): Boolean {
        environment["APP_ENV"]?.let { return it.equals("production", ignoreCase = true) }
        return ServePorts.readDotEnv(envFile)["APP_ENV"].equals("production", ignoreCase = true)
    }

    private fun ensureEnvFile(root: Path, io: GrindIO): Path {
        val envFile = root.resolve(".env")
        if (Files.isRegularFile(envFile)) {
            return envFile
        }
        val example = root.resolve(".env.example")
        if (Files.isRegularFile(example)) {
            Files.copy(example, envFile, StandardCopyOption.COPY_ATTRIBUTES)
            io.info("Created .env from .env.example")
            return envFile
        }
        throw GrindException(
            "Unable to set application key. No .env file found (and no .env.example to copy).",
        )
    }

    private fun writeKey(envFile: Path, key: String, io: GrindIO): Boolean {
        val input = Files.readString(envFile)
        val replaced = KEY_LINE.replaceFirst(input, "APP_KEY=$key")
        if (replaced == input) {
            io.error("Unable to set application key. No APP_KEY variable was found in the .env file.")
            return false
        }
        Files.writeString(envFile, replaced)
        return true
    }

    companion object {
        private const val KEY_BYTES = 32

        /** Matches a dotenv `APP_KEY=…` assignment line. */
        private val KEY_LINE = Regex("""(?m)^APP_KEY=.*$""")

        /** 32-byte random key in `base64:…` form (same as `kona new` / [org.konaframework.security.Encrypter.generateKey]). */
        @JvmStatic
        fun generateAppKey(): String {
            val bytes = ByteArray(KEY_BYTES)
            SecureRandom().nextBytes(bytes)
            return "base64:" + Base64.getEncoder().encodeToString(bytes)
        }

    }
}
