package org.konaframework.grind

import java.nio.file.Files
import java.nio.file.Path

/**
 * `grind deploy:systemd` — build a production tree plus a systemd unit, EnvironmentFile, and
 * `install.sh`.
 *
 * Default output: `build/deploy/systemd/<app>/` (bin, lib, public, storage, unit, env, install.sh).
 * Flags: `--skip-build`, `--force`, `--output=`, `--prefix=`, `--user=`, `--group=`, `--host=`,
 * `--port=`, `--url=`, `--name=`, `--java-opts=`, `--base=`.
 */
class SystemdDeployCommand
@JvmOverloads
constructor(
    private val workingDirectory: Path = Path.of(".").toAbsolutePath().normalize(),
    private val processRunner: ProcessRunner = ProcessRunner.SYSTEM,
    private val windows: Boolean = System.getProperty("os.name").startsWith("Windows"),
) {
    fun run(args: List<String>): Int {
        val (_, options) = parseOptions(args)
        val io = GrindIO.fromOptions(options)
        if (options.containsKey("help")) {
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
        val rawName = options["name"] ?: SystemdPackager.readAppName(root)
        val appName = SystemdPackager.sanitizeUnitName(rawName)
        val fileEnv = loadDotEnv(root)
        val host = options["host"] ?: fileEnv["HOST"] ?: SystemdPackager.DEFAULT_HOST
        val port = SystemdPackager.parsePort(
            options["port"] ?: fileEnv["PORT"] ?: SystemdPackager.DEFAULT_PORT.toString(),
        )
        val url = options["url"] ?: fileEnv["APP_URL"] ?: "http://$host:$port"
        val user = options["user"] ?: SystemdPackager.DEFAULT_USER
        val group = options["group"] ?: user
        val javaOpts = options["java-opts"] ?: SystemdPackager.DEFAULT_JAVA_OPTS
        val prefix = options["prefix"] ?: "/var/www/$appName"
        val dest = resolveDest(root, options, appName)
        if (Files.exists(dest) && !options.containsKey("force")) {
            throw GrindException("Output already exists: $dest (pass --force to overwrite)")
        }
        if (!options.containsKey("skip-build")) {
            io.info("Running installDist…")
            val code = processRunner.run(gradleInstallDist(root), root, emptyMap())
            if (code != 0) {
                throw GrindException("installDist failed (exit $code)")
            }
        }
        if (options.containsKey("force")) {
            SystemdPackager.deleteRecursively(dest)
        }
        Files.createDirectories(dest)

        val install = SystemdPackager.resolveInstallDist(root, appName)
        if (install != null) {
            SystemdPackager.copyTree(install.resolve("bin"), dest.resolve("bin"))
            SystemdPackager.copyTree(install.resolve("lib"), dest.resolve("lib"))
        } else {
            io.warn("No build/install tree found. Wrote unit files only. Run without --skip-build, or ./gradlew installDist first.")
        }
        SystemdPackager.copyTree(root.resolve("public"), dest.resolve("public"))
        SystemdPackager.ensureStorage(dest)

        val startScript = SystemdPackager.unixStartScript(dest.resolve("bin"), appName)
        val envPath = "/etc/kona/$appName.env"
        val unitPath = "/etc/systemd/system/$appName.service"
        val env = SystemdPackager.overlayEnv(fileEnv, appName, host, port, url, javaOpts)
        Files.writeString(dest.resolve("$appName.env"), SystemdPackager.renderEnvFile(env))
        Files.writeString(
            dest.resolve("$appName.service"),
            SystemdPackager.renderUnit(appName, prefix, user, group, envPath, startScript, javaOpts),
        )
        val installSh = dest.resolve("install.sh")
        Files.writeString(installSh, SystemdPackager.renderInstallScript(appName, prefix, user, group, envPath, unitPath))
        SystemdPackager.markExecutable(installSh)
        Files.writeString(dest.resolve("README.txt"), SystemdPackager.renderReadme(appName, dest, prefix, envPath, unitPath))

        io.success("systemd bundle written to $dest")
        io.info("Review $appName.env, then on the server: sudo ./install.sh")
        io.outro("curl -fsS http://$host:$port/api/health")
        return 0
    }

    private fun loadDotEnv(root: Path): Map<String, String> {
        val envFile = root.resolve(".env")
        if (Files.isRegularFile(envFile)) {
            return ServePorts.readDotEnv(envFile)
        }
        val example = root.resolve(".env.example")
        if (Files.isRegularFile(example)) {
            return ServePorts.readDotEnv(example)
        }
        return emptyMap()
    }

    private fun resolveDest(root: Path, options: Map<String, String>, appName: String): Path {
        val raw = options["output"] ?: return root.resolve("build/deploy/systemd").resolve(appName)
        val path = Path.of(raw)
        return if (path.isAbsolute) path.normalize() else root.resolve(path).normalize()
    }

    private fun gradleInstallDist(root: Path): List<String> =
        GradleInvocation.command(root, "installDist", windows)

    private fun printHelp(io: GrindIO) {
        io.box(
            "grind deploy:systemd",
            listOf(
                "Build a production tree + systemd unit + env file + install.sh",
                "Default output: build/deploy/systemd/<app>/",
                "--skip-build     do not run ./gradlew installDist",
                "--force          overwrite the output directory",
                "--output=        bundle directory (default build/deploy/systemd/<app>)",
                "--prefix=        server install root (default /var/www/<app>)",
                "--user= --group= service user (default kona)",
                "--host= --port=  bind address written into the env file",
                "--url=           APP_URL",
                "--name=          unit / directory name",
                "--java-opts=     JAVA_OPTS on the unit (default G1 + MaxRAMPercentage=75)",
                "--base=          application root",
            ),
        )
    }
}
