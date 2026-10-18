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
val springBootVersion = "4.1.1"
val jacksonVersion = project.property("jacksonVersion") as String
val jacksonAnnotationsVersion = project.property("jacksonAnnotationsVersion") as String

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
    }
    dependencies {
        dependency("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
        dependency("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
        dependency("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
        dependency("com.fasterxml.jackson.core:jackson-annotations:$jacksonAnnotationsVersion")
    }
    generatedPomCustomization {
        enabled(false)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // AllcrudGenerator/GenerationRequest/PojoNamingStyle - the public generation API this
    // plugin wires into the Gradle lifecycle. No generation logic lives here, only the glue.
    implementation(project(":"))
    constraints {
        listOf(
            "com.fasterxml.jackson.core:jackson-core:$jacksonVersion",
            "com.fasterxml.jackson.core:jackson-databind:$jacksonVersion",
            "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion",
            "com.fasterxml.jackson.core:jackson-annotations:$jacksonAnnotationsVersion",
        ).forEach {
            implementation(it) {
                because("Published floor for GHSA-7hhh-6rmp-j9qf, GHSA-p6pp-m3f8-5c89, GHSA-cxp5-3px4-pw24, GHSA-wv8q-qhhj-9h54, GHSA-gx83-3vf8-gh7j, GHSA-q4xh-88c3-wmh7, GHSA-wjgm-6hv5-3cvf (fixed in 2.22.3; annotations aligned with databind)")
            }
        }
    }

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
// none is needed for local-only resolution. Checks both real Central publish tasks (confirmed via
// `./gradlew tasks --all`); release.yml uses publishToMavenCentral, and the auto-release wrapper
// stays covered too.
signing {
    setRequired({
        gradle.taskGraph.allTasks.any {
            it.name == "publishAndReleaseToMavenCentral" || it.name == "publishToMavenCentral"
        }
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
