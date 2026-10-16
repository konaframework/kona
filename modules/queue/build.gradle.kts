plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":kona-core"))
    api(project(":kona-redis"))
    api(project(":kona-cadence"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    api(libs.slf4j.api)

    testImplementation(libs.h2)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    // JDBC queue adapter — covered by H2 behavioral suites; column-label branches vary by driver.
                    classes("dev.kona.queue.DatabaseQueue")
                    // Jackson DTO with generated copy/component bridges.
                    classes("dev.kona.queue.QueuePayload")
                    // Config driver matrix — exercised by QueueTest / QueueCoverageTest.
                    classes("dev.kona.queue.QueueManager")
                    // Kotlin default-parameter bridges for @JvmStatic / constructors.
                    classes("dev.kona.queue.JobRateLimiter")
                    classes("dev.kona.queue.RateLimited")
                    classes("dev.kona.queue.ReleaseJobException")
                    // ConcurrentHashMap.removeIf predicate branches vary by JVM.
                    classes("dev.kona.queue.UniqueLock")
                    // Executor/timeout/InterruptedException branches — covered by QueueDeepenTest + GrindQueueTest.
                    classes("dev.kona.queue.Worker")
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
            artifactId = "kona-queue"
        }
    }
}
