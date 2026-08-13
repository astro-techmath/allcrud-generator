package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// See docs/adr/0006-entity-out-of-scope-v1.md. AllcrudSpringCodegen#resolveEntityPackage/
// readPackageStatement run for every confirmed resource regardless of which GeneratedLayer set
// the caller requests - staging always renders all 5 layers per operation unconditionally, so
// these fail-fasts are reachable through a plain end-to-end AllcrudGenerator.generate() call,
// no reflection needed.
class EntityResolutionCompatTest {

    private static final Path SPEC = Path.of("src/main/resources/specs/product-example.yaml");

    @Test
    void generateFailsFastWhenEntityFileIsMissing() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-entity-missing");
        // Deliberately no EntityFixtures.copyInto call - neither Product.java nor Order.java
        // exists anywhere under sourceRoot.

        Throwable rootCause = rootCauseOf(() ->
                AllcrudGenerator.generate(new GenerationRequest(
                        SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                        Set.of(GeneratedLayer.POJO),
                        Map.of(GeneratedLayer.POJO, "org.openapitools.model"),
                        OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER)));

        assertTrue(rootCause instanceof IllegalStateException,
                "Expected root cause to be IllegalStateException, was: " + rootCause.getClass().getName());
        assertTrue(rootCause.getMessage().contains(".java not found under"),
                "Expected the entity-not-found message, was: " + rootCause.getMessage());
        assertTrue(rootCause.getMessage().contains("create the entity before running the generator"),
                "Expected the fail-fast's guidance, was: " + rootCause.getMessage());
    }

    @Test
    void generateFailsFastWhenEntityFileIsAmbiguous() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-entity-ambiguous");
        EntityFixtures.copyInto(sourceRoot, "Order");

        // Two files named Product.java under sourceRoot, in different subdirectories - resolving
        // "the" entity package for Product is genuinely ambiguous.
        Path first = sourceRoot.resolve("org/openapitools/entity/Product.java");
        Path second = sourceRoot.resolve("other/pkg/Product.java");
        Files.createDirectories(second.getParent());
        Files.writeString(first, "package org.openapitools.entity;\npublic class Product {}\n");
        Files.writeString(second, "package other.pkg;\npublic class Product {}\n");

        Throwable rootCause = rootCauseOf(() ->
                AllcrudGenerator.generate(new GenerationRequest(
                        SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                        Set.of(GeneratedLayer.POJO),
                        Map.of(GeneratedLayer.POJO, "org.openapitools.model"),
                        OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER)));

        assertTrue(rootCause instanceof IllegalStateException,
                "Expected root cause to be IllegalStateException, was: " + rootCause.getClass().getName());
        assertTrue(rootCause.getMessage().contains("Found more than one"),
                "Expected the ambiguous-entity message, was: " + rootCause.getMessage());
    }

    @Test
    void generateFailsFastWhenEntityFileFirstLineIsNotAPackageDeclaration() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-entity-malformed");
        EntityFixtures.copyInto(sourceRoot, "Order");

        Path malformed = sourceRoot.resolve("org/openapitools/entity/Product.java");
        Files.createDirectories(malformed.getParent());
        Files.writeString(malformed, "// TODO: add package statement\npublic class Product {}\n");

        Throwable rootCause = rootCauseOf(() ->
                AllcrudGenerator.generate(new GenerationRequest(
                        SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                        Set.of(GeneratedLayer.POJO),
                        Map.of(GeneratedLayer.POJO, "org.openapitools.model"),
                        OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER)));

        assertTrue(rootCause instanceof IllegalStateException,
                "Expected root cause to be IllegalStateException, was: " + rootCause.getClass().getName());
        assertTrue(rootCause.getMessage().contains("Expected first line"),
                "Expected the malformed-package-line message, was: " + rootCause.getMessage());
    }

    @Test
    void generateFailsFastWhenEntityFilePackageLineHasNoTrailingSemicolon() throws IOException {
        // generateFailsFastWhenEntityFileFirstLineIsNotAPackageDeclaration above only covers the
        // "doesn't start with 'package '" half of readPackageStatement's check (short-circuits
        // before ever evaluating endsWith(";")) - every other test's fixture always has a
        // proper, semicolon-terminated package line. This is the other half: starts with
        // "package " but is missing the trailing ';', never exercised elsewhere.
        Path sourceRoot = Files.createTempDirectory("allcrud-entity-no-semicolon");
        EntityFixtures.copyInto(sourceRoot, "Order");

        Path malformed = sourceRoot.resolve("org/openapitools/entity/Product.java");
        Files.createDirectories(malformed.getParent());
        Files.writeString(malformed, "package org.openapitools.entity\npublic class Product {}\n");

        Throwable rootCause = rootCauseOf(() ->
                AllcrudGenerator.generate(new GenerationRequest(
                        SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                        Set.of(GeneratedLayer.POJO),
                        Map.of(GeneratedLayer.POJO, "org.openapitools.model"),
                        OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER)));

        assertTrue(rootCause instanceof IllegalStateException,
                "Expected root cause to be IllegalStateException, was: " + rootCause.getClass().getName());
        assertTrue(rootCause.getMessage().contains("Expected first line"),
                "Expected the malformed-package-line message, was: " + rootCause.getMessage());
    }

    private Throwable rootCauseOf(Executable executable) {
        Throwable wrapper = assertThrows(RuntimeException.class, executable);
        Throwable rootCause = wrapper;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    @Test
    void generateSucceedsWhenEntityFileHasNoTrailingNewline() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-entity-no-trailing-newline");
        EntityFixtures.copyInto(sourceRoot, "Order");

        // A single line, no '\n' anywhere in the file - readPackageStatement's
        // newlineIndex == -1 branch, on a REAL entity file this time (not a synthetic string
        // in AllcrudGeneratorInternalsCompatTest's equivalent for rewritePackageStatement).
        Path noNewline = sourceRoot.resolve("org/openapitools/entity/Product.java");
        Files.createDirectories(noNewline.getParent());
        Files.writeString(noNewline, "package org.openapitools.entity;");

        AllcrudGenerator.generate(new GenerationRequest(
                SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.POJO),
                Map.of(GeneratedLayer.POJO, "org.openapitools.model"),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/model/ProductVO.java")));
    }

}
