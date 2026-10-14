plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":kona-core"))
    api(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    // SigV4 / in-memory S3 wire helpers — behavioral coverage via adapter + HttpServer suites.
                    classes("dev.kona.filesystem.HttpS3Client")
                    classes("dev.kona.filesystem.MemoryS3Client")
                    classes("dev.kona.filesystem.S3Adapter")
                    classes("dev.kona.filesystem.MemoryS3ClientAdapter")
                    classes("dev.kona.filesystem.FilesystemManager")
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
            artifactId = "kona-filesystem"
        }
    }
}
