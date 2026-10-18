plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("bootstrap.App")
}

dependencies {
    implementation(project(":kona-brew"))
    implementation(project(":kona-http"))
    implementation(project(":kona-session"))
    implementation(project(":kona-crema"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
