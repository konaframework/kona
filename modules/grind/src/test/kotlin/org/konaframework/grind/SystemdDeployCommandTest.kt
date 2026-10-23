package org.konaframework.grind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class SystemdDeployCommandTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun helpDoesNotNeedAProject() {
        val out = capture {
            assertEquals(0, SystemdDeployCommand().run(listOf("--help")))
        }
        assertTrue(out.contains("grind deploy:systemd"))
        assertTrue(out.contains("--skip-build"))
        assertTrue(out.contains("/var/www"))
    }

    @Test
    fun missingProjectThrows() {
        val orphan = temp.resolve("orphan")
        Files.createDirectories(orphan)
        val ex = assertThrows(GrindException::class.java) {
            SystemdDeployCommand().run(listOf("--base=$orphan", "--skip-build"))
        }
        assertTrue(ex.message!!.contains("No Kona application found"))
    }

    @Test
    fun skipBuildWritesUnitEnvAndInstallScript() {
        val root = project("shop")
        Files.writeString(
            root.resolve(".env"),
            """
            APP_ENV=local
            APP_DEBUG=true
            APP_KEY=base64:secret
            HOST=0.0.0.0
            PORT=4010
            APP_URL=http://127.0.0.1:4010
            DB_URL=jdbc:postgresql://127.0.0.1:5432/shop
            CUSTOM_FLAG=keep-me
            QUOTED=value with spaces
            """.trimIndent() + "\n",
        )
        assertEquals(0, command().run(listOf("--base=$root", "--skip-build")))
        val dest = root.resolve("build/deploy/systemd/shop")
        assertTrue(Files.isRegularFile(dest.resolve("install.sh")))
        assertTrue(Files.isRegularFile(dest.resolve("shop.service")))
        assertTrue(Files.isRegularFile(dest.resolve("shop.env")))
        assertTrue(Files.isDirectory(dest.resolve("storage/logs")))
        assertTrue(Files.isDirectory(dest.resolve("storage/framework")))
        assertTrue(Files.isDirectory(dest.resolve("storage/app/public")))
        assertTrue(Files.isDirectory(dest.resolve("storage/database")))
        val env = Files.readString(dest.resolve("shop.env"))
        assertTrue(env.contains("APP_ENV=production"))
        assertTrue(env.contains("APP_DEBUG=false"))
        assertTrue(env.contains("APP_KEY=base64:secret"))
        assertTrue(env.contains("HOST=0.0.0.0"))
        assertTrue(env.contains("PORT=4010"))
        assertTrue(env.contains("DB_URL=jdbc:postgresql://127.0.0.1:5432/shop"))
        assertTrue(env.contains("CUSTOM_FLAG=keep-me"))
        assertTrue(env.contains("QUOTED=\"value with spaces\""))
        assertFalse(env.contains("APP_KEY is empty"))
        val unit = Files.readString(dest.resolve("shop.service"))
        assertTrue(unit.contains("WorkingDirectory=/var/www/shop"))
        assertTrue(unit.contains("EnvironmentFile=/etc/kona/shop.env"))
        assertTrue(unit.contains("ExecStart=/var/www/shop/bin/shop --base=/var/www/shop"))
        assertTrue(unit.contains("User=kona"))
        assertTrue(unit.contains("LimitNOFILE=65535"))
        val install = Files.readString(dest.resolve("install.sh"))
        assertTrue(install.startsWith("#!/usr/bin/env bash"))
        assertTrue(install.contains("systemctl enable --now shop.service"))
        assertTrue(install.contains("Keeping existing"))
        val readme = Files.readString(dest.resolve("README.txt"))
        assertTrue(readme.contains("/var/www/shop"))
        assertTrue(readme.contains("./grind migrate"))
        val perms = Files.getPosixFilePermissions(dest.resolve("install.sh"))
        assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE))
        assertTrue(ListCommand.BUILTIN.any { it.first == "deploy:systemd" })
    }

    @Test
    fun refusesExistingOutputWithoutForce() {
        val root = project("exists")
        val dest = root.resolve("build/deploy/systemd/exists")
        Files.createDirectories(dest)
        Files.writeString(dest.resolve("stale"), "old")
        val ex = assertThrows(GrindException::class.java) {
            command().run(listOf("--base=$root", "--skip-build"))
        }
        assertTrue(ex.message!!.contains("--force"))
        assertEquals("old", Files.readString(dest.resolve("stale")))
    }

    @Test
    fun forceOverwritesExistingBundle() {
        val root = project("forced")
        val dest = root.resolve("build/deploy/systemd/forced")
        Files.createDirectories(dest)
        Files.writeString(dest.resolve("stale"), "old")
        assertEquals(0, command().run(listOf("--base=$root", "--skip-build", "--force")))
        assertFalse(Files.exists(dest.resolve("stale")))
        assertTrue(Files.isRegularFile(dest.resolve("forced.service")))
    }

    @Test
    fun copiesInstallDistPublicAndSkipsHotAndSymlinks() {
        val root = project("pack")
        val install = root.resolve("build/install/pack")
        Files.createDirectories(install.resolve("bin"))
        Files.createDirectories(install.resolve("lib/nested"))
        Files.writeString(install.resolve("bin/pack"), "#!/bin/sh\n")
        Files.writeString(install.resolve("lib/nested/app.jar"), "jar")
        Files.createDirectories(root.resolve("public"))
        Files.writeString(root.resolve("public/index.html"), "<h1>ok</h1>")
        Files.writeString(root.resolve("public/hot"), "http://127.0.0.1:5173")
        Files.createDirectories(root.resolve("storage/app/public"))
        Files.createSymbolicLink(root.resolve("public/storage"), root.resolve("storage/app/public"))
        assertEquals(0, command().run(listOf("--base=$root", "--skip-build")))
        val dest = root.resolve("build/deploy/systemd/pack")
        assertEquals("#!/bin/sh\n", Files.readString(dest.resolve("bin/pack")))
        assertEquals("jar", Files.readString(dest.resolve("lib/nested/app.jar")))
        assertEquals("<h1>ok</h1>", Files.readString(dest.resolve("public/index.html")))
        assertFalse(Files.exists(dest.resolve("public/hot")))
        assertFalse(Files.exists(dest.resolve("public/storage")))
        val unit = Files.readString(dest.resolve("pack.service"))
        assertTrue(unit.contains("ExecStart=/var/www/pack/bin/pack --base=/var/www/pack"))
    }

    @Test
    fun discoversNonMatchingInstallFolderAndStartScript() {
        val root = project("alias")
        val install = root.resolve("build/install/other-name")
        Files.createDirectories(install.resolve("bin"))
        Files.writeString(install.resolve("bin/other-name"), "#!/bin/sh\n")
        Files.writeString(install.resolve("bin/other-name.bat"), "@echo off\n")
        assertEquals(0, command().run(listOf("--base=$root", "--skip-build")))
        val dest = root.resolve("build/deploy/systemd/alias")
        assertTrue(Files.isRegularFile(dest.resolve("bin/other-name")))
        val unit = Files.readString(dest.resolve("alias.service"))
        assertTrue(unit.contains("bin/other-name --base=/var/www/alias"))
    }

    @Test
    fun warnsWhenInstallTreeMissing() {
        val root = project("empty")
        val out = capture {
            assertEquals(0, command().run(listOf("--base=$root", "--skip-build")))
        }
        assertTrue(out.contains("No build/install tree found"))
        assertTrue(out.contains("systemd bundle written"))
    }

    @Test
    fun gradleSuccessThenBundle() {
        val root = project("built")
        var seen: List<String>? = null
        val runner = ProcessRunner { command, dir, _ ->
            seen = command
            assertEquals(root, dir)
            0
        }
        assertEquals(
            0,
            SystemdDeployCommand(root, runner, windows = false)
                .run(listOf("--base=$root")),
        )
        assertNotNull(seen)
        assertTrue(seen!!.contains("installDist"))
        assertFalse(seen!!.any { it.endsWith("gradlew.bat") })
        assertTrue(Files.isRegularFile(root.resolve("build/deploy/systemd/built/built.service")))
    }

    @Test
    fun gradleFailureStopsBeforeWritingBundle() {
        val root = project("fail")
        val runner = ProcessRunner { _, _, _ -> 7 }
        val ex = assertThrows(GrindException::class.java) {
            SystemdDeployCommand(root, runner, windows = false).run(listOf("--base=$root"))
        }
        assertTrue(ex.message!!.contains("exit 7"))
        assertFalse(Files.exists(root.resolve("build/deploy/systemd/fail/fail.service")))
    }

    @Test
    fun windowsWrapperIsGradlewBat() {
        val root = project("win")
        Files.writeString(root.resolve("gradlew.bat"), "@echo off\n")
        var seen: List<String>? = null
        val runner = ProcessRunner { command, _, _ ->
            seen = command
            0
        }
        assertEquals(0, SystemdDeployCommand(root, runner, windows = true).run(listOf("--base=$root")))
        assertEquals(root.resolve("gradlew.bat").toString(), seen!!.first())
    }

    @Test
    fun usesGradleWhenWrapperMissing() {
        val root = project("nowrap")
        Files.deleteIfExists(root.resolve("gradlew"))
        var seen: List<String>? = null
        val runner = ProcessRunner { command, _, _ ->
            seen = command
            0
        }
        assertEquals(0, SystemdDeployCommand(root, runner, windows = false).run(listOf("--base=$root")))
        assertEquals("gradle", seen!!.first())
    }

    @Test
    fun customFlagsAndAbsoluteOutput() {
        val root = project("flags")
        val out = temp.resolve("bundle-out")
        assertEquals(
            0,
            command().run(
                listOf(
                    "--base=$root",
                    "--skip-build",
                    "--output=$out",
                    "--prefix=/opt/flags",
                    "--user=www",
                    "--group=web",
                    "--host=127.0.0.1",
                    "--port=8080",
                    "--url=https://app.example.com",
                    "--java-opts=-Xmx1g",
                    "--name=Flags App",
                ),
            ),
        )
        assertTrue(Files.isRegularFile(out.resolve("flags-app.service")))
        val unit = Files.readString(out.resolve("flags-app.service"))
        assertTrue(unit.contains("User=www"))
        assertTrue(unit.contains("Group=web"))
        assertTrue(unit.contains("WorkingDirectory=/opt/flags"))
        assertTrue(unit.contains("Environment=JAVA_OPTS=-Xmx1g"))
        val env = Files.readString(out.resolve("flags-app.env"))
        assertTrue(env.contains("APP_URL=https://app.example.com"))
        assertTrue(env.contains("PORT=8080"))
        assertTrue(env.contains("HOST=127.0.0.1"))
        assertTrue(env.contains("JAVA_OPTS=-Xmx1g"))
    }

    @Test
    fun relativeOutputAndEnvExampleFallback() {
        val root = project("example")
        Files.writeString(
            root.resolve(".env.example"),
            "APP_KEY=\nHOST=127.0.0.1\nPORT=4000\nHASHED=value # comment\n",
        )
        assertEquals(
            0,
            command().run(listOf("--base=$root", "--skip-build", "--output=dist/systemd")),
        )
        val dest = root.resolve("dist/systemd")
        val env = Files.readString(dest.resolve("example.env"))
        assertTrue(env.contains("APP_KEY is empty"))
        assertTrue(env.contains("APP_KEY="))
        assertTrue(env.contains("HOST=127.0.0.1"))
    }

    @Test
    fun settingsGradleSingleQuotesAndWorkingDirectory() {
        val root = temp.resolve("from-cwd")
        Files.createDirectories(root)
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'from-cwd'\n")
        assertEquals(0, SystemdDeployCommand(workingDirectory = root, processRunner = { _, _, _ -> 0 }).run(listOf("--skip-build")))
        assertTrue(Files.isRegularFile(root.resolve("build/deploy/systemd/from-cwd/from-cwd.service")))
    }

    @Test
    fun unparseableSettingsUsesDirectoryName() {
        val root = temp.resolve("dirname-app")
        Files.createDirectories(root)
        Files.writeString(root.resolve("settings.gradle.kts"), "plugins {}\n")
        assertEquals(0, command().run(listOf("--base=$root", "--skip-build")))
        assertTrue(Files.isRegularFile(root.resolve("build/deploy/systemd/dirname-app/dirname-app.service")))
    }

    @Test
    fun settingsGradleFillsWhenKtsHasNoName() {
        val root = temp.resolve("from-groovy")
        Files.createDirectories(root)
        Files.writeString(root.resolve("settings.gradle.kts"), "plugins {}\n")
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'from-groovy'\n")
        assertEquals(0, command().run(listOf("--base=$root", "--skip-build")))
        assertTrue(Files.isRegularFile(root.resolve("build/deploy/systemd/from-groovy/from-groovy.service")))
    }

    @Test
    fun invalidNameAndPort() {
        val root = project("bad")
        val nameEx = assertThrows(GrindException::class.java) {
            command().run(listOf("--base=$root", "--skip-build", "--name=???"))
        }
        assertTrue(nameEx.message!!.contains("Invalid application name"))
        val portEx = assertThrows(GrindException::class.java) {
            command().run(listOf("--base=$root", "--skip-build", "--port=nope"))
        }
        assertTrue(portEx.message!!.contains("Invalid PORT"))
        val rangeEx = assertThrows(GrindException::class.java) {
            command().run(listOf("--base=$root", "--skip-build", "--port=0"))
        }
        assertTrue(rangeEx.message!!.contains("Invalid PORT"))
        val highEx = assertThrows(GrindException::class.java) {
            command().run(listOf("--base=$root", "--skip-build", "--port=70000"))
        }
        assertTrue(highEx.message!!.contains("Invalid PORT"))
    }

    @Test
    fun wiredThroughGrindApplication() {
        val root = project("wired")
        assertEquals(0, GrindApplication().run(arrayOf("deploy:systemd", "--base=$root", "--skip-build")))
        assertTrue(Files.isRegularFile(root.resolve("build/deploy/systemd/wired/wired.service")))
        val listed = capture { GrindApplication().run(arrayOf("list")) }
        assertTrue(listed.contains("deploy:systemd"))
    }

    @Test
    fun packagerHelpersCoverEdges() {
        assertNull(SystemdPackager.parseRootProjectName("plugins {}"))
        assertNull(SystemdPackager.parseRootProjectName("rootProject.name = \"   \""))
        assertEquals("shop", SystemdPackager.parseRootProjectName("rootProject.name = \"shop\""))
        assertEquals("", SystemdPackager.quoteEnv(""))
        assertEquals("plain", SystemdPackager.quoteEnv("plain"))
        assertTrue(SystemdPackager.envCharNeedsQuote(' '))
        assertTrue(SystemdPackager.envCharNeedsQuote('\t'))
        assertTrue(SystemdPackager.envCharNeedsQuote('#'))
        assertTrue(SystemdPackager.envCharNeedsQuote('"'))
        assertTrue(SystemdPackager.envCharNeedsQuote('\''))
        assertTrue(SystemdPackager.envCharNeedsQuote('='))
        assertFalse(SystemdPackager.envCharNeedsQuote('a'))
        assertEquals("\"a=b\"", SystemdPackager.quoteEnv("a=b"))
        assertEquals("\"say \\\"hi\\\"\"", SystemdPackager.quoteEnv("say \"hi\""))
        assertEquals("\"a # b\"", SystemdPackager.quoteEnv("a # b"))
        assertEquals("\"a#b\"", SystemdPackager.quoteEnv("a#b"))
        assertEquals("\"a'b\"", SystemdPackager.quoteEnv("a'b"))
        assertEquals("\"a\\\\b c\"", SystemdPackager.quoteEnv("a\\b c"))
        assertEquals("hello-kona", SystemdPackager.sanitizeUnitName("Hello Kona"))
        assertEquals("my.app", SystemdPackager.sanitizeUnitName(".My.App."))
        assertEquals("x", SystemdPackager.sanitizeUnitName("-x-"))
        val env = SystemdPackager.overlayEnv(
            mapOf("Z_LAST" to "1", "APP_NAME" to "keep"),
            "shop",
            "127.0.0.1",
            4000,
            "http://127.0.0.1:4000",
            SystemdPackager.DEFAULT_JAVA_OPTS,
        )
        assertEquals("keep", env["APP_NAME"])
        assertEquals("1", env["Z_LAST"])
        assertEquals("production", env["APP_ENV"])

        assertEquals("app", SystemdPackager.unixStartScript(temp.resolve("missing-bin"), "app"))
        val emptyBin = temp.resolve("empty-bin")
        Files.createDirectories(emptyBin)
        assertEquals("app", SystemdPackager.unixStartScript(emptyBin, "app"))
        val mixedBin = temp.resolve("mixed-bin")
        Files.createDirectories(mixedBin)
        Files.writeString(mixedBin.resolve(".hidden"), "x")
        Files.writeString(mixedBin.resolve("runner.bat"), "@echo off\n")
        Files.writeString(mixedBin.resolve("runner"), "#!/bin/sh\n")
        assertEquals("runner", SystemdPackager.unixStartScript(mixedBin, "missing"))

        assertNull(SystemdPackager.resolveInstallDist(temp.resolve("no-install"), "x"))
        val emptyInstall = temp.resolve("empty-install")
        Files.createDirectories(emptyInstall.resolve("build/install"))
        assertNull(SystemdPackager.resolveInstallDist(emptyInstall, "x"))

        SystemdPackager.copyTree(temp.resolve("missing-tree"), temp.resolve("dest-tree"))
        assertFalse(Files.exists(temp.resolve("dest-tree")))
        SystemdPackager.deleteRecursively(temp.resolve("missing-delete"))

        val probe = temp.resolve("posix-probe")
        Files.writeString(probe, "x")
        assertTrue(SystemdPackager.posixView(probe))
        assertFalse(SystemdPackager.posixView(temp.resolve("does-not-exist")))
        val skipExec = temp.resolve("not-exec.sh")
        Files.writeString(skipExec, "#!/bin/sh\n")
        SystemdPackager.markExecutable(skipExec, posix = false)
        assertFalse(
            Files.getPosixFilePermissions(skipExec).contains(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE),
        )
        SystemdPackager.markExecutable(skipExec, posix = true)
        assertTrue(
            Files.getPosixFilePermissions(skipExec).contains(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE),
        )

        val unit = SystemdPackager.renderUnit(
            "shop",
            "/var/www/shop",
            "kona",
            "kona",
            "/etc/kona/shop.env",
            "shop",
            SystemdPackager.DEFAULT_JAVA_OPTS,
        )
        assertTrue(unit.contains("Description=Kona shop"))
        val script = SystemdPackager.renderInstallScript(
            "shop",
            "/var/www/shop",
            "kona",
            "kona",
            "/etc/kona/shop.env",
            "/etc/systemd/system/shop.service",
        )
        assertTrue(script.contains("PREFIX=\"\${PREFIX:-/var/www/shop}\""))
        assertTrue(Files.getPosixFilePermissions(probe) != PosixFilePermissions.fromString("---------"))
    }

    @Test
    fun forceOnMissingOutputStillWrites() {
        val root = project("fresh-force")
        assertEquals(0, command().run(listOf("--base=$root", "--skip-build", "--force")))
        assertTrue(Files.isRegularFile(root.resolve("build/deploy/systemd/fresh-force/fresh-force.service")))
    }

    @Test
    fun groupDefaultsToUser() {
        val root = project("usergroup")
        assertEquals(
            0,
            command().run(listOf("--base=$root", "--skip-build", "--user=deploy")),
        )
        val unit = Files.readString(root.resolve("build/deploy/systemd/usergroup/usergroup.service"))
        assertTrue(unit.contains("User=deploy"))
        assertTrue(unit.contains("Group=deploy"))
    }

    private fun command() = SystemdDeployCommand(processRunner = { _, _, _ -> 0 })

    private fun project(name: String): Path {
        val root = temp.resolve(name)
        Files.createDirectories(root)
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"$name\"\n")
        Files.writeString(root.resolve("gradlew"), "#!/bin/sh\n")
        return root
    }

    private fun capture(block: () -> Unit): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        try {
            block()
            return buffer.toString()
        } finally {
            System.setOut(original)
        }
    }
}
