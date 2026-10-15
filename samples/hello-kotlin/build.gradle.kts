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
    implementation(project(":kona-extract"))
    implementation(project(":kona-redis"))
    implementation(project(":kona-session"))
    implementation(project(":kona-auth"))
    implementation(project(":kona-queue"))
    implementation(project(":kona-schedule"))
    implementation(project(":kona-mail"))
    implementation(project(":kona-crema"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
