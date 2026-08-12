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

    @Test
    void emptyYamlFailsWithClearMessage() throws IOException {
        Path emptyYml = writeYaml("");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(emptyYml));
        assertTrue(ex.getMessage().contains("Empty or invalid YAML"),
                "Expected the empty/invalid YAML message, was: " + ex.getMessage());
    }

    @Test
    void serviceWithoutRepositoryFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    enabled: false
                  repository:
                    enabled: false
                  converter:
                    enabled: false
                  service:
                    enabled: true
                    package: d
                  controller:
                    enabled: false
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("SERVICE requires REPOSITORY"),
                "Expected error to name SERVICE's missing REPOSITORY dependency, was: " + ex.getMessage());
    }

    @Test
    void unitTestWithoutServiceFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    enabled: false
                  repository:
                    enabled: false
                  converter:
                    enabled: false
                  service:
                    enabled: false
                  controller:
                    enabled: false
                  unitTest:
                    enabled: true
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("UNIT_TEST requires SERVICE"),
                "Expected error to name UNIT_TEST's missing SERVICE dependency, was: " + ex.getMessage());
    }

    @Test
    void integrationTestWithoutControllerFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    enabled: false
                  repository:
                    enabled: false
                  converter:
                    enabled: false
                  service:
                    enabled: false
                  controller:
                    enabled: false
                  integrationTest:
                    enabled: true
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("INTEGRATION_TEST requires CONTROLLER"),
                "Expected error to name INTEGRATION_TEST's missing CONTROLLER dependency, was: " + ex.getMessage());
    }

    @Test
    void integrationTestWithControllerBothEnabledLoadsWithoutThrowing() throws IOException {
        // The only other INTEGRATION_TEST/CONTROLLER test above proves the throwing side of
        // this check (CONTROLLER absent) - this proves the non-throwing side (CONTROLLER
        // present too), never exercised elsewhere since most fixtures don't enable
        // integrationTest at all. Needs pojo/converter (CONTROLLER's own dependencies) enabled
        // too, or CONTROLLER's own validation would throw first.
        Path validYml = writeYaml("""
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
                  integrationTest:
                    enabled: true
                """);

        AllcrudGeneratorYamlConfig config = AllcrudGeneratorYamlConfig.load(validYml);

        assertNotEquals(null, config);
    }

    @Test
    void exceptionHandlerWithNoResolvablePackageFailsWithClearMessage() throws IOException {
        // controller disabled (so exceptionHandler can't fall back to generation.controller.
        // package) and exceptionHandler.package never declared explicitly either - enabled:true
        // is exceptionHandler's own default, so this has to fail rather than silently resolve
        // to a null target package downstream.
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    enabled: false
                  repository:
                    enabled: false
                  converter:
                    enabled: false
                  service:
                    enabled: false
                  controller:
                    enabled: false
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("exceptionHandler"),
                "Expected error to name exceptionHandler, was: " + ex.getMessage());
    }

    @Test
    void resourcePackageOverrideParsesFromYaml() throws IOException {
        Path yml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: org.openapitools.model
                  repository:
                    package: org.openapitools.api
                  converter:
                    package: org.openapitools.api
                  service:
                    package: org.openapitools.api
                  controller:
                    package: org.openapitools.api
                resources:
                  Product:
                    pojo:
                      package: com.acme.catalog.dto
                """);

        GenerationRequest request = AllcrudGeneratorYamlConfig.load(yml)
                .toGenerationRequest(SPEC, EntityFixtures.unusedTestSourceRoot(), EntityFixtures.unusedTestSourceRoot());

        assertEquals("com.acme.catalog.dto",
                request.resourceOverrides().get("Product").packageOverrides().get(GeneratedLayer.POJO));
    }

    @Test
    void resourceBasePathParsesFromYaml() throws IOException {
        Path yml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: org.openapitools.model
                  repository:
                    package: org.openapitools.api
                  converter:
                    package: org.openapitools.api
                  service:
                    package: org.openapitools.api
                  controller:
                    package: org.openapitools.api
                resources:
                  Product:
                    basePath: /custom/products
                """);

        GenerationRequest request = AllcrudGeneratorYamlConfig.load(yml)
                .toGenerationRequest(SPEC, EntityFixtures.unusedTestSourceRoot(), EntityFixtures.unusedTestSourceRoot());

        assertEquals("/custom/products", request.resourceOverrides().get("Product").basePath());
    }

    @Test
    void resourceUnitTestPackageOverrideParsesFromYaml() throws IOException {
        Path yml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: org.openapitools.model
                  repository:
                    package: org.openapitools.api
                  converter:
                    package: org.openapitools.api
                  service:
                    package: org.openapitools.api
                  controller:
                    package: org.openapitools.api
                resources:
                  Product:
                    unitTest:
                      enabled: true
                      package: com.acme.catalog.test.unit
                """);

        GenerationRequest request = AllcrudGeneratorYamlConfig.load(yml)
                .toGenerationRequest(SPEC, EntityFixtures.unusedTestSourceRoot(), EntityFixtures.unusedTestSourceRoot());

        ResourceOverride productOverride = request.resourceOverrides().get("Product");
        assertTrue(productOverride.generate().contains(GeneratedLayer.UNIT_TEST));
        assertEquals("com.acme.catalog.test.unit", productOverride.packageOverrides().get(GeneratedLayer.UNIT_TEST));
    }

    @Test
    void resourceEnabledButNoPackageFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: org.openapitools.model
                  repository:
                    package: org.openapitools.api
                  converter:
                    enabled: false
                  service:
                    enabled: false
                  controller:
                    enabled: false
                resources:
                  Product:
                    converter:
                      enabled: true
                """);
        // Product turns converter back on for itself, but neither the resource nor
        // generation.converter declares a package for it.

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("resources.Product.converter"),
                "Expected error to name the resource-level layer, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("no \"package\" is configured"),
                "Expected error to explain the missing package, was: " + ex.getMessage());
    }

    @Test
    void routingBasePathPrefixParsesFromYaml() throws IOException {
        Path yml = writeYaml("""
                pojoNamingStyle: VO
                routing:
                  basePathPrefix: /v2
                generation:
                  pojo:
                    package: org.openapitools.model
                  repository:
                    package: org.openapitools.api
                  converter:
                    package: org.openapitools.api
                  service:
                    package: org.openapitools.api
                  controller:
                    package: org.openapitools.api
                """);

        GenerationRequest request = AllcrudGeneratorYamlConfig.load(yml)
                .toGenerationRequest(SPEC, EntityFixtures.unusedTestSourceRoot(), EntityFixtures.unusedTestSourceRoot());

        assertEquals("/v2", request.basePathPrefix());
    }

    @Test
    void routingSectionPresentButEmptyDefaultsToNoPrefix() throws IOException {
        // "routing:" declared but without "basePathPrefix" inside it - distinct from omitting
        // "routing:" entirely (already the default path every other test takes implicitly) -
        // both must resolve to the same "" default, not throw.
        Path yml = writeYaml("""
                pojoNamingStyle: VO
                routing: {}
                generation:
                  pojo:
                    package: org.openapitools.model
                  repository:
                    package: org.openapitools.api
                  converter:
                    package: org.openapitools.api
                  service:
                    package: org.openapitools.api
                  controller:
                    package: org.openapitools.api
                """);

        GenerationRequest request = AllcrudGeneratorYamlConfig.load(yml)
                .toGenerationRequest(SPEC, EntityFixtures.unusedTestSourceRoot(), EntityFixtures.unusedTestSourceRoot());

        assertEquals("", request.basePathPrefix());
    }

    @Test
    void routingBasePathPrefixWrongTypeFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                routing:
                  basePathPrefix: 123
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
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("routing.basePathPrefix"),
                "Expected error to name routing.basePathPrefix, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("must be a string"),
                "Expected a type-mismatch message, was: " + ex.getMessage());
    }

    @Test
    void invalidPojoNamingStyleValueFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: FOO
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
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("must be VO or DTO"),
                "Expected error to explain the allowed values, was: " + ex.getMessage());
    }

    @Test
    void controllerMissingOnlyServiceFailsWithClearMessage() throws IOException {
        // Converter and pojo ARE present - isolates CONTROLLER's own missing-SERVICE branch
        // from the missing-CONVERTER/missing-POJO ones already covered by
        // controllerWithoutPojoFailsWithClearMessage above.
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: a
                  repository:
                    enabled: false
                  converter:
                    package: c
                  service:
                    enabled: false
                  controller:
                    package: e
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("CONTROLLER requires"),
                "Expected error to name CONTROLLER's requirement, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("service"),
                "Expected error to list service among CONTROLLER's missing dependencies, was: " + ex.getMessage());
    }

    @Test
    void invalidOnRegenerateValueFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: a
                    onRegenerate: bogus
                  repository:
                    package: b
                  converter:
                    package: c
                  service:
                    package: d
                  controller:
                    package: e
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("must be preserve or overwrite"),
                "Expected error to explain the allowed values, was: " + ex.getMessage());
    }

    @Test
    void nonMapGenerationNodeFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation: not-a-map
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("must be a mapping"),
                "Expected a type-mismatch message, was: " + ex.getMessage());
    }

    @Test
    void nonStringPackageValueFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: 123
                  repository:
                    package: b
                  converter:
                    package: c
                  service:
                    package: d
                  controller:
                    package: e
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("generation.pojo.package"),
                "Expected error to name the bad key, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("must be a string"),
                "Expected a type-mismatch message, was: " + ex.getMessage());
    }

    @Test
    void nonBooleanEnabledValueFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                generation:
                  pojo:
                    package: a
                    enabled: yes-please
                  repository:
                    package: b
                  converter:
                    package: c
                  service:
                    package: d
                  controller:
                    package: e
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("generation.pojo.enabled"),
                "Expected error to name the bad key, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("must be a boolean"),
                "Expected a type-mismatch message, was: " + ex.getMessage());
    }

    @Test
    void missingPojoNamingStyleFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
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
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("pojoNamingStyle"),
                "Expected error to name the missing key, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("missing required key"),
                "Expected a missing-key message, was: " + ex.getMessage());
    }

    @Test
    void missingGenerationBlockFailsWithClearMessage() throws IOException {
        Path badYml = writeYaml("""
                pojoNamingStyle: VO
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AllcrudGeneratorYamlConfig.load(badYml));
        assertTrue(ex.getMessage().contains("generation"),
                "Expected error to name the missing key, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("missing required key"),
                "Expected a missing-key message, was: " + ex.getMessage());
    }

    private Path writeYaml(String content) throws IOException {
        Path file = Files.createTempFile("allcrud-generator-bad", ".yml");
        Files.writeString(file, content);
        return file;
    }

}
