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
        api(project(":kona-auth"))
        api(project(":kona-session"))
        api(project(":kona-support"))
        api(project(":kona-redis"))
        api(project(":kona-cache"))
        api(project(":kona-crema"))
        api(project(":kona-grind"))
        api(project(":kona-cli"))
    }
}
