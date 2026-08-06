package com.techmath.allcrud.generator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// x-allcrud-id-type (per-path vendor extension) overrides the ID type AllcrudSpringCodegen would
// otherwise resolve from the "id" property's own schema (see AllcrudSpringCodegen#
// postProcessOperationsWithModels' idTypeOverride handling) - documented in the README's vendor
// extensions table as "for the rare case where the id property's schema type isn't a reliable
// signal on its own", but had no test anywhere proving it actually wins over the schema-derived
// type before this.
class IdTypeOverrideCompatTest {

    private static final Path SPEC = Path.of("src/test/resources/fixtures/id-type-override.yaml");

    @Test
    void explicitIdTypeOverrideWinsOverSchemaDerivedType() throws IOException {
        Path sourceRoot = Files.createTempDirectory("allcrud-id-type-override");
        EntityFixtures.copyInto(sourceRoot, "Widget");

        AllcrudGenerator.generate(new GenerationRequest(
                SPEC, sourceRoot, EntityFixtures.unusedTestSourceRoot(), PojoNamingStyle.VO,
                Set.of(GeneratedLayer.CONTROLLER),
                Map.of(
                        GeneratedLayer.POJO, "org.openapitools.model",
                        GeneratedLayer.CONTROLLER, "org.openapitools.api",
                        GeneratedLayer.SERVICE, "org.openapitools.api",
                        GeneratedLayer.REPOSITORY, "org.openapitools.api",
                        GeneratedLayer.CONVERTER, "org.openapitools.api"),
                OnRegenerate.PRESERVE, Map.of(), "", EntityFixtures.NO_EXCEPTION_HANDLER));

        String controller = Files.readString(sourceRoot.resolve("org/openapitools/api/WidgetController.java"));

        // Widget's "id" schema property is int64 (Long) - x-allcrud-id-type: java.util.UUID on
        // both paths must win over that, not the schema-derived type.
        assertTrue(controller.contains("extends CrudController<Widget, WidgetVO, java.util.UUID>"),
                "Expected the x-allcrud-id-type override (UUID) to be used, found: " + controller);
        assertFalse(controller.contains("CrudController<Widget, WidgetVO, Long>"),
                "The schema-derived type (Long) must NOT have been used once overridden, found: " + controller);
    }

}
