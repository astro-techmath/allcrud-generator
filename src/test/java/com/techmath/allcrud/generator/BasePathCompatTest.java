package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Text-based (not compiled) checks on the @RequestMapping literal AllcrudSpringCodegen#
// resolveBasePath computes - GeneratedControllerAndServiceCompatTest already proves the
// default ("/product", no prefix, no override) case end to end via a real compiled class;
// these only need to prove the prefix-concatenation and override-replaces-entirely rules,
// which don't need Repository/Converter/Service to exist or compile alongside Controller.
class BasePathCompatTest {

    private static final Path SPEC = Path.of("src/main/resources/specs/product-example.yaml");

    @Test
    void basePathPrefixIsPrependedToTheLowercasedEntityName() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-base-path-prefix");
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        AllcrudGenerator.generate(new GenerationRequest(
                SPEC, sourceRoot, PojoNamingStyle.VO,
                Set.of(GeneratedLayer.CONTROLLER),
                Map.of(GeneratedLayer.CONTROLLER, "org.openapitools.api"),
                OnRegenerate.PRESERVE, Map.of(), "/v1", EntityFixtures.NO_EXCEPTION_HANDLER));

        String content = Files.readString(sourceRoot.resolve("org/openapitools/api/ProductController.java"));
        assertTrue(content.contains("@RequestMapping(\"/v1/product\")"),
                "Expected @RequestMapping(\"/v1/product\"), found: " + content);
    }

    @Test
    void resourceBasePathOverrideReplacesPrefixEntirely() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-base-path-override");
        EntityFixtures.copyInto(sourceRoot, "Product", "Order");

        AllcrudGenerator.generate(new GenerationRequest(
                SPEC, sourceRoot, PojoNamingStyle.VO,
                Set.of(GeneratedLayer.CONTROLLER),
                Map.of(GeneratedLayer.CONTROLLER, "org.openapitools.api"),
                OnRegenerate.PRESERVE,
                Map.of("Product", new ResourceOverride(null, null, "/custom/products", null)),
                "/v1", EntityFixtures.NO_EXCEPTION_HANDLER));

        String content = Files.readString(sourceRoot.resolve("org/openapitools/api/ProductController.java"));
        assertTrue(content.contains("@RequestMapping(\"/custom/products\")"),
                "Expected the override to replace the prefix entirely, found: " + content);
    }

}
