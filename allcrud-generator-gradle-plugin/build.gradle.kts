plugins {
    java
    `java-gradle-plugin`
    `maven-publish`
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.astro-techmath"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

val junitVersion = "6.1.2"

// The allcrud core artifact (see project(":") below) declares its Spring Boot starters
// without explicit versions, managed by this same BOM in the root module - it doesn't
// propagate transitively through project(":"), so it's imported here too.
val springBootVersion = "4.1.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // AllcrudGenerator/GenerationRequest/PojoNamingStyle - the public generation API this
    // plugin wires into the Gradle lifecycle. No generation logic lives here, only the glue.
    implementation(project(":"))

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        create("allcrudGenerator") {
            id = "io.github.astro-techmath.allcrud-generator"
            implementationClass = "com.techmath.allcrud.generator.gradle.AllcrudGeneratorPlugin"
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
