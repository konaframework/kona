plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
}

dependencies {
    api(project(":kona-core"))
    api(project(":kona-http"))
    api(project(":kona-support"))
    api(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        // Common filters only. `total { filters }` replaces this block on Kover 0.9.
        filters {
            excludes {
                // Template interpreter — exercised via CremaEngine behavioral suite.
                classes("org.konaframework.crema.CremaRuntime*")
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

