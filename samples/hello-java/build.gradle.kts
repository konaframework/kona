plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("hello.Main")
}

dependencies {
    implementation(project(":kona-http"))
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

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
