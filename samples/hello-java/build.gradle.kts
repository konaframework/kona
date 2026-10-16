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
    implementation(project(":kona-session"))
    implementation(project(":kona-auth"))
    implementation(project(":kona-queue"))
    implementation(project(":kona-schedule"))
    implementation(project(":kona-mail"))
    implementation(project(":kona-crema"))

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
