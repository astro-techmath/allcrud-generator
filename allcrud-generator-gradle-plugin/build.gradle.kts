plugins {
    java
    `java-gradle-plugin`
    `maven-publish`
    jacoco
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.astro-techmath"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

jacoco {
    toolVersion = "0.8.15"
}

val junitVersion = "6.1.3"

// See docs/notes/allcrud-generator-gradle-plugin-build.gradle.kts.md#springbootversion-bom-re-imported--doesnt-propagate-through-project-reference
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

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}
