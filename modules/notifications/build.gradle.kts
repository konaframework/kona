plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":kona-core"))
    api(project(":kona-mail"))
    api(project(":kona-cadence"))
    api(project(":kona-queue"))
    api(libs.slf4j.api)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.h2)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    // Data-class / companion factory closing-brace instrumentation noise (behavior covered in DurableNotificationTest).
                    classes("dev.kona.notifications.NotificationRecord")
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
            artifactId = "kona-notifications"
        }
    }
}
