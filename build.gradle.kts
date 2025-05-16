plugins {
    id("java-library")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.derrop.ocr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":cluster-executor"))
    api(project(":cluster-executor-hetzner"))

    api("org.yaml:snakeyaml:2.4")

    api("org.slf4j:slf4j-simple:2.0.17")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    getArchiveBaseName().set("ocr")
    getArchiveFileName().set("ocr.jar")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.github.einrobin.ocr.Main"
    }
}
