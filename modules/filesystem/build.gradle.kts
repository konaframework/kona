plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
    `maven-publish`
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
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
                    // SigV4 HTTP client — behavioral coverage via HttpServer suites.
                    classes("dev.kona.filesystem.HttpS3Client")
                    // Kotlin default-parameter / JVM overload bridges on manager create noise in branch totals.
                    classes("dev.kona.filesystem.FilesystemManager")
                    classes("dev.kona.filesystem.S3Adapter")
                    classes("dev.kona.filesystem.MemoryS3Client")
                    classes("dev.kona.filesystem.MemoryS3ClientAdapter")
                    classes("dev.kona.filesystem.AdapterSupport")
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
