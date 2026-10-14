plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":kona-core"))
    api(project(":kona-http"))
    api(project(":kona-support"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    // Template interpreter — exercised via CremaEngine behavioral suite.
                    classes("dev.kona.crema.CremaRuntime*")
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
            artifactId = "kona-crema"
        }
    }
}
