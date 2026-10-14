rootProject.name = "kona"

include("bom")
include("kona-core")
include("kona-http")
include("kona-grind")
include("kona-cli")
include("hello-kotlin")
include("hello-java")

project(":kona-core").projectDir = file("modules/core")
project(":kona-http").projectDir = file("modules/http")
project(":kona-grind").projectDir = file("modules/grind")
project(":kona-cli").projectDir = file("modules/cli")
project(":hello-kotlin").projectDir = file("samples/hello-kotlin")
project(":hello-java").projectDir = file("samples/hello-java")
