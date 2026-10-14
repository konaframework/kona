plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":kona-core"))
    api(project(":kona-http"))
    api(project(":kona-extract"))
    api(project(":kona-mail"))
    api(project(":kona-queue"))
    api(project(":kona-notifications"))
    api(project(":kona-filesystem"))
    api(project(":kona-grind"))
    api(project(":kona-events"))
    api(project(":kona-cache"))
    api(project(":kona-broadcasting"))
    api(libs.junit.jupiter)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.h2)
}

kover {
    reports {
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "kona-testing"
        }
    }
}
