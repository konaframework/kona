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
    api(project(":kona-extract"))
    api(project(":kona-crema"))
    api(project(":kona-queue"))
    implementation(libs.logback.classic)

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
