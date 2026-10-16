plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    application
    `maven-publish`
}

application {
    applicationName = "kona"
    mainClass.set("dev.kona.cli.KonaKt")
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

tasks.processResources {
    inputs.property("konaVersion", konaVersion)
    inputs.property("kotlinVersion", kotlinVersion)
    filesMatching("dev/kona/cli/kona.properties") {
        expand("konaVersion" to konaVersion, "kotlinVersion" to kotlinVersion)
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
        ":kona-session:publishToMavenLocal",
        ":kona-auth:publishToMavenLocal",
        ":kona-queue:publishToMavenLocal",
        ":kona-schedule:publishToMavenLocal",
        ":kona-mail:publishToMavenLocal",
        ":kona-grind:publishToMavenLocal",
    )
    systemProperty("kona.repoRoot", rootDir.absolutePath)
    // Generated kit apps resolve composite builds via KONA_HOME when present.
    environment("KONA_HOME", rootDir.absolutePath)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    classes("dev.kona.cli.KonaKt")
                    annotatedBy("dev.kona.support.KoverExclude")
                }
            }
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "kona-cli"
        }
    }
}
