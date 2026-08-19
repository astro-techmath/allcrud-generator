plugins {
    java
    `java-gradle-plugin`
    jacoco
    signing
    id("io.spring.dependency-management") version "1.1.7"
    id("com.vanniktech.maven.publish") version "0.37.0"
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

mavenPublishing {
    coordinates("io.github.astro-techmath", "allcrud-generator-gradle-plugin", version.toString())

    pom {
        name.set("Allcrud Generator Gradle Plugin")
        description.set("Gradle plugin for Allcrud Generator - wires contract-first code generation from an OpenAPI spec plus an allcrud-generator.yml config file into the Gradle build lifecycle.")
        url.set("https://github.com/astro-techmath/allcrud-generator")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("mathmferreira")
                name.set("Matheus de Almeida Maia Ferreira")
                email.set("mathmferreira@gmail.com")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/astro-techmath/allcrud-generator.git")
            developerConnection.set("scm:git:ssh://github.com/astro-techmath/allcrud-generator.git")
            url.set("https://github.com/astro-techmath/allcrud-generator")
        }
    }

    publishToMavenCentral()
    signAllPublications()
}

// See build.gradle.kts (root) for the full rationale - same fix, same reason: signAllPublications()
// makes publishToMavenLocal require a signatory too, which fails in CI where no key is present and
// none is needed for local-only resolution.
signing {
    setRequired({
        gradle.taskGraph.allTasks.any { it.name == "publishAndReleaseToMavenCentral" }
    })
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
