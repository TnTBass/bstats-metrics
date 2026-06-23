plugins {
    java
}

repositories {
    mavenCentral()
    maven {
        name = "neoforge"
        url = uri("https://maven.neoforged.net/releases/")
    }
    maven {
        name = "mojang"
        url = uri("https://libraries.minecraft.net/")
    }
}

dependencies {
    compileOnly("net.neoforged:neoforge:26.1.2.75")
    compileOnly("net.neoforged.fancymodloader:loader:11.0.13")
    api(project(":base"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withJavadocJar()
    withSourcesJar()
}
