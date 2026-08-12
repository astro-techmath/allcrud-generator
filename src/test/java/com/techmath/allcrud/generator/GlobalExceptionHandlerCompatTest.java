package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// Covers the first "global" (project-level, not per-resource) artifact this generator
// produces - see AllcrudGenerator#generateGlobalExceptionHandler and ExceptionHandlerConfig.
// Uses generation.pojo only (everything else disabled) throughout - the exception handler is
// independent of which layers a resource generates, so keeping the fixture yml minimal proves
// that independence rather than obscuring it. Where the fallback-to-controller-package
// behavior is under test, controller stays "enabled: false" but keeps its own "package" -
// proving the fallback reads the CONFIGURED package regardless of whether that layer is
// actually enabled for generation, exactly like the old format's "packages:" block (which
// declared all 5 unconditionally) behaved.
class GlobalExceptionHandlerCompatTest {

    private static final Path SPEC = Path.of("src/main/resources/specs/product-example.yaml");

    @Test
    void defaultGeneratesWithFallbackToControllerPackage() throws Exception {
        Path sourceRoot = Files.createTempDirectory("allcrud-exception-handler-default");
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        Path yml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: org.openapitools.model
                  repository:
                    enabled: false
                  converter:
                    enabled: false
                  service:
                    enabled: false
                  controller:
                    enabled: false
                    package: org.openapitools.api
                """);

        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(yml);
        AllcrudGenerator.generate(config.toGenerationRequest(SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot()));

        Path handler = sourceRoot.resolve("org/openapitools/api/GlobalExceptionHandler.java");
        assertTrue(Files.exists(handler), "Expected GlobalExceptionHandler.java at the generation.controller.package fallback");

        String content = Files.readString(handler);
        assertTrue(content.contains("package org.openapitools.api;"), "Wrong package, found:\n" + content);
        assertTrue(content.contains("public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler"),
                "Wrong class declaration, found:\n" + content);
        assertTrue(content.contains("@ControllerAdvice"), "Missing @ControllerAdvice, found:\n" + content);

        compile(handler);
    }

    @Test
    void explicitPackageAndClassNameAreHonored() throws Exception {
        Path sourceRoot = Files.createTempDirectory("allcrud-exception-handler-explicit");
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        Path yml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: org.openapitools.model
                  repository:
                    enabled: false
                  converter:
                    enabled: false
                  service:
                    enabled: false
                  controller:
                    enabled: false
                exceptionHandler:
                  package: com.acme.errors
                  className: ApiExceptionHandler
                """);

        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(yml);
        AllcrudGenerator.generate(config.toGenerationRequest(SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot()));

        Path handler = sourceRoot.resolve("com/acme/errors/ApiExceptionHandler.java");
        assertTrue(Files.exists(handler), "Expected ApiExceptionHandler.java under the explicit package");

        String content = Files.readString(handler);
        assertTrue(content.contains("package com.acme.errors;"), "Wrong package, found:\n" + content);
        assertTrue(content.contains("public class ApiExceptionHandler extends AbstractGlobalExceptionHandler"),
                "Wrong class declaration, found:\n" + content);

        // The fallback-to-controller-package location must NOT also get a file.
        assertFalse(Files.exists(sourceRoot.resolve("org/openapitools/api/GlobalExceptionHandler.java")));

        compile(handler);
    }

    @Test
    void disabledProducesNoFileAnywhere() throws Exception {
        Path sourceRoot = Files.createTempDirectory("allcrud-exception-handler-disabled");
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        Path yml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: org.openapitools.model
                  repository:
                    enabled: false
                  converter:
                    enabled: false
                  service:
                    enabled: false
                  controller:
                    enabled: false
                exceptionHandler:
                  enabled: false
                """);

        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(yml);
        AllcrudGenerator.generate(config.toGenerationRequest(SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot()));

        assertFalse(Files.exists(sourceRoot.resolve("org/openapitools/api/GlobalExceptionHandler.java")));
        try (var files = Files.walk(sourceRoot)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().equals("GlobalExceptionHandler.java")),
                    "exceptionHandler.enabled: false must not produce a GlobalExceptionHandler.java anywhere under sourceRoot");
        }
    }

    @Test
    void existingFileIsNeverOverwritten() throws Exception {
        Path sourceRoot = Files.createTempDirectory("allcrud-exception-handler-preserve");
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        Path handler = sourceRoot.resolve("org/openapitools/api/GlobalExceptionHandler.java");
        Files.createDirectories(handler.getParent());
        String handWritten = """
                package org.openapitools.api;
                // hand-edited, has custom @ExceptionHandler methods
                public class GlobalExceptionHandler extends com.techmath.allcrud.exception.handler.AbstractGlobalExceptionHandler {
                }
                """;
        Files.writeString(handler, handWritten);

        Path yml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: org.openapitools.model
                  repository:
                    enabled: false
                  converter:
                    enabled: false
                  service:
                    enabled: false
                  controller:
                    enabled: false
                    package: org.openapitools.api
                """);

        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(yml);
        AllcrudGenerator.generate(config.toGenerationRequest(SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot()));

        assertEquals(handWritten, Files.readString(handler),
                "GlobalExceptionHandler.java must never be overwritten once it exists - no onRegenerate knob");
    }

    private void compile(Path source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        File classesOutputDir = Files.createTempDirectory("allcrud-exception-handler-classes").toFile();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.getDefault(), null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(classesOutputDir));
            List<String> options = List.of("-classpath", System.getProperty("java.class.path"));
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromFiles(List.of(source.toFile()));
            boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call();
            if (!success) {
                String errors = diagnostics.getDiagnostics().stream()
                        .map(Diagnostic::toString)
                        .collect(Collectors.joining("\n"));
                fail("Failed to compile " + source + ":\n" + errors);
            }
        }
    }

    private Path writeYaml(String content) throws IOException {
        Path file = Files.createTempFile("allcrud-generator-exception-handler", ".yml");
        Files.writeString(file, content);
        return file;
    }

}
