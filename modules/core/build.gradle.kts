plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
}

dependencies {
    api(libs.slf4j.api)
    implementation(libs.logback.classic)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        // Common filters only. `total { filters }` replaces this block on Kover 0.9.
        filters {
            excludes {
                classes("org.konaframework.core.internal.*")
                annotatedBy("org.konaframework.core.KoverExclude")
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

