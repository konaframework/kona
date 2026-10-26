plugins {
    java
    application
}

application {
    mainClass.set("bootstrap.App")
}

dependencies {
    implementation(project(":kona-http"))
    implementation(project(":kona-grind"))
    implementation(project(":kona-cadence"))
    implementation(project(":kona-redis"))
    implementation(project(":kona-cache"))
    implementation(project(":kona-session"))
    implementation(project(":kona-auth"))
    implementation(project(":kona-queue"))
    implementation(project(":kona-schedule"))
    implementation(project(":kona-mail"))
    implementation(project(":kona-broadcasting"))
    implementation(project(":kona-crema"))
    implementation(project(":kona-firefly"))
    // Matches default H2 URL in config/Database (zero-install local demos).
    runtimeOnly(libs.h2)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}
