plugins {
    id("java-library")
}

group = "dev.derrop.ocr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("org.bouncycastle:bcprov-jdk18on:1.80")
    api("org.bouncycastle:bcpkix-jdk18on:1.80")
    api("com.github.mwiede:jsch:2.27.0")
    api("org.slf4j:slf4j-simple:2.0.17")
}
