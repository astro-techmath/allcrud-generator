package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// See docs/adr/0001-generate-once-never-overwrite.md - proves AllcrudGenerator#shouldOverwrite
// is actually correct, not just that files land in the right place.
class OverwritePolicyCompatTest {

    private static final Path SPEC = Path.of("src/main/resources/specs/product-example.yaml");

    @Test
    void preservePolicyKeepsExistingPojoUntouched() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-overwrite-policy-preserve");
        Path targetFile = sourceRoot.resolve("org/openapitools/model/ProductVO.java");
        Files.createDirectories(targetFile.getParent());
        String sentinel = "// CUSTOM HAND-WRITTEN MARKER - must survive PRESERVE\npackage org.openapitools.model;\n";
        Files.writeString(targetFile, sentinel);
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        AllcrudGenerator.generate(new GenerationRequest(
                SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.POJO, GeneratedLayer.CONTROLLER),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api"),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        assertEquals(sentinel, Files.readString(targetFile), "PRESERVE must not touch an existing POJO file");
    }

    @Test
    void overwritePolicyRegeneratesExistingPojo() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-overwrite-policy-overwrite");
        Path targetFile = sourceRoot.resolve("org/openapitools/model/ProductVO.java");
        Files.createDirectories(targetFile.getParent());
        String sentinel = "// STALE CONTENT - must be replaced by OVERWRITE\npackage org.openapitools.model;\n";
        Files.writeString(targetFile, sentinel);
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        AllcrudGenerator.generate(new GenerationRequest(
                SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.POJO, GeneratedLayer.CONTROLLER),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api"),
                OnRegenerate.OVERWRITE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        String actual = Files.readString(targetFile);
        assertNotEquals(sentinel, actual, "OVERWRITE must regenerate an existing POJO file");
        assertTrue(actual.contains("class ProductVO"), "Expected real generated ProductVO content, found: " + actual);
    }

    @Test
    void nonPojoLayersNeverOverwriteRegardlessOfPojoPolicy() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-overwrite-policy-controller");
        Path targetFile = sourceRoot.resolve("org/openapitools/api/ProductController.java");
        Files.createDirectories(targetFile.getParent());
        String sentinel = "// HAND-WRITTEN CONTROLLER LOGIC - must survive, no config knob for this layer\npackage org.openapitools.api;\n";
        Files.writeString(targetFile, sentinel);
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        // See docs/adr/0001-generate-once-never-overwrite.md
        AllcrudGenerator.generate(new GenerationRequest(
                SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.POJO, GeneratedLayer.CONTROLLER),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api"),
                OnRegenerate.OVERWRITE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        assertEquals(sentinel, Files.readString(targetFile),
                "Controller must never be overwritten, regardless of pojoOnRegenerate");
    }

}
