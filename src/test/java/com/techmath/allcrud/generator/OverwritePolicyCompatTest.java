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

// Checkpoint 3 of the idempotent-scaffolding rework: proves the per-layer overwrite policy
// decision in AllcrudGenerator#shouldOverwrite is actually correct - not just that files land
// in the right place (checkpoints 1-2), but that a pre-existing file is preserved or replaced
// exactly per the fixed table (POJO configurable, everything else never-overwrite, no knob).
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
                SPEC, sourceRoot, PojoNamingStyle.VO,
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
                SPEC, sourceRoot, PojoNamingStyle.VO,
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

        // OnRegenerate.OVERWRITE only governs POJO - Controller has no knob and must never be
        // touched, regardless of this value.
        AllcrudGenerator.generate(new GenerationRequest(
                SPEC, sourceRoot, PojoNamingStyle.VO,
                Set.of(GeneratedLayer.POJO, GeneratedLayer.CONTROLLER),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api"),
                OnRegenerate.OVERWRITE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        assertEquals(sentinel, Files.readString(targetFile),
                "Controller must never be overwritten, regardless of pojoOnRegenerate");
    }

}
