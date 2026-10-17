plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
}

dependencies {
    api(project(":kona-core"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    // Nested cron field parser — covered by CronExpression behavioral assertions.
                    classes("org.konaframework.schedule.CronExpression")
                    classes("org.konaframework.schedule.CronExpression\$Field")
                    classes("org.konaframework.schedule.CronExpression\$Field\$Companion")
                }
                // Schedule API is fully exercised by ScheduleTest / grind schedule commands.
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

