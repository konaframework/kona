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
    api(project(":kona-cadence"))
    api(project(":kona-crema"))
    api(project(":kona-queue"))
    api(project(":kona-schedule"))
    api(project(":kona-support"))
    implementation(libs.logback.classic)
    implementation(libs.kotlin.scripting.jsr223)

    testImplementation(libs.h2)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    classes("dev.kona.grind.GrindKt")
                    // ScriptEngine wiring varies by JDK/classpath; fallback paths covered in GrindM8Test.
                    classes("dev.kona.grind.SteamCommand")
                    // Interactive prompt matrix — covered by GrindM8Test stream fixtures.
                    classes("dev.kona.grind.Prompts")
                    // ClassLoader resource enumeration — covered by GrindM8Test.
                    classes("dev.kona.grind.PackageDiscovery")
                    // Top-level command router — exercised by GrindTest / GrindM8Test suites.
                    classes("dev.kona.grind.GrindApplication")
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
            artifactId = "kona-grind"
        }
    }
}
