plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    application
    `maven-publish`
}

application {
    mainClass.set("dev.kona.grind.GrindKt")
}

dependencies {
    api(project(":kona-http"))
    implementation(libs.logback.classic)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val konaVersion = project.version.toString()
val kotlinVersion = libs.versions.kotlin.get()

tasks.processResources {
    inputs.property("konaVersion", konaVersion)
    inputs.property("kotlinVersion", kotlinVersion)
    filesMatching("dev/kona/grind/grind.properties") {
        expand("konaVersion" to konaVersion, "kotlinVersion" to kotlinVersion)
    }
    // Generated apps ship the same Gradle wrapper as Kona itself.
    from(rootProject.file("gradle/wrapper")) {
        into("skeletons/common/gradle/wrapper")
    }
    from(listOf(rootProject.file("gradlew"), rootProject.file("gradlew.bat"))) {
        into("skeletons/common")
    }
}

tasks.test {
    // GeneratedAppBuildTest compiles `grind new` output against these artifacts via mavenLocal().
    dependsOn(":kona-core:publishToMavenLocal", ":kona-http:publishToMavenLocal")
    systemProperty("kona.repoRoot", rootDir.absolutePath)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    // process entrypoint calls exitProcess — covered via GrindApplication
                    classes("dev.kona.grind.GrindKt")
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
            artifactId = "kona-grind"
        }
    }
}
