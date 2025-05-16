plugins {
    id("java-library")
}

group = "dev.derrop.ocr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":cluster-executor"))

    api("org.slf4j:slf4j-simple:2.0.17")
}
