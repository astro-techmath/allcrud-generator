package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// AllcrudGenerator#packageOverrides() (resources.<name>.<layer>.package) had no end-to-end test
// before this - BasePathCompatTest's ResourceOverride usages only ever exercised the basePath
// field, always passing packageOverrides=null. This proves a per-resource package override
// actually relocates the generated file to the overridden package, not just the global one.
class PackageOverrideCompatTest {

    private static final Path SPEC = Path.of("src/main/resources/specs/product-example.yaml");

    @Test
    void resourcePackageOverrideRelocatesFileToOverriddenPackageInstead() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-package-override");
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        AllcrudGenerator.generate(new GenerationRequest(
                SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.POJO),
                Map.of(GeneratedLayer.POJO, "org.openapitools.model"),
                OnRegenerate.PRESERVE,
                Map.of("Product", new ResourceOverride(null, null, null,
                        Map.of(GeneratedLayer.POJO, "com.acme.catalog.dto"))),
                "", EntityFixtures.NO_EXCEPTION_HANDLER));

        // Product overrides pojo's package - must land under the override, NOT the global one.
        assertTrue(Files.exists(sourceRoot.resolve("com/acme/catalog/dto/ProductVO.java")),
                "Expected ProductVO.java under the per-resource package override");
        assertFalse(Files.exists(sourceRoot.resolve("org/openapitools/model/ProductVO.java")),
                "ProductVO.java should NOT have landed under the global package once overridden");

        // Order has no override for Product in the map above - must still use the global package.
        assertTrue(Files.exists(sourceRoot.resolve("org/openapitools/model/OrderVO.java")),
                "Expected OrderVO.java under the global package - no override applies to it");
    }

}
