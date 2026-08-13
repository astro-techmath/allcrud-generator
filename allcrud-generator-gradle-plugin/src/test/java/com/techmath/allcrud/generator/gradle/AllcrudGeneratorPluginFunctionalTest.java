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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Applies the plugin for real against a throwaway fixture project (GradleRunner +
// withPluginClasspath(), populated automatically by the java-gradle-plugin plugin's
// pluginUnderTestMetadata mechanism - no manual classpath wiring). Proves both that
// the task runs standalone and that the sourceSet wiring (outputDir vs javaSourceDir,
// see AllcrudGenerateTask) actually lets a real compileJava pick up the generated code.
class AllcrudGeneratorPluginFunctionalTest {

    @TempDir
    Path projectDir;

    // See docs/notes/AllcrudGeneratorPluginFunctionalTest.md#junit-5-doesnt-guarantee-beforeeach-execution-order-without-order
    @BeforeEach
    void setUpProjectFixtures() throws IOException {
        Path specTarget = projectDir.resolve("product-example.yaml");
        try (InputStream in = getClass().getResourceAsStream("/product-example.yaml")) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture: /product-example.yaml");
            }
            Files.copy(in, specTarget, StandardCopyOption.REPLACE_EXISTING);
        }

        // Real allcrud-generator.yml (packages/defaults/resources), not a hardcoded placeholder -
        // proves the task actually reads yml-driven config end to end, including a per-resource
        // override (Order: generate [pojo, controller] only).
        copyResource("/allcrud-generator-example.yml", projectDir.resolve("allcrud-generator.yml"));

        // See docs/adr/0006-entity-out-of-scope-v1.md - apiController.mustache/service.mustache
        // reference "Product"/"Order" by a hardcoded name and package, so something has to
        // provide those classes for compileJava to resolve against. These are the same TEST
        // FIXTURE - NOT GENERATOR OUTPUT entities used by the root module's own compat tests
        // (src/test/resources/fixtures), copied here rather than shared across modules to keep
        // this subproject self-contained.
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
    void generateAllcrudTaskProducesExpectedSources() {
        writeBuildFiles();

        BuildResult result = runner("generateAllcrud").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateAllcrud").getOutcome());

        // Default outputDir is now src/main/java directly (see AllcrudGeneratorPlugin) - no
        // more build/generated/... default, per the "never default to build/" rule.
        Path generatedVo = projectDir.resolve("src/main/java/org/openapitools/model/ProductVO.java");
        assertTrue(Files.exists(generatedVo), "Expected generated file at " + generatedVo);

        // Order's "generate: [repository, service]" (allcrud-generator-example.yml) REPLACES
        // the default layer list entirely - proves the yml override reaches through the real
        // task.
        assertTrue(Files.exists(projectDir.resolve("src/main/java/org/openapitools/api/OrderRepository.java")));
        assertTrue(Files.exists(projectDir.resolve("src/main/java/org/openapitools/api/OrderService.java")));
        assertFalse(Files.exists(projectDir.resolve("src/main/java/org/openapitools/model/OrderVO.java")));
        assertFalse(Files.exists(projectDir.resolve("src/main/java/org/openapitools/api/OrderController.java")));
        assertFalse(Files.exists(projectDir.resolve("src/main/java/org/openapitools/api/OrderConverter.java")));
    }

    @Test
    void generatedSourcesAreWiredIntoCompileJavaAndCompile() {
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

        // See docs/adr/0011-pinned-core-version-drift-guard.md and
        // docs/notes/AllcrudGeneratorPluginFunctionalTest.md#allcruds-published-pom-leaves-spring-boot-starters-unversioned
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
                    // See docs/notes/AllcrudGeneratorPluginFunctionalTest.md#allcruds-pom-declares-its-own-dependencies-at-runtime-scope-not-compile
                    implementation("org.springframework.boot:spring-boot-starter-web")
                    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
                    implementation("org.springframework.boot:spring-boot-starter-validation")
                    // See docs/notes/AllcrudGeneratorPluginFunctionalTest.md#openapi-generators-swagger2annotationlibrary-emits-schema-annotations
                    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.28")
                    // See docs/notes/AllcrudGeneratorPluginFunctionalTest.md#the-stock-spring-generator-also-emits-springdocconfigurationjava-by-default
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
