import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    java
    application
    jacoco
    signing
    id("io.spring.dependency-management") version "1.1.7"
    id("org.sonarqube") version "7.5.0.8588"
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

sonar {
    properties {
        property("sonar.projectKey", "astro-techmath_allcrud-generator")
        property("sonar.organization", "astro-techmath")
        // See docs/notes/build.gradle.kts.md#sonarcoveragejacocoxmlreportpaths--multi-project-build-needs-both-paths-listed
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "build/reports/jacoco/test/jacocoTestReport.xml," +
                "allcrud-generator-gradle-plugin/build/reports/jacoco/test/jacocoTestReport.xml"
        )
        // See docs/notes/build.gradle.kts.md#coverage-exclusions--ioexceptions-and-testkit-forked-tasks-show-0-for-different-reasons
        property("sonar.coverage.exclusions", "src/main/java/com/techmath/allcrud/generator/IoExceptions.java," +
                "allcrud-generator-gradle-plugin/src/main/java/com/techmath/allcrud/generator/gradle/AllcrudGenerateTask.java," +
                "allcrud-generator-gradle-plugin/src/main/java/com/techmath/allcrud/generator/gradle/AllcrudGeneratorPlugin.java," +
                "allcrud-generator-gradle-plugin/src/main/java/com/techmath/allcrud/generator/gradle/AllcrudGeneratorExtension.java")
    }
}

val springBootVersion = "4.1.1"
val commonsLang3Version = "3.21.0"
val jacksonBomVersion = project.property("jacksonVersion") as String
val jacksonAnnotationsVersion = project.property("jacksonAnnotationsVersion") as String
val handlebarsVersion = project.property("handlebarsVersion") as String

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
    mavenCentral()
}

val openapiGeneratorVersion = "7.26.0"
val junitVersion = "6.1.3"
val snakeYamlVersion = "2.7"

dependencies {
    implementation("org.openapitools:openapi-generator:$openapiGeneratorVersion") {
        exclude(group = "org.openjdk.nashorn", module = "nashorn-core")
    }
    // See docs/notes/build.gradle.kts.md#snakeyaml-declared-explicitly-despite-already-being-a-transitive-dependency
    implementation("org.yaml:snakeyaml:$snakeYamlVersion")
    constraints {
        listOf(
            "com.fasterxml.jackson.core:jackson-core:$jacksonBomVersion",
            "com.fasterxml.jackson.core:jackson-databind:$jacksonBomVersion",
            "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonBomVersion",
            "com.fasterxml.jackson.core:jackson-annotations:$jacksonAnnotationsVersion",
        ).forEach {
            implementation(it) {
                because("Published floor for GHSA-7hhh-6rmp-j9qf, GHSA-p6pp-m3f8-5c89, GHSA-cxp5-3px4-pw24, GHSA-wv8q-qhhj-9h54, GHSA-gx83-3vf8-gh7j, GHSA-q4xh-88c3-wmh7, GHSA-wjgm-6hv5-3cvf (fixed in 2.22.3; annotations aligned with databind)")
            }
        }
        implementation("com.github.jknack:handlebars:$handlebarsVersion") {
            because("Handlebars 4.5.2 approved for 0.1.0 (#39)")
        }
    }
    // See docs/notes/build.gradle.kts.md#allcrud-core-test-dependency-scope--testimplementation-avoids-leaking-spring-starters
    testImplementation("$allcrudCoreGroup:$allcrudCoreArtifact:$allcrudCoreVersion")
    // See docs/notes/build.gradle.kts.md#testfixtures-dependency--confirmed-via-javap-not-assumed
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
    // See docs/notes/build.gradle.kts.md#ioexceptions-jacoco-exclusion--excluded-as-its-own-class-not-blanket
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

// See docs/notes/build.gradle.kts.md#sonar-task-dependson-jacocotestreport--accidental-ordering-bug-same-class-as-allcruds-fix
tasks.named("sonar") {
    dependsOn(tasks.jacocoTestReport)
    dependsOn(":allcrud-generator-gradle-plugin:jacocoTestReport")
}

mavenPublishing {
    coordinates("io.github.astro-techmath", "allcrud-generator", version.toString())

    pom {
        name.set("Allcrud Generator")
        description.set("Contract-first code generator core API for Allcrud - reads an OpenAPI spec plus an allcrud-generator.yml config file and generates the Controller/Service/Repository/Converter/POJO stack for each resource.")
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

// signAllPublications() wires signing into every publish task unconditionally, including
// publishToMavenLocal - which CI's own "Publish root artifact to Maven local" step (needed so the
// Maven module can resolve this project as a dependency) and release.yml's test gate both call,
// neither with a signing key present nor needing one: mavenLocal resolution never checks .asc
// files, only a real Central publish does. Confirmed as a real CI failure (No configured
// signatory), not assumed. setRequired with a lazy closure is the documented Gradle signing-plugin
// idiom for this - required only when a real publish task is actually in the graph, so
// publishToMavenLocal silently skips signing instead of failing when no key is configured.
//
// Checks both publishAndReleaseToMavenCentral AND publishToMavenCentral - vanniktech exposes both
// as real, independent publish-to-Central tasks (confirmed via `./gradlew tasks --all`, not
// assumed). release.yml calls publishToMavenCentral (upload only; Portal confirms VALIDATED);
// publishAndReleaseToMavenCentral remains covered so an accidental local/CI auto-release run
// still requires a signatory.
signing {
    setRequired({
        gradle.taskGraph.allTasks.any {
            it.name == "publishAndReleaseToMavenCentral" || it.name == "publishToMavenCentral"
        }
    })
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
