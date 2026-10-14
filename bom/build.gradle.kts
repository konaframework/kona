plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":kona-core"))
        api(project(":kona-http"))
        api(project(":kona-extract"))
        api(project(":kona-grind"))
        api(project(":kona-cli"))
    }
}
