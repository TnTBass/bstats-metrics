plugins {
    java
}

repositories {
    mavenCentral()
    maven {
        name = "fabric"
        url = uri("https://maven.fabricmc.net/")
    }
}

dependencies {
    compileOnly("net.fabricmc:fabric-loader:0.19.3")
    compileOnly("net.fabricmc.fabric-api:fabric-lifecycle-events-v1:4.1.3+4575b05f9e")
    api(project(":base")) {
        isTransitive = true
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}
