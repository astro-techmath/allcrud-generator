package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Covers 3 of AllcrudGenerator's private static fail-fast guards that AllcrudGeneratorYamlConfig
// already prevents from being reachable through the yml-driven path (see that class's own
// validation), but AllcrudGenerator.generate() is a public entrypoint callers CAN invoke directly
// with a hand-built GenerationRequest, bypassing yml entirely - these guards are this class's own
// last line of defense, and deserve their own direct coverage independent of the yml layer above
// it. classify()/rewritePackageStatement() are private - reached via reflection, same technique
// already used elsewhere in this suite (ValidationUtilsTest, in the sibling allcrud repo) for
// private guard clauses that don't need a public entrypoint of their own.
class AllcrudGeneratorInternalsCompatTest {

    private static final Path SPEC = Path.of("src/main/resources/specs/product-example.yaml");

    @Test
    void generateThrowsWhenEnabledLayerHasNoResolvablePackage() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-no-target-package");
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        // CONTROLLER is enabled (defaultLayersToGenerate) but the packages map below has no
        // entry for it at all - AllcrudGeneratorYamlConfig would never let this combination
        // reach AllcrudGenerator (see its own "is enabled but has no package" check), but
        // AllcrudGenerator.generate() is a public entrypoint a caller can invoke directly with
        // a hand-built GenerationRequest that skips yml entirely.
        // Constructed outside the lambda passed to assertThrows below - keeps that lambda down
        // to the single invocation actually under test (generate()), not also the
        // GenerationRequest constructor, which is what the rule wants unambiguous.
        GenerationRequest request = new GenerationRequest(
                SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.CONTROLLER),
                Map.of(),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AllcrudGenerator.generate(request));

        assertTrue(ex.getMessage().contains("No target package configured"),
                "Expected the no-target-package message, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("CONTROLLER"),
                "Expected the message to name the layer, was: " + ex.getMessage());
    }

    @Test
    void classifyThrowsOnUnrecognizedFileSuffix() throws Exception {
        Method classify = AllcrudGenerator.class.getDeclaredMethod("classify", String.class, PojoNamingStyle.class);
        classify.setAccessible(true);

        InvocationTargetException wrapper = assertThrows(InvocationTargetException.class,
                () -> classify.invoke(null, "SomethingElse.java", PojoNamingStyle.VO));

        assertTrue(wrapper.getCause() instanceof IllegalStateException,
                "Expected the cause to be IllegalStateException, was: " + wrapper.getCause());
        assertTrue(wrapper.getCause().getMessage().contains("Unrecognized generated file"),
                "Expected the unrecognized-file message, was: " + wrapper.getCause().getMessage());
    }

    @Test
    void classifyStillMatchesSuffixWhenFileNameHasNoJavaExtension() throws Exception {
        // Every real staged file always ends in ".java" (registerApiLayerTemplates/
        // apiTemplateFiles hardcode that suffix) - this only proves the ".java"-stripping
        // ternary's other branch (raw filename used as-is) doesn't break suffix matching, since
        // nothing in production ever exercises it.
        Method classify = AllcrudGenerator.class.getDeclaredMethod("classify", String.class, PojoNamingStyle.class);
        classify.setAccessible(true);

        Object staged = classify.invoke(null, "ProductVO", PojoNamingStyle.VO);

        Method resourceName = staged.getClass().getDeclaredMethod("resourceName");
        resourceName.setAccessible(true);
        assertEquals("Product", resourceName.invoke(staged));
    }

    @Test
    void rewritePackageStatementThrowsWhenFirstLineIsNotAPackageDeclaration() throws Exception {
        Method rewrite = AllcrudGenerator.class.getDeclaredMethod(
                "rewritePackageStatement", String.class, String.class, String.class);
        rewrite.setAccessible(true);

        InvocationTargetException wrapper = assertThrows(InvocationTargetException.class,
                () -> rewrite.invoke(null, "import java.util.List;\npublic class Product {}\n",
                        "com.acme.dto", "ProductVO.java"));

        assertTrue(wrapper.getCause() instanceof IllegalStateException,
                "Expected the cause to be IllegalStateException, was: " + wrapper.getCause());
        assertTrue(wrapper.getCause().getMessage().contains("Expected first line"),
                "Expected the malformed-first-line message, was: " + wrapper.getCause().getMessage());
    }

    @Test
    void rewritePackageStatementHandlesSingleLineContentWithNoTrailingNewline() throws Exception {
        Method rewrite = AllcrudGenerator.class.getDeclaredMethod(
                "rewritePackageStatement", String.class, String.class, String.class);
        rewrite.setAccessible(true);

        // No '\n' at all in the content - exercises the newlineIndex == -1 branch on both
        // sides (firstLine == the whole content, rest == "").
        String result = (String) rewrite.invoke(null, "package org.openapitools.model;", "com.acme.dto", "ProductVO.java");

        assertEquals("package com.acme.dto;", result);
    }

}
