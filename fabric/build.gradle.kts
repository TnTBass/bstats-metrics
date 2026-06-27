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
    api(project(":base")) {
        isTransitive = true
    }
    testCompileOnly("net.fabricmc:fabric-loader:0.19.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
