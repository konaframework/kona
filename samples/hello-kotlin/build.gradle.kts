plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("bootstrap.App")
}

dependencies {
    implementation(project(":kona-http"))
    implementation(project(":kona-grind"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
