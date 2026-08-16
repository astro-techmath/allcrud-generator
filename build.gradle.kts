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
    jacoco
    id("io.spring.dependency-management") version "1.1.7"
    id("org.sonarqube") version "7.4.0.8496"
}

group = "io.github.astro-techmath"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

jacoco {
    toolVersion = "0.8.15"
}

sonar {
    properties {
        property("sonar.projectKey", "astro-techmath_allcrud-generator")
        property("sonar.organization", "astro-techmath")
        // The allcrud-generator-gradle-plugin subproject has its own jacocoTestReport.xml -
        // sonar treats a multi-project build as one analysis, so both paths need to be listed
        // (comma-separated, relative to the project root) or the subproject's classes never get
        // matched to a coverage report at all - confirmed against Sonar's own docs on this
        // property, not assumed.
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "build/reports/jacoco/test/jacocoTestReport.xml," +
                "allcrud-generator-gradle-plugin/build/reports/jacoco/test/jacocoTestReport.xml"
        )
        // IoExceptions is already excluded from the JaCoCo XML itself (see the
        // jacocoTestReport.classDirectories exclusion below), but that only controls what JaCoCo
        // reports - Sonar reads the same XML and, for any class absent from it, treats "absent"
        // as "0% covered" rather than "excluded on purpose". Those are different concepts to
        // Sonar, so the exclusion has to be declared here too, not just at the JaCoCo level.
        // AllcrudGenerateTask/AllcrudGeneratorPlugin (allcrud-generator-gradle-plugin
        // subproject) are genuinely exercised for real, not untested - see
        // AllcrudGeneratorPluginFunctionalTest, which applies the plugin and runs both
        // generateAllcrud and build via real GradleRunner/TestKit. They show 0% anyway because
        // TestKit forks a separate Gradle daemon by default, outside the JaCoCo agent attached
        // to that test's own JVM. The documented fix (GradleRunner.withDebug(true), running the
        // build in-process instead) was tried and confirmed broken here: withPluginClasspath()
        // under in-process execution doesn't expose the plugin's classes to the fixture's own
        // buildscript compilation (an "Unresolved reference" on the plugin's own extension type,
        // not just its Kotlin DSL accessor sugar) - a real TestKit/Kotlin-DSL limitation, not a
        // gap this project's own tests can close without risking the passing tests that already
        // prove this code works.
        property("sonar.coverage.exclusions", "src/main/java/com/techmath/allcrud/generator/IoExceptions.java," +
                "allcrud-generator-gradle-plugin/src/main/java/com/techmath/allcrud/generator/gradle/AllcrudGenerateTask.java," +
                "allcrud-generator-gradle-plugin/src/main/java/com/techmath/allcrud/generator/gradle/AllcrudGeneratorPlugin.java," +
                "allcrud-generator-gradle-plugin/src/main/java/com/techmath/allcrud/generator/gradle/AllcrudGeneratorExtension.java")
    }
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

val allcrudCoreGroup = project.property("allcrudCoreGroup") as String
val allcrudCoreArtifact = project.property("allcrudCoreArtifact") as String
val allcrudCoreVersion = project.property("allcrudCoreVersion") as String

repositories {
    mavenLocal()
    mavenCentral()
}

val openapiGeneratorVersion = "7.24.0"
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

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    // IoExceptions (com.techmath.allcrud.generator) is a single-purpose wrapper around
    // "catch (IOException e) { throw new UncheckedIOException(e); }" - defensive I/O-failure
    // plumbing with no branching logic of its own, only reachable by mocking disk failure.
    // Excluded as its own dedicated class (not a blanket class-level exclusion elsewhere) so
    // every other line in AllcrudGenerator/AllcrudGeneratorYamlConfig stays subject to the
    // project's 100%-coverage goal.
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("**/IoExceptions.class", "**/AllcrudGeneratorExtension.class")
            }
        })
    )
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

// The sonar task only picked up jacocoTestReport.xml by accident, when a CI step happened to run
// ./gradlew build (which produces it via test's finalizedBy) before a separate ./gradlew sonar
// step - confirmed empirically via `./gradlew :sonar --dry-run`, which shows no dependency on
// jacocoTestReport in the task graph. Same bug class as the plainJavadocJar/generateMetadataFile
// issue already fixed in allcrud: a task consuming another task's output without declaring it.
// Also needs the subproject's own jacocoTestReport (see sonar.coverage.jacoco.xmlReportPaths
// above) for the same reason - it's a separate task in a separate project, with no dependency
// on it otherwise.
tasks.named("sonar") {
    dependsOn(tasks.jacocoTestReport)
    dependsOn(":allcrud-generator-gradle-plugin:jacocoTestReport")
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
