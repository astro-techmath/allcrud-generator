package com.techmath.allcrud.generator.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Applies the plugin for real against a throwaway fixture project (GradleRunner +
// withPluginClasspath(), populated automatically by the java-gradle-plugin plugin's
// pluginUnderTestMetadata mechanism - no manual classpath wiring). Proves both that
// the task runs standalone and that the sourceSet wiring (outputDir vs javaSourceDir,
// see AllcrudGenerateTask) actually lets a real compileJava pick up the generated code.
class AllcrudGeneratorPluginFunctionalTest {

    @TempDir
    Path projectDir;

    @BeforeEach
    void copySpecFixture() throws IOException {
        Path specTarget = projectDir.resolve("product-example.yaml");
        try (InputStream in = getClass().getResourceAsStream("/product-example.yaml")) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture: /product-example.yaml");
            }
            Files.copy(in, specTarget, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // Entity generation is out of scope for allcrud-generator's V1 (already decided) -
    // apiController.mustache/service.mustache reference "Product"/"Order" by a hardcoded
    // name and package, so something has to provide those classes for compileJava to
    // resolve against. These are the same TEST FIXTURE - NOT GENERATOR OUTPUT entities
    // used by the root module's own compat tests (src/test/resources/fixtures), copied
    // here rather than shared across modules to keep this subproject self-contained.
    @BeforeEach
    void copyEntityFixtures() throws IOException {
        Path entityDir = projectDir.resolve("src/main/java/org/openapitools/entity");
        Files.createDirectories(entityDir);
        copyResource("/fixtures/Product.java", entityDir.resolve("Product.java"));
        copyResource("/fixtures/Order.java", entityDir.resolve("Order.java"));
    }

    private void copyResource(String resourcePath, Path target) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture: " + resourcePath);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    void generateAllcrudTaskProducesExpectedSources() throws IOException {
        writeBuildFiles();

        BuildResult result = runner("generateAllcrud").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateAllcrud").getOutcome());

        Path generatedVo = projectDir.resolve("build/generated/sources/allcrud/src/main/java/org/openapitools/model/ProductVO.java");
        assertTrue(Files.exists(generatedVo), "Expected generated file at " + generatedVo);
    }

    @Test
    void generatedSourcesAreWiredIntoCompileJavaAndCompile() throws IOException {
        writeBuildFiles();

        BuildResult result = runner("build").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateAllcrud").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileJava").getOutcome());
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput();
    }

    private void writeBuildFiles() {
        write("settings.gradle.kts", "rootProject.name = \"allcrud-generator-plugin-fixture\"\n");

        // 0.1.0-beta / io.github.astro-techmath:allcrud - same pinned coordinate as
        // gradle.properties in the root module (allcrudCoreVersion). Hardcoded here
        // deliberately: this is a throwaway fixture project, not production code.
        // allcrud's published POM leaves its Spring Boot starters unversioned (managed by
        // the same BOM at allcrud's own build time, not baked into the published POM) -
        // any consumer, this fixture included, has to import that BOM itself. Same reason
        // allcrud-generator-gradle-plugin/build.gradle.kts does it.
        write("build.gradle.kts", """
                plugins {
                    java
                    id("io.spring.dependency-management") version "1.1.7"
                    id("io.github.astro-techmath.allcrud-generator")
                }

                repositories {
                    mavenCentral()
                }

                dependencyManagement {
                    imports {
                        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.6")
                    }
                }

                dependencies {
                    implementation("io.github.astro-techmath:allcrud:0.1.0-beta")
                    // allcrud's own POM declares these at "runtime" scope, not "compile" -
                    // by design, a Spring Boot library expects the consuming app to bring
                    // its own starters at compile scope, exactly like any real app already
                    // would. This fixture simulates that real app dependency, it's not
                    // compensating for a gap in allcrud or the plugin.
                    implementation("org.springframework.boot:spring-boot-starter-web")
                    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
                    implementation("org.springframework.boot:spring-boot-starter-validation")
                    // openapi-generator's swagger2AnnotationLibrary (this project's default)
                    // emits @Schema annotations on generated models - a real consumer project
                    // needs this on its own compile classpath, same reasoning as the starters
                    // above.
                    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.28")
                    // The stock "spring" generator also emits SpringDocConfiguration.java by
                    // default (io.swagger.v3.oas.models.*) - not an allcrud-generator concern,
                    // just what a real consumer of the underlying spring library needs.
                    implementation("io.swagger.core.v3:swagger-models-jakarta:2.2.28")
                }

                allcrudGenerator {
                    specFile.set(file("product-example.yaml"))
                }
                """);
    }

    private void write(String fileName, String content) {
        try {
            Files.writeString(projectDir.resolve(fileName), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
