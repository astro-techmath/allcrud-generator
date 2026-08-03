package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Checkpoint 4 of the idempotent-scaffolding rework: proves allcrud-generator.yml parsing
// (generation/resources) actually drives the already-validated relocate/overwrite logic
// (checkpoints 1-3) end to end - not just that the yml parses into the right Java values, but
// that running AllcrudGenerator against a resolved GenerationRequest produces exactly what the
// yml says: Order's disabled converter/service/controller (pojo+repository only) and Product's
// pojo-only onRegenerate override.
class AllcrudGeneratorYamlConfigCompatTest {

    private static final Path SPEC = Path.of("src/main/resources/specs/product-example.yaml");
    private static final Path FIXTURE_YML = Path.of("src/test/resources/fixtures/allcrud-generator-example.yml");

    @Test
    void resourcePerLayerEnabledOverridesGlobalDefaults() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-yaml-config-generate-list");
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(FIXTURE_YML);
        AllcrudGenerator.generate(config.toGenerationRequest(SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot()));

        // Product has no layer overrides in the fixture yml - inherits generation.*'s global
        // enabled:true default for all 5 production layers.
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/model/ProductVO.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/ProductController.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/ProductService.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/ProductRepository.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/ProductConverter.java")));

        // Order disables converter/service/controller - only pojo+repository (inherited from
        // the global default) must exist, not just be "empty".
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/model/OrderVO.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/OrderRepository.java")));
        assertFalse(Files.exists(sourceRoot.resolve("org/openapitools/api/OrderController.java")));
        assertFalse(Files.exists(sourceRoot.resolve("org/openapitools/api/OrderService.java")));
        assertFalse(Files.exists(sourceRoot.resolve("org/openapitools/api/OrderConverter.java")));
    }

    @Test
    void perResourcePojoOnRegenerateOverridesDefault() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-yaml-config-onregenerate");

        Path productVo = sourceRoot.resolve("org/openapitools/model/ProductVO.java");
        Path orderVo = sourceRoot.resolve("org/openapitools/model/OrderVO.java");
        Files.createDirectories(productVo.getParent());
        String productSentinel = "// STALE - Product overrides onRegenerate to overwrite\npackage org.openapitools.model;\n";
        String orderSentinel = "// CUSTOM - Order has no override, inherits generation.pojo.onRegenerate: preserve\npackage org.openapitools.model;\n";
        Files.writeString(productVo, productSentinel);
        Files.writeString(orderVo, orderSentinel);
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(FIXTURE_YML);
        AllcrudGenerator.generate(config.toGenerationRequest(SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot()));

        assertNotEquals(productSentinel, Files.readString(productVo),
                "Product's pojo.onRegenerate: overwrite must replace the existing file");
        assertEquals(orderSentinel, Files.readString(orderVo),
                "Order has no pojo override - must inherit generation.pojo.onRegenerate: preserve");
    }

    @Test
    void unknownRootKeyFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: a
                  repository:
                    package: b
                  converter:
                    package: c
                  service:
                    package: d
                  controller:
                    package: e
                typo: oops
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("typo"), "Expected error to name the bad key, was: " + ex.getMessage());
    }

    @Test
    void onRegenerateOutsidePojoFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: a
                  repository:
                    package: b
                    onRegenerate: preserve
                  converter:
                    package: c
                  service:
                    package: d
                  controller:
                    package: e
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("onRegenerate"), "Expected error to name the bad key, was: " + ex.getMessage());
    }

    @Test
    void converterWithoutPojoFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    enabled: false
                  repository:
                    enabled: false
                  converter:
                    enabled: true
                    package: c
                  service:
                    enabled: false
                  controller:
                    enabled: false
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("CONVERTER requires POJO"),
                "Expected error to name the missing POJO dependency, was: " + ex.getMessage());
    }

    @Test
    void controllerWithoutPojoFailsWithClearMessage() throws IOException {
        // CONVERTER is deliberately absent too (not just POJO) - if it were present without
        // POJO, the CONVERTER-requires-POJO check above would fire first and this test
        // wouldn't isolate CONTROLLER's own POJO requirement. With CONVERTER also absent, the
        // CONTROLLER check's own missing-list must list both - proving CONTROLLER's rule
        // considers POJO at all (before this fix, this list would only ever have said
        // "converter", never "pojo").
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    enabled: false
                  repository:
                    package: b
                  converter:
                    enabled: false
                  service:
                    package: d
                  controller:
                    package: e
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("CONTROLLER requires"),
                "Expected error to name CONTROLLER's requirement, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("pojo"),
                "Expected error to list pojo among CONTROLLER's missing dependencies, was: " + ex.getMessage());
    }

    @Test
    void missingPackageEntryFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: a
                  repository:
                    package: b
                  converter:
                    package: c
                  service:
                    package: d
                """);
        // "controller" is omitted entirely - defaults to enabled:true (like every other
        // production layer) with no package, which must fail loudly rather than resolve to
        // a silent null downstream.

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("controller"), "Expected error to name the missing layer, was: " + ex.getMessage());
    }

    private Path writeYaml(String content) throws IOException {
        Path file = Files.createTempFile("allcrud-generator-bad", ".yml");
        Files.writeString(file, content);
        return file;
    }

}
