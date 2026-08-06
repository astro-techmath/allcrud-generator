import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    java
    application
    `maven-publish`
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.astro-techmath"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

val springBootVersion = "4.1.0"
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
    mavenLocal()
    mavenCentral()
}

val openapiGeneratorVersion = "7.23.0"
val junitVersion = "5.11.0"
val snakeYamlVersion = "2.4"

dependencies {
    implementation("org.openapitools:openapi-generator:$openapiGeneratorVersion")
    // allcrud-generator.yml parsing (AllcrudGeneratorYamlConfig) - already resolved
    // transitively via openapi-generator's own dependency tree (confirmed via
    // ./gradlew dependencies --configuration compileClasspath), declared explicitly here
    // instead of relying on that accidental transitive availability.
    implementation("org.yaml:snakeyaml:$snakeYamlVersion")
    // Only src/test/java references allcrud core classes (reflection + compiling
    // generated fixtures against it) - main never does. Keeping this as "implementation"
    // pulled it into allcrud-generator-gradle-plugin's own plugin classpath via
    // implementation(project(":")), needlessly dragging in allcrud's runtime-scope Spring
    // starters (unversioned by design - see allcrud's own POM) into plugin resolution.
    testImplementation("$allcrudCoreGroup:$allcrudCoreArtifact:$allcrudCoreVersion")
    // CrudServiceTests/CrudControllerIntegrationTests (the base classes unitTest.mustache/
    // integrationTest.mustache extend) live in allcrud's test-fixtures variant, not its main
    // jar - needed here so generated *ServiceTest/*ControllerIT compat tests (compiled for real
    // via javax.tools.JavaCompiler, not just reflected on) actually have something to extend.
    // Confirmed via javap against the fixtures jar directly, not assumed.
    testImplementation(testFixtures("$allcrudCoreGroup:$allcrudCoreArtifact:$allcrudCoreVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Minimal publish setup - just enough for allcrud-generator-gradle-plugin's
// implementation(project(":")) to resolve as a real coordinate via publishToMavenLocal.
// Not a statement about real (Central) publishing, which is a separate decision.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// Job 2: manual/on-demand check for a newer core version on Maven Central.
// Does not validate compatibility (that's Job 1's job, on manual bump) - just
// alerts that a newer allcrud version exists, as a reminder to go look.
tasks.register("checkCoreUpdates") {
    group = "verification"
    description = "Checks Maven Central for a newer $allcrudCoreGroup:$allcrudCoreArtifact version than the one pinned in gradle.properties."

    doLast {
        val metadataUrl =
            "https://repo1.maven.org/maven2/${allcrudCoreGroup.replace('.', '/')}/$allcrudCoreArtifact/maven-metadata.xml"

        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder(URI.create(metadataUrl)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw GradleException("Failed to fetch $metadataUrl - HTTP ${response.statusCode()}")
        }

        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(response.body().toByteArray()))
        val latestVersion = document.getElementsByTagName("release").item(0).textContent

        if (latestVersion != allcrudCoreVersion) {
            throw GradleException(
                "Newer $allcrudCoreGroup:$allcrudCoreArtifact version available: " +
                    "$latestVersion (pinned: $allcrudCoreVersion). Consider bumping allcrudCoreVersion in gradle.properties."
            )
        }

        println("$allcrudCoreGroup:$allcrudCoreArtifact is up to date (pinned: $allcrudCoreVersion).")
    }
}
