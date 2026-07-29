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
// (packages/defaults/resources) actually drives the already-validated relocate/overwrite
// logic (checkpoints 1-3) end to end - not just that the yml parses into the right Java
// values, but that running AllcrudGenerator against a resolved GenerationRequest produces
// exactly what the yml says: Order's reduced "generate" list (pojo+controller only, no
// repository/converter/service) and Product's pojo-only onRegenerate override.
class AllcrudGeneratorYamlConfigCompatTest {

    private static final Path SPEC = Path.of("src/main/resources/specs/product-example.yaml");
    private static final Path FIXTURE_YML = Path.of("src/test/resources/fixtures/allcrud-generator-example.yml");

    @Test
    void resourceGenerateListReplacesDefaultEntirely() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-yaml-config-generate-list");

        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(FIXTURE_YML);
        AllcrudGenerator.generate(config.toGenerationRequest(SPEC, sourceRoot));

        // Product has no "generate" override in the fixture yml - inherits defaults.generate,
        // all 5 layers.
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/model/ProductVO.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/ProductController.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/ProductService.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/ProductRepository.java")));
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/api/ProductConverter.java")));

        // Order's "generate: [pojo, repository]" REPLACES the default list entirely - the
        // other 3 layers must not exist, not just be "empty".
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
        String orderSentinel = "// CUSTOM - Order has no override, inherits defaults.pojo.onRegenerate: preserve\npackage org.openapitools.model;\n";
        Files.writeString(productVo, productSentinel);
        Files.writeString(orderVo, orderSentinel);

        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(FIXTURE_YML);
        AllcrudGenerator.generate(config.toGenerationRequest(SPEC, sourceRoot));

        assertNotEquals(productSentinel, Files.readString(productVo),
                "Product's pojo.onRegenerate: overwrite must replace the existing file");
        assertEquals(orderSentinel, Files.readString(orderVo),
                "Order has no pojo override - must inherit defaults.pojo.onRegenerate: preserve");
    }

    @Test
    void unknownRootKeyFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                packages:
                  pojo: a
                  repository: b
                  converter: c
                  service: d
                  controller: e
                defaults:
                  generate: [pojo]
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
                packages:
                  pojo: a
                  repository: b
                  converter: c
                  service: d
                  controller: e
                defaults:
                  generate: [pojo]
                  onRegenerate: preserve
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("onRegenerate"), "Expected error to name the bad key, was: " + ex.getMessage());
    }

    @Test
    void missingPackageEntryFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                packages:
                  pojo: a
                  repository: b
                  converter: c
                  service: d
                defaults:
                  generate: [pojo]
                """);

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
