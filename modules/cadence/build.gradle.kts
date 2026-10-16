plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
    `maven-publish`
}

kotlin {
    compilerOptions {
        // Interface default methods (e.g. SoftDeletes) must be real JVM defaults for Java models.
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    api(project(":kona-core"))
    api(libs.slf4j.api)
    implementation(libs.hikaricp)
    implementation(libs.jackson.databind)

    testImplementation(libs.h2)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
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
            artifactId = "kona-cadence"
        }
    }
}
