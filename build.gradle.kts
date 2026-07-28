plugins {
    java
    application
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.astro-techmath"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

val springBootVersion = "3.5.6"
val commonsLang3Version = "3.20.0"
val jacksonBomVersion = "2.22.1"

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        mavenBom("com.fasterxml.jackson:jackson-bom:$jacksonBomVersion")
    }
    dependencies {
        dependency("org.apache.commons:commons-lang3:$commonsLang3Version")
    }
}

val allcrudCoreGroup: String by project
val allcrudCoreArtifact: String by project
val allcrudCoreVersion: String by project

repositories {
    mavenCentral()
}

val openapiGeneratorVersion = "7.23.0"
val junitVersion = "5.11.0"

dependencies {
    implementation("$allcrudCoreGroup:$allcrudCoreArtifact:$allcrudCoreVersion")
    implementation("org.openapitools:openapi-generator:$openapiGeneratorVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
