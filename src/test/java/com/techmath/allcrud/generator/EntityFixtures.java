package com.techmath.allcrud.generator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// TEST SUPPORT - copies the TEST FIXTURE (not generator output) entity classes from
// src/test/resources/fixtures into a given sourceRoot, at org.openapitools.entity - matching
// where a real consumer would have hand-written them (this project's placeholder package
// convention). Needed because AllcrudSpringCodegen now resolves each entity's package by
// scanning sourceRoot for <EntityName>.java (entity generation is out of scope, and there's
// no "packages.entity" yml key - see AllcrudSpringCodegen#resolveEntityPackage) - that scan
// runs unconditionally for every resource in a generate() call, regardless of which of the 5
// layers a given test actually cares about downstream, so every test that generates anything
// needs its entity fixtures in place first.
final class EntityFixtures {

    // Shared by tests that construct GenerationRequest directly (not via
    // AllcrudGeneratorYamlConfig) and don't care about the global exception handler - opts out
    // so those tests keep testing only what they're actually about.
    static final ExceptionHandlerConfig NO_EXCEPTION_HANDLER =
            new ExceptionHandlerConfig(false, null, "GlobalExceptionHandler");

    private EntityFixtures() {
    }

    static void copyInto(Path sourceRoot, String... entityNames) throws IOException {
        Path entityDir = sourceRoot.resolve("org/openapitools/entity");
        Files.createDirectories(entityDir);
        for (String entityName : entityNames) {
            String resourcePath = "/fixtures/" + entityName + ".java";
            try (InputStream in = EntityFixtures.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new IllegalStateException("Missing test fixture: " + resourcePath);
                }
                Files.copy(in, entityDir.resolve(entityName + ".java"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

}
