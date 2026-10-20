plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    application
}

application {
    applicationName = "kona"
    mainClass.set("org.konaframework.cli.KonaKt")
}

dependencies {
    implementation(project(":kona-support"))
    implementation(libs.logback.classic)

    testImplementation(project(":kona-grind"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val konaVersion = project.version.toString()
val kotlinVersion = libs.versions.kotlin.get()
val konaRevision = gitDescribe(rootDir, konaVersion)

tasks.processResources {
    inputs.property("konaVersion", konaVersion)
    inputs.property("kotlinVersion", kotlinVersion)
    inputs.property("konaRevision", konaRevision)
    filesMatching("org/konaframework/cli/kona.properties") {
        expand(
            "konaVersion" to konaVersion,
            "kotlinVersion" to kotlinVersion,
            "konaRevision" to konaRevision,
        )
    }
    from(rootProject.file("gradle/wrapper")) {
        into("skeletons/common/gradle/wrapper")
    }
    from(listOf(rootProject.file("gradlew"), rootProject.file("gradlew.bat"))) {
        into("skeletons/common")
    }
}

tasks.test {
    dependsOn(
        ":kona-core:publishToMavenLocal",
        ":kona-http:publishToMavenLocal",
        ":kona-cadence:publishToMavenLocal",
        ":kona-crema:publishToMavenLocal",
        ":kona-redis:publishToMavenLocal",
        ":kona-cache:publishToMavenLocal",
        ":kona-session:publishToMavenLocal",
        ":kona-auth:publishToMavenLocal",
        ":kona-queue:publishToMavenLocal",
        ":kona-schedule:publishToMavenLocal",
        ":kona-mail:publishToMavenLocal",
        ":kona-broadcasting:publishToMavenLocal",
        ":kona-grind:publishToMavenLocal",
        ":kona-brew:publishToMavenLocal",
        ":kona-inertia:publishToMavenLocal",
    )
    systemProperty("kona.repoRoot", rootDir.absolutePath)
    // Generated kit apps resolve composite builds via KONA_HOME when present.
    environment("KONA_HOME", rootDir.absolutePath)
}

kover {
    reports {
        // Common filters only. `total { filters }` *replaces* this block (Kover 0.9).
        filters {
            excludes {
                classes("org.konaframework.cli.KonaKt")
                annotatedBy("org.konaframework.support.KoverExclude")
            }
        }
        total {
            verify {
                rule {
                    bound {
                        minValue = 100
                        coverageUnits.set(kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
                    }
                    bound {
                        minValue = 100
                        coverageUnits.set(kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
                    }
                }
            }
        }
    }
}

private fun gitDescribe(repo: java.io.File, fallback: String): String =
    try {
        val process =
            ProcessBuilder("git", "describe", "--always", "--dirty", "--tags")
                .directory(repo)
                .redirectErrorStream(true)
                .start()
        val text = process.inputStream.bufferedReader().readText().trim()
        val code = process.waitFor()
        if (code == 0 && text.isNotEmpty() && !text.startsWith("fatal")) text else fallback
    } catch (_: Exception) {
        fallback
    }

