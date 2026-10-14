plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("hello.MainKt")
}

dependencies {
    implementation(project(":kona-http"))
    implementation(project(":kona-extract"))
    implementation(project(":kona-session"))
    implementation(project(":kona-auth"))
    implementation(project(":kona-support"))
    implementation(project(":kona-redis"))
    implementation(project(":kona-cache"))
    implementation(project(":kona-crema"))
    implementation(project(":kona-events"))
    implementation(project(":kona-queue"))
    implementation(project(":kona-mail"))
    implementation(project(":kona-notifications"))
    implementation(project(":kona-filesystem"))
    implementation(project(":kona-drip"))
    implementation(project(":kona-process"))
    implementation(project(":kona-concurrency"))
    implementation(project(":kona-broadcasting"))
    implementation(project(":kona-search"))
    implementation(project(":kona-brew"))
    runtimeOnly(libs.h2)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
